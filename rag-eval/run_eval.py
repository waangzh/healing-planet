#!/usr/bin/env python3
"""Run a small, reproducible batch against the Healing Planet RAG API.

The runner deliberately does not score answers. It preserves the golden case,
request, raw API response, evidence, and timing so scoring can be repeated
without calling the service again.
"""

from __future__ import annotations

import argparse
import json
import logging
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


DEFAULT_GOLDEN = Path(__file__).with_name("golden.jsonl")
DEFAULT_OUTPUT = Path(__file__).with_name("results") / "raw.jsonl"
DEFAULT_BASE_URL = "http://localhost:8010"
DEFAULT_TIMEOUT_SECONDS = 180.0
DEFAULT_LIMIT = 5
LOG = logging.getLogger("rag_eval.runner")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run Healing Planet RAG golden cases.")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL,
                        help=f"RAG 服务地址（默认：{DEFAULT_BASE_URL}）")
    parser.add_argument("--golden", type=Path, default=DEFAULT_GOLDEN,
                        help=f"Golden JSONL 路径（默认：{DEFAULT_GOLDEN}）")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT,
                        help=f"原始结果 JSONL 路径（默认：{DEFAULT_OUTPUT}）")
    parser.add_argument("--limit", type=int, default=DEFAULT_LIMIT,
                        help=f"最多执行的 Case 数（默认：{DEFAULT_LIMIT}；不会默认跑完整数据集）")
    parser.add_argument("--start", type=int, default=0,
                        help="从 Golden Set 的 0-based 行号开始（默认：0）")
    parser.add_argument("--id", dest="case_ids", action="append",
                        help="只执行指定 Case ID；可重复传入，优先于 --start/--limit")
    parser.add_argument("--timeout", type=float, default=DEFAULT_TIMEOUT_SECONDS,
                        help=f"单条请求超时秒数（默认：{DEFAULT_TIMEOUT_SECONDS}）")
    parser.add_argument("--no-stream", action="store_true",
                        help="使用一次性响应接口；默认使用 SSE 流式接口")
    return parser.parse_args()


def load_cases(path: Path) -> list[dict[str, Any]]:
    cases: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            if not line.strip():
                continue
            try:
                case = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"{path}:{line_number} 不是有效 JSON：{exc}") from exc
            if not isinstance(case, dict) or not case.get("id") or not case.get("query"):
                raise ValueError(f"{path}:{line_number} 必须包含非空 id 和 query")
            cases.append(case)
    return cases


def select_cases(cases: list[dict[str, Any]], args: argparse.Namespace) -> list[dict[str, Any]]:
    if args.limit < 1:
        raise ValueError("--limit 必须大于 0")
    if args.start < 0:
        raise ValueError("--start 不能小于 0")
    if args.case_ids:
        wanted = set(args.case_ids)
        selected = [case for case in cases if case["id"] in wanted]
        missing = wanted - {case["id"] for case in selected}
        if missing:
            raise ValueError(f"Golden Set 中找不到 Case：{', '.join(sorted(missing))}")
        return selected
    return cases[args.start: args.start + args.limit]


def build_payload(case: dict[str, Any]) -> dict[str, Any]:
    """Map the golden set's snake_case fields to RagChatRequest's JSON names."""
    return {
        "userId": case.get("user_id"),
        "plantInstanceId": case.get("plant_instance_id"),
        "canonicalPlantId": case.get("canonical_plant_id"),
        "intent": case.get("input_intent"),
        "query": case["query"],
    }


def request_chat(base_url: str, payload: dict[str, Any], timeout: float) -> tuple[int, Any]:
    endpoint = base_url.rstrip("/") + "/api/rag/chat"
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = Request(endpoint, data=body, headers={
        "Accept": "application/json",
        "Content-Type": "application/json",
    }, method="POST")
    with urlopen(request, timeout=timeout) as response:
        raw = response.read().decode("utf-8")
        return response.status, json.loads(raw) if raw else None


def iter_sse_events(response: Any):
    """Yield parsed SSE event name and its data text from a urllib response."""
    event_name = "message"
    data_lines: list[str] = []

    def emit():
        nonlocal event_name, data_lines
        if data_lines:
            event = (event_name, "\n".join(data_lines))
            event_name = "message"
            data_lines = []
            return event
        event_name = "message"
        return None

    while True:
        line = response.readline()
        if not line:
            break
        text = line.decode("utf-8", errors="replace").rstrip("\r\n")
        if not text:
            event = emit()
            if event:
                yield event
        elif text.startswith("event:"):
            event_name = text[6:].strip() or "message"
        elif text.startswith("data:"):
            data_lines.append(text[5:].lstrip())

    event = emit()
    if event:
        yield event


def request_chat_stream(base_url: str, payload: dict[str, Any], timeout: float) -> tuple[int, Any]:
    endpoint = base_url.rstrip("/") + "/api/rag/chat/stream"
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = Request(endpoint, data=body, headers={
        "Accept": "text/event-stream",
        "Content-Type": "application/json",
    }, method="POST")

    evidence: list[Any] = []
    entity_resolution: dict[str, Any] | None = None
    retrieval_trace: dict[str, Any] | None = None
    answer_parts: list[str] = []
    events: list[dict[str, Any]] = []
    stream_error: str | None = None
    transport_error: dict[str, Any] | None = None
    with urlopen(request, timeout=timeout) as response:
        try:
            for event_name, raw_data in iter_sse_events(response):
                try:
                    data: Any = json.loads(raw_data)
                except json.JSONDecodeError:
                    data = raw_data
                events.append({"event": event_name, "data": data})

                if event_name == "evidence" and isinstance(data, list):
                    evidence = data
                elif event_name == "entity_resolution" and isinstance(data, dict):
                    entity_resolution = data
                elif event_name == "retrieval_trace" and isinstance(data, dict):
                    retrieval_trace = data
                elif event_name == "token":
                    if isinstance(data, str):
                        answer_parts.append(data)
                    elif isinstance(data, dict):
                        answer_parts.append(str(data.get("content", "")))
                elif event_name == "error":
                    stream_error = data if isinstance(data, str) else str(data.get("message", "流式回答失败"))
                elif event_name == "done" and isinstance(data, dict) and data.get("done") is False:
                    stream_error = stream_error or "流式回答未正常完成"
        except Exception as exc:
            transport_error = error_details(exc)

    result = {
        "answer": "".join(answer_parts),
        "evidence": evidence,
        "entityResolution": entity_resolution,
        "retrievalTrace": retrieval_trace,
        "events": events,
    }
    if stream_error:
        result["error"] = stream_error
    if transport_error:
        result["transportError"] = transport_error
    return response.status, result


def error_details(exc: Exception) -> dict[str, Any]:
    if isinstance(exc, HTTPError):
        body = exc.read().decode("utf-8", errors="replace")
        return {"type": type(exc).__name__, "message": str(exc), "status": exc.code,
                "body": body[:4000]}
    if isinstance(exc, URLError):
        return {"type": type(exc).__name__, "message": str(exc),
                "reason": str(exc.reason)}
    return {"type": type(exc).__name__, "message": str(exc)}


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def evidence_types(evidence: list[Any]) -> str:
    types = sorted({str(item.get("type")) for item in evidence if isinstance(item, dict) and item.get("type")})
    return ",".join(types) if types else "none"


def run_case(case: dict[str, Any], base_url: str, timeout: float, use_stream: bool) -> dict[str, Any]:
    payload = build_payload(case)
    LOG.info("case=%s 请求 mode=%s user_id=%s plant_instance_id=%s", case["id"],
             "stream" if use_stream else "sync", payload["userId"], payload["plantInstanceId"])
    started_at = utc_now()
    started = time.perf_counter()
    result: dict[str, Any] = {
        "case_id": case["id"],
        "query": case["query"],
        "request": payload,
        "started_at": started_at,
    }
    try:
        status, response = (request_chat_stream(base_url, payload, timeout)
                            if use_stream else request_chat(base_url, payload, timeout))
        result["http_status"] = status
        result["answer"] = response.get("answer") if isinstance(response, dict) else None
        result["evidence"] = response.get("evidence", []) if isinstance(response, dict) else []
        diagnostics = response.get("entityResolution") if isinstance(response, dict) else None
        retrieval_trace = response.get("retrievalTrace") if isinstance(response, dict) else None
        if isinstance(retrieval_trace, dict):
            result["retrieval_trace"] = retrieval_trace
        if isinstance(diagnostics, dict):
            for field in ("resolutionKind", "resolutionMethod", "canonicalPlantId", "canonicalPlantIds",
                          "top1Score", "top2Score", "scoreMargin", "candidateCount", "rejectionReason"):
                result[field] = diagnostics.get(field)
        result["response"] = response
        if isinstance(response, dict) and response.get("transportError"):
            result["error"] = response["transportError"]
        elif isinstance(response, dict) and response.get("error"):
            result["error"] = {"type": "RagStreamError", "message": response["error"]}
        LOG.info("case=%s 响应 http=%s evidence=%s types=%s answer_chars=%s", case["id"], status,
                 len(result["evidence"]), evidence_types(result["evidence"]), len(result["answer"] or ""))
    except Exception as exc:  # Keep the batch running so one bad case is diagnosable.
        result["error"] = error_details(exc)
        result["answer"] = None
        result["evidence"] = []
        LOG.warning("case=%s 请求失败 type=%s message=%s", case["id"], type(exc).__name__, str(exc))
    result["latency_ms"] = round((time.perf_counter() - started) * 1000, 2)
    result["completed_at"] = utc_now()
    return result


def main() -> int:
    args = parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s", stream=sys.stdout)
    try:
        cases = select_cases(load_cases(args.golden), args)
    except (OSError, ValueError) as exc:
        print(f"错误：{exc}", file=sys.stderr)
        return 2
    if not cases:
        print("错误：没有选中的 Case。", file=sys.stderr)
        return 2

    args.output.parent.mkdir(parents=True, exist_ok=True)
    succeeded = 0
    LOG.info("Runner 启动 base_url=%s timeout=%.1fs output=%s", args.base_url, args.timeout, args.output)
    print(f"将执行 {len(cases)} 条 Case，输出到 {args.output}")
    with args.output.open("w", encoding="utf-8", newline="\n") as handle:
        for index, case in enumerate(cases, start=1):
            result = run_case(case, args.base_url, args.timeout, use_stream=not args.no_stream)
            handle.write(json.dumps(result, ensure_ascii=False) + "\n")
            handle.flush()
            if "error" not in result:
                succeeded += 1
                status = f"HTTP {result['http_status']}"
            else:
                status = f"失败：{result['error']['type']}"
            print(f"[{index}/{len(cases)}] {case['id']} {status} {result['latency_ms']} ms")
    print(f"完成：{succeeded}/{len(cases)} 条请求成功。")
    return 0 if succeeded == len(cases) else 1


if __name__ == "__main__":
    raise SystemExit(main())
