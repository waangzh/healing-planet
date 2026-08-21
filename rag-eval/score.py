#!/usr/bin/env python3
"""离线计算 Healing Planet RAG 的确定性评测指标。"""

from __future__ import annotations

import argparse
import hashlib
from http.client import IncompleteRead, RemoteDisconnected
import json
import logging
import math
import os
import re
import sys
from collections import Counter
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from urllib.parse import urlsplit


ROOT = Path(__file__).resolve().parent
DEFAULT_GOLDEN = ROOT / "holdout.jsonl"
DEFAULT_RAW = ROOT / "results" / "raw.jsonl"
DEFAULT_OUTPUT = ROOT / "results" / "score.json"
DEFAULT_REPORT = ROOT / "results" / "report.md"
DEFAULT_JUDGMENTS = ROOT / "results" / "judgments.jsonl"
DEFAULT_JUDGE_CONFIG = ROOT / "judge.local.json"
PROMPTS = ROOT / "prompts"
KNOWLEDGE_TYPES = {"PLANT_KNOWLEDGE", "CARE_GUIDE", "COMMUNITY_POST", "DISEASE_KNOWLEDGE"}
KNOWLEDGE_TOP_K = 6
RETRIEVAL_TOP_K = 10
CITATION_PATTERN = re.compile(r"\[E(\d+)\]")
SAFE_REFUSAL_OUTCOMES = {
    "INSUFFICIENT_KNOWLEDGE", "STATE_UNAVAILABLE", "REQUIRE_USER_ID", "REQUIRE_PLANT_INSTANCE",
    "OUT_OF_SCOPE",
}
ROUTING_TRACE_FIELDS = {
    "schemaVersion", "includeKnowledge", "includeCommunity", "includeState", "inputIntent", "resolvedIntent", "domain",
    "entityRequirement", "stateEvidenceNeed", "searchQuery", "knowledgeRequirement",
    "communityRequirement", "stateRequirement",
}
ENTITY_DEPENDENCY_FAILURES = {
    "llm_connect_timeout", "llm_read_timeout", "llm_connection_failed", "llm_invalid_json",
}
LOG = logging.getLogger("rag_eval.score")


@dataclass(frozen=True)
class JudgeSettings:
    url: str
    api_key: str
    model: str
    timeout: float
    retries: int
    temperature: float = 0.0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="评分 Healing Planet RAG 原始评测结果。")
    parser.add_argument("--golden", type=Path, default=DEFAULT_GOLDEN,
                        help=f"Golden JSONL 路径（默认：{DEFAULT_GOLDEN}）")
    parser.add_argument("--raw", type=Path, default=DEFAULT_RAW,
                        help=f"Runner 原始结果 JSONL 路径（默认：{DEFAULT_RAW}）")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT,
                        help=f"JSON 汇总输出路径（默认：{DEFAULT_OUTPUT}）")
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT,
                        help=f"Markdown 报告输出路径（默认：{DEFAULT_REPORT}）")
    parser.add_argument("--judge", action="store_true",
                        help="调用两个 LLM Judge，并将结果缓存到 --judgments")
    parser.add_argument("--judgments", type=Path, default=DEFAULT_JUDGMENTS,
                        help=f"Judge 结果 JSONL 路径（默认：{DEFAULT_JUDGMENTS}）")
    parser.add_argument("--refresh-judges", action="store_true",
                        help="忽略同输入的 Judge 缓存并重新评分")
    parser.add_argument("--judge-config", type=Path, default=DEFAULT_JUDGE_CONFIG,
                        help=f"Judge 本地 JSON 配置路径（默认：{DEFAULT_JUDGE_CONFIG}）")
    parser.add_argument("--judge-url",
                        help="OpenAI 兼容 chat/completions 完整地址，优先级高于环境变量和本地配置")
    parser.add_argument("--judge-model",
                        help="Judge 模型名，优先级高于环境变量和本地配置")
    parser.add_argument("--judge-api-key-env",
                        help="保存 Judge API Key 的环境变量名，优先级高于本地配置")
    parser.add_argument("--judge-timeout", type=float,
                        help="单次 Judge 请求超时秒数，优先级高于本地配置")
    parser.add_argument("--judge-retries", type=int,
                        help="单个 Judge 失败后的重试次数，优先级高于本地配置")
    return parser.parse_args()


def load_jsonl(path: Path, label: str) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            if not line.strip():
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"{label} {path}:{line_number} 不是有效 JSON：{exc}") from exc
            if not isinstance(row, dict) or not row.get("id" if label == "Golden Set" else "case_id"):
                field = "id" if label == "Golden Set" else "case_id"
                raise ValueError(f"{label} {path}:{line_number} 必须包含非空 {field}")
            rows.append(row)
    return rows


def load_judgments(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            if not line.strip():
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"Judge 结果 {path}:{line_number} 不是有效 JSON：{exc}") from exc
            if not isinstance(row, dict) or not row.get("case_id"):
                raise ValueError(f"Judge 结果 {path}:{line_number} 必须包含非空 case_id")
            rows.append(row)
    return rows


def load_judge_config(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise ValueError(f"Judge 配置 {path} 不是有效 JSON：{exc}") from exc
    if not isinstance(value, dict):
        raise ValueError(f"Judge 配置 {path} 必须是 JSON 对象")
    if "api_key" in value:
        raise ValueError(f"Judge 配置 {path} 不允许保存 api_key；请使用 api_key_env")
    return value


def normalized_text(value: Any) -> str:
    return "" if value is None else str(value).replace(" ", "").lower()


def entity_rejection_reason(raw: dict[str, Any]) -> str:
    reason = raw.get("rejectionReason")
    if isinstance(reason, str) and reason:
        return reason
    response = raw.get("response")
    diagnostics = response.get("entityResolution") if isinstance(response, dict) else None
    nested_reason = diagnostics.get("rejectionReason") if isinstance(diagnostics, dict) else None
    return nested_reason if isinstance(nested_reason, str) else ""


def predict_outcome(raw: dict[str, Any]) -> str:
    if raw.get("error") or not isinstance(raw.get("answer"), str):
        return "ERROR"
    rejection_reason = entity_rejection_reason(raw)
    if rejection_reason.startswith(("llm_disambiguation_", "llm_http_")) \
            or rejection_reason in ENTITY_DEPENDENCY_FAILURES:
        return "ENTITY_RESOLUTION_UNAVAILABLE"
    answer = normalized_text(raw["answer"])
    if "个体化状态分析需要userid" in answer:
        return "REQUIRE_USER_ID"
    if "个体化状态分析需要plantinstanceid" in answer:
        return "REQUIRE_PLANT_INSTANCE"
    if "无法获取这盆植物的最新状态" in answer and "不能可靠判断" in answer:
        return "STATE_UNAVAILABLE"
    routing = retrieval_trace(raw).get("routing")
    if isinstance(routing, dict) and routing.get("domain") == "OUT_OF_DOMAIN":
        return "OUT_OF_SCOPE"
    if "不属于当前植物养护知识库的可回答范围" in answer:
        return "OUT_OF_SCOPE"
    if "当前知识库中没有足够证据" in answer:
        return "INSUFFICIENT_KNOWLEDGE"
    return "ANSWER"


def evidence_list(raw: dict[str, Any]) -> list[dict[str, Any]]:
    evidence = raw.get("evidence")
    return [item for item in evidence if isinstance(item, dict)] if isinstance(evidence, list) else []


def value_from_evidence(evidence: dict[str, Any], key: str) -> Any:
    value = evidence.get(key)
    if value is not None:
        return value
    metadata = evidence.get("metadata")
    return metadata.get(key) if isinstance(metadata, dict) else None


def evidence_ref(evidence: dict[str, Any]) -> tuple[str, str | None]:
    source_id = value_from_evidence(evidence, "sourceId")
    knowledge_type = value_from_evidence(evidence, "knowledgeType")
    return str(source_id) if source_id is not None else "", str(knowledge_type) if knowledge_type else None


def gold_ref(ref: Any) -> tuple[str, str | None] | None:
    if not isinstance(ref, dict) or ref.get("source_id") is None:
        return None
    knowledge_type = ref.get("knowledge_type")
    return str(ref["source_id"]), str(knowledge_type) if knowledge_type else None


def reference_matches(gold: tuple[str, str | None], actual: tuple[str, str | None]) -> bool:
    return gold[0] == actual[0] and (gold[1] is None or gold[1] == actual[1])


def knowledge_evidence(evidence: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [item for item in evidence if str(item.get("type", "")) in KNOWLEDGE_TYPES][:KNOWLEDGE_TOP_K]


def retrieval_trace(raw: dict[str, Any]) -> dict[str, Any]:
    trace = raw.get("retrieval_trace")
    if isinstance(trace, dict):
        return trace
    response = raw.get("response")
    nested = response.get("retrievalTrace") if isinstance(response, dict) else None
    return nested if isinstance(nested, dict) else {}


def pre_selection_candidates(raw: dict[str, Any]) -> list[dict[str, Any]]:
    candidates = retrieval_trace(raw).get("preSelectionRanked")
    if not isinstance(candidates, list):
        return []
    return [item for item in candidates[:RETRIEVAL_TOP_K] if isinstance(item, dict)]


def predicted_route(raw: dict[str, Any]) -> str | None:
    trace = retrieval_trace(raw)
    routing = trace.get("routing")
    intent = routing.get("resolvedIntent") if isinstance(routing, dict) else None
    return intent if isinstance(intent, str) and intent else None


def routing_snapshot(raw: dict[str, Any]) -> dict[str, Any]:
    routing = retrieval_trace(raw).get("routing")
    return routing if isinstance(routing, dict) else {}


def validate_routing_trace_contract(raw_rows: list[dict[str, Any]]) -> None:
    invalid: list[str] = []
    for raw in raw_rows:
        if raw.get("error"):
            continue
        routing = routing_snapshot(raw)
        missing = sorted(field for field in ROUTING_TRACE_FIELDS if field not in routing)
        if missing:
            invalid.append(f"{raw.get('case_id', '<unknown>')} 缺少 {', '.join(missing)}")
        elif routing.get("schemaVersion") != 3:
            invalid.append(f"{raw.get('case_id', '<unknown>')} schemaVersion 不是 3")
    if invalid:
        raise ValueError("原始结果不满足 Routing Trace v3；请使用新版服务重新执行 run_eval.py：" + "; ".join(invalid))


def expected_source_requirement(golden: dict[str, Any]) -> dict[str, str] | None:
    value = golden.get("expected_source_requirement")
    if not isinstance(value, dict):
        return None
    expected = {name: value.get(name) for name in ("knowledge", "community", "state")}
    if any(not isinstance(mode, str) or not mode for mode in expected.values()):
        return None
    return expected


def propagation_is_consistent(raw: dict[str, Any]) -> bool | None:
    routing = routing_snapshot(raw)
    request = raw.get("request")
    if not routing or not isinstance(request, dict):
        return None
    return request.get("intent") == routing.get("inputIntent")


def outcome_matches(expected: str, predicted: str) -> bool:
    return predicted in SAFE_REFUSAL_OUTCOMES if expected == "SAFE_REFUSAL" else predicted == expected


def retrieval_latency(raw: dict[str, Any]) -> float | None:
    stages = retrieval_trace(raw).get("stages")
    if not isinstance(stages, list):
        return None
    durations = [
        float(item["durationMs"])
        for item in stages
        if isinstance(item, dict) and item.get("stage") == "retrieve_total"
        and item.get("status") == "ok" and isinstance(item.get("durationMs"), (int, float))
    ]
    return round(max(durations), 3) if durations else None


def average_precision(relevance: list[bool]) -> float:
    relevant = 0
    precision_sum = 0.0
    for rank, is_relevant in enumerate(relevance, start=1):
        if is_relevant:
            relevant += 1
            precision_sum += relevant / rank
    return 0.0 if relevant == 0 else precision_sum / relevant


def evidence_type_matches(required: str, evidence: dict[str, Any]) -> bool:
    base, separator, qualifier = required.partition(":")
    if str(evidence.get("type", "")) != base:
        return False
    if not separator:
        return True
    return str(value_from_evidence(evidence, "knowledgeType") or "") == qualifier


def selection_constraint_result(golden: dict[str, Any], raw: dict[str, Any],
                                evidence: list[dict[str, Any]]) -> dict[str, Any] | None:
    expectations = golden.get("selection_expectations")
    if not isinstance(expectations, dict):
        return None

    selected = knowledge_evidence(evidence)
    checks: dict[str, bool] = {}
    group_counts = Counter(evidence_ref(item) for item in selected)
    max_per_group = expectations.get("max_selected_per_source_knowledge_type")
    if isinstance(max_per_group, int) and max_per_group >= 0:
        checks["max_selected_per_source_knowledge_type"] = all(
            count <= max_per_group for count in group_counts.values())

    plant_types = {
        str(value_from_evidence(item, "knowledgeType") or "")
        for item in selected if str(item.get("type")) == "CARE_GUIDE"
    }
    minimum_topics = expectations.get("min_distinct_plant_knowledge_types")
    if isinstance(minimum_topics, int) and minimum_topics >= 0:
        checks["min_distinct_plant_knowledge_types"] = len(plant_types) >= minimum_topics

    required_types = expectations.get("required_plant_knowledge_types")
    if isinstance(required_types, list):
        checks["required_plant_knowledge_types"] = all(
            isinstance(item, str) and item in plant_types for item in required_types)

    forbidden_types = expectations.get("forbidden_plant_knowledge_types")
    if isinstance(forbidden_types, list):
        checks["forbidden_plant_knowledge_types"] = not any(
            isinstance(item, str) and item in plant_types for item in forbidden_types)

    community_sources = {
        evidence_ref(item)[0] for item in selected
        if str(item.get("type")) == "COMMUNITY_POST" and evidence_ref(item)[0]
    }
    minimum_community_sources = expectations.get("min_community_sources")
    if isinstance(minimum_community_sources, int) and minimum_community_sources >= 0:
        checks["min_community_sources"] = len(community_sources) >= minimum_community_sources
    maximum_community_sources = expectations.get("max_community_sources")
    if isinstance(maximum_community_sources, int) and maximum_community_sources >= 0:
        checks["max_community_sources"] = len(community_sources) <= maximum_community_sources

    trace = raw.get("retrieval_trace")
    ranked = trace.get("rerankAfter", []) if isinstance(trace, dict) else []
    if not isinstance(ranked, list):
        ranked = []
    for index, group in enumerate(expectations.get("ranked_group_minimums", [])):
        if not isinstance(group, dict):
            checks[f"ranked_group_minimums[{index}]"] = False
            continue
        source_id = str(group.get("source_id", ""))
        knowledge_type = str(group.get("knowledge_type", ""))
        minimum = group.get("min")
        if not source_id or not knowledge_type or not isinstance(minimum, int) or minimum < 0:
            checks[f"ranked_group_minimums[{index}]"] = False
            continue
        count = sum(1 for item in ranked if isinstance(item, dict)
                    and str(value_from_evidence(item, "sourceId")) == source_id
                    and str(value_from_evidence(item, "knowledgeType")) == knowledge_type)
        checks[f"ranked_group_minimums[{index}]"] = count >= minimum

    return {"passed": all(checks.values()), "checks": checks}


def percentile(values: list[float], percentile_value: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    position = (len(ordered) - 1) * percentile_value
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return round(ordered[lower], 2)
    return round(ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower), 2)


def ratio(numerator: int | float, denominator: int | float) -> float | None:
    return round(numerator / denominator, 6) if denominator else None


def metric(value: float | None, numerator: int | float, denominator: int | float) -> dict[str, Any]:
    return {"value": value, "numerator": numerator, "denominator": denominator}


def prompt_text(name: str) -> str:
    return (PROMPTS / name).read_text(encoding="utf-8")


def render_prompt(template: str, values: dict[str, str]) -> str:
    for name, value in values.items():
        template = template.replace("{{" + name + "}}", value)
    return template


def format_claims(claims: Any) -> str:
    if not isinstance(claims, list) or not claims:
        return "无"
    return "\n".join(f"- {claim}" for claim in claims)


def format_indexed_claims(claims: Any) -> str:
    if not isinstance(claims, list) or not claims:
        return "无"
    return "\n".join(f"[C{index}] {claim}" for index, claim in enumerate(claims, start=1))


def format_evidence(evidence: list[dict[str, Any]]) -> str:
    if not evidence:
        return "无 Evidence。"
    items: list[str] = []
    for index, item in enumerate(evidence, start=1):
        title = item.get("title") or "无标题"
        content = item.get("content") or ""
        items.append(f"[E{index}] type={item.get('type', 'UNKNOWN')} title={title}\n{content}")
    return "\n\n".join(items)


def system_policy_context(case: dict[str, Any], raw: dict[str, Any]) -> str:
    policies: list[str] = []
    evidence = evidence_list(raw)
    actual_types = {str(item.get("type")) for item in evidence}
    if "CARE_GUIDE" in actual_types and "COMMUNITY_POST" in actual_types:
        policies.append("当答案同时引用正式指南与社区经验时，应标明来源；若两类证据冲突，以正式指南为准。")
    if "COMMUNITY_POST" in actual_types:
        policies.append("类型为 COMMUNITY_POST 的证据属于社区用户或帖子作者的个人经验，不是正式指南。")
    if "LIVE_STATE" in actual_types:
        policies.append("LIVE_STATE 中的阈值判断、当前读数均未超出已配置阈值、以及明确写出的配置范围，属于可直接引用的状态事实。")
        policies.append("若 LIVE_STATE 明确写出当前读数低于或高于已配置阈值，则基于该事实给出的同方向处理判断（例如需要浇水或建议补水）属于直接推理；若数据已过期，则不能据此可靠判断当前状态。")
        policies.append("对于即时处理决策，若未过期的当前读数位于已配置范围内，系统策略是不建议仅凭该读数立即重复处理。")
    if "SENSOR_HISTORY" in actual_types:
        policies.append("SENSOR_HISTORY 中明确给出的过去24小时/7天平均值与趋势，属于可直接引用的状态事实。")
    return "\n".join(f"- {item}" for item in policies) if policies else "无额外系统策略。"


def fingerprint(value: Any) -> str:
    serialized = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(serialized.encode("utf-8")).hexdigest()


def parse_json_content(content: str) -> dict[str, Any]:
    text = content.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*|\s*```$", "", text, flags=re.IGNORECASE)
    parsed = json.loads(text)
    if not isinstance(parsed, dict):
        raise ValueError("Judge 未返回 JSON 对象")
    return parsed


def extract_message_content(response: dict[str, Any]) -> str:
    try:
        content = response["choices"][0]["message"]["content"]
    except (KeyError, IndexError, TypeError) as exc:
        raise ValueError("Judge API 响应缺少 choices[0].message.content") from exc
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return "".join(str(part.get("text", "")) for part in content if isinstance(part, dict))
    raise ValueError("Judge API 返回的 message.content 不是文本")


def call_judge(prompt: str, settings: JudgeSettings) -> dict[str, Any]:
    payload = json.dumps({
        "model": settings.model,
        "temperature": settings.temperature,
        "messages": [{"role": "user", "content": prompt}],
    }, ensure_ascii=False).encode("utf-8")
    request = Request(settings.url, data=payload, headers={
        "Authorization": "Bearer " + settings.api_key,
        "Content-Type": "application/json",
        "Accept": "application/json",
    }, method="POST")
    last_error: Exception | None = None
    for attempt in range(1, settings.retries + 2):
        try:
            with urlopen(request, timeout=settings.timeout) as response:
                body = json.loads(response.read().decode("utf-8"))
            return parse_json_content(extract_message_content(body))
        # Judge 可能以 chunked 响应返回；上游在响应体结束前断开时，
        # http.client 会抛出 IncompleteRead/RemoteDisconnected，应按可重试网络错误处理。
        except (HTTPError, URLError, OSError, IncompleteRead, RemoteDisconnected,
                ValueError, json.JSONDecodeError) as exc:
            last_error = exc
            LOG.warning("Judge 请求失败 attempt=%s/%s type=%s message=%s", attempt, settings.retries + 1,
                        type(exc).__name__, str(exc))
    raise RuntimeError(f"Judge 调用失败：{last_error}")


def validate_correctness(value: dict[str, Any]) -> dict[str, Any]:
    score = value.get("score")
    if not isinstance(score, int) or score not in {0, 1, 2}:
        raise ValueError("Correctness Judge 的 score 必须是 0、1 或 2")
    return {
        "score": score,
        "missing_claims": value.get("missing_claims") if isinstance(value.get("missing_claims"), list) else [],
        "wrong_claims": value.get("wrong_claims") if isinstance(value.get("wrong_claims"), list) else [],
        "reason": value.get("reason") if isinstance(value.get("reason"), str) else "",
    }


def validate_faithfulness(value: dict[str, Any]) -> dict[str, Any]:
    claims = value.get("claims")
    if not isinstance(claims, list):
        raise ValueError("Faithfulness Judge 的 claims 必须是数组")
    normalized: list[dict[str, Any]] = []
    for claim in claims:
        if not isinstance(claim, dict) or not isinstance(claim.get("claim"), str) or not isinstance(claim.get("supported"), bool):
            raise ValueError("Faithfulness Judge 的每条 claim 必须包含 claim 和 supported")
        evidence_ids = claim.get("evidence_ids")
        claim_type = claim.get("claim_type", "EVIDENCE_FACT")
        if claim_type not in {"EVIDENCE_FACT", "SYSTEM_POLICY"}:
            raise ValueError("Faithfulness Judge 的 claim_type 必须是 EVIDENCE_FACT 或 SYSTEM_POLICY")
        normalized.append({
            "claim": claim["claim"],
            "claim_type": claim_type,
            "supported": claim["supported"],
            "evidence_ids": evidence_ids if isinstance(evidence_ids, list) else [],
        })
    return {"claims": normalized}


def validate_context_quality(value: dict[str, Any], evidence_count: int,
                             claim_count: int) -> dict[str, Any]:
    contexts = value.get("contexts")
    reference_claims = value.get("reference_claims")
    if not isinstance(contexts, list) or not isinstance(reference_claims, list):
        raise ValueError("Context Judge 必须返回 contexts 和 reference_claims 数组")
    expected_evidence_ids = [f"E{index}" for index in range(1, evidence_count + 1)]
    actual_evidence_ids = [item.get("evidence_id") for item in contexts if isinstance(item, dict)]
    if actual_evidence_ids != expected_evidence_ids:
        raise ValueError("Context Judge 必须按顺序逐条返回全部 Evidence")
    normalized_contexts: list[dict[str, Any]] = []
    for item in contexts:
        if not isinstance(item.get("relevant"), bool):
            raise ValueError("Context Judge 的每条 context 必须包含布尔值 relevant")
        normalized_contexts.append({"evidence_id": item["evidence_id"], "relevant": item["relevant"]})

    expected_claim_indexes = list(range(1, claim_count + 1))
    actual_claim_indexes = [item.get("claim_index") for item in reference_claims if isinstance(item, dict)]
    if actual_claim_indexes != expected_claim_indexes:
        raise ValueError("Context Judge 必须按顺序逐条返回全部 Gold Claim")
    normalized_claims: list[dict[str, Any]] = []
    for item in reference_claims:
        if not isinstance(item.get("supported"), bool):
            raise ValueError("Context Judge 的每条 reference claim 必须包含布尔值 supported")
        evidence_ids = item.get("evidence_ids")
        normalized_claims.append({
            "claim_index": item["claim_index"],
            "supported": item["supported"],
            "evidence_ids": evidence_ids if isinstance(evidence_ids, list) else [],
        })
    return {"contexts": normalized_contexts, "reference_claims": normalized_claims}


def judge_input_fingerprint(case: dict[str, Any], raw: dict[str, Any], settings: JudgeSettings,
                            correctness_prompt: str, faithfulness_prompt: str,
                            context_prompt: str) -> str:
    return fingerprint({
        "source": source_fingerprint(case, raw),
        "judge": {
            "model": settings.model, "temperature": settings.temperature,
            "correctness_prompt": fingerprint(correctness_prompt),
            "faithfulness_prompt": fingerprint(faithfulness_prompt),
            "context_prompt": fingerprint(context_prompt),
        },
    })


def source_fingerprint(case: dict[str, Any], raw: dict[str, Any]) -> str:
    return fingerprint({
        "case": {
            "id": case["id"], "query": case.get("query"), "gold_claims": case.get("gold_claims"),
            "reference_answer": case.get("reference_answer"), "expected_outcome": case.get("expected_outcome"),
        },
        "raw": {"answer": raw.get("answer"), "evidence": evidence_list(raw)},
    })


def merge_judgments(existing: list[dict[str, Any]], updated: list[dict[str, Any]]) -> list[dict[str, Any]]:
    updated_keys = {(item["case_id"], item.get("input_fingerprint")) for item in updated}
    retained = [item for item in existing if (item.get("case_id"), item.get("input_fingerprint")) not in updated_keys]
    return retained + updated


def run_judges(golden_rows: list[dict[str, Any]], raw_rows: list[dict[str, Any]], settings: JudgeSettings,
               existing: list[dict[str, Any]], refresh: bool,
               on_case_complete: Callable[[list[dict[str, Any]]], None] | None = None) -> tuple[list[dict[str, Any]], int]:
    correctness_prompt = prompt_text("correctness-judge.txt")
    faithfulness_prompt = prompt_text("faithfulness-judge.txt")
    context_prompt = prompt_text("context-judge.txt")
    golden_by_id = {case["id"]: case for case in golden_rows}
    cached = {(item.get("case_id"), item.get("input_fingerprint")): item for item in existing
              if item.get("status") == "ok"}
    updated: list[dict[str, Any]] = []
    failures = 0
    candidates = [raw for raw in raw_rows if golden_by_id[raw["case_id"]].get("expected_outcome", "ANSWER") == "ANSWER"
                  and predict_outcome(raw) == "ANSWER"]
    LOG.info("Judge 启动 candidates=%s existing_cache=%s refresh=%s model=%s endpoint=%s", len(candidates),
             len(existing), refresh, settings.model, safe_endpoint(settings.url))

    for raw in candidates:
        case = golden_by_id[raw["case_id"]]
        input_fingerprint = judge_input_fingerprint(
            case, raw, settings, correctness_prompt, faithfulness_prompt, context_prompt)
        cached_row = cached.get((case["id"], input_fingerprint))
        if cached_row and not refresh:
            LOG.info("case=%s Judge 缓存命中", case["id"])
            updated.append(cached_row)
            continue

        correctness = faithfulness = context_quality = None
        errors: dict[str, str] = {}
        try:
            LOG.info("case=%s 调用 Correctness Judge", case["id"])
            correctness = validate_correctness(call_judge(render_prompt(correctness_prompt, {
                "QUESTION": str(case.get("query", "")),
                "GOLD_CLAIMS": format_claims(case.get("gold_claims")),
                "REFERENCE_ANSWER": str(case.get("reference_answer", "")),
                "GENERATED_ANSWER": str(raw.get("answer", "")),
            }), settings))
            LOG.info("case=%s Correctness Judge 完成 score=%s", case["id"], correctness["score"])
        except (RuntimeError, ValueError) as exc:
            errors["correctness"] = str(exc)
            LOG.warning("case=%s Correctness Judge 失败: %s", case["id"], exc)
        try:
            LOG.info("case=%s 调用 Faithfulness Judge evidence=%s", case["id"], len(evidence_list(raw)))
            faithfulness = validate_faithfulness(call_judge(render_prompt(faithfulness_prompt, {
                "QUESTION": str(case.get("query", "")),
                "SYSTEM_POLICY": system_policy_context(case, raw),
                "EVIDENCE": format_evidence(evidence_list(raw)),
                "GENERATED_ANSWER": str(raw.get("answer", "")),
            }), settings))
            LOG.info("case=%s Faithfulness Judge 完成 claims=%s", case["id"], len(faithfulness["claims"]))
        except (RuntimeError, ValueError) as exc:
            errors["faithfulness"] = str(exc)
            LOG.warning("case=%s Faithfulness Judge 失败: %s", case["id"], exc)
        try:
            claims = case.get("gold_claims") if isinstance(case.get("gold_claims"), list) else []
            evidence = evidence_list(raw)
            LOG.info("case=%s 调用 Context Judge evidence=%s claims=%s", case["id"], len(evidence), len(claims))
            context_quality = validate_context_quality(call_judge(render_prompt(context_prompt, {
                "QUESTION": str(case.get("query", "")),
                "REFERENCE_ANSWER": str(case.get("reference_answer", "")),
                "GOLD_CLAIMS": format_indexed_claims(claims),
                "EVIDENCE": format_evidence(evidence),
            }), settings), len(evidence), len(claims))
            LOG.info("case=%s Context Judge 完成 contexts=%s claims=%s", case["id"],
                     len(context_quality["contexts"]), len(context_quality["reference_claims"]))
        except (RuntimeError, ValueError) as exc:
            errors["context_quality"] = str(exc)
            LOG.warning("case=%s Context Judge 失败: %s", case["id"], exc)

        if errors:
            failures += len(errors)
        updated.append({
            "schema_version": 2,
            "case_id": case["id"],
            "source_fingerprint": source_fingerprint(case, raw),
            "input_fingerprint": input_fingerprint,
            "judged_at": datetime.now(timezone.utc).isoformat(),
            "status": "ok" if not errors else "partial_error",
            "judge": {"model": settings.model, "temperature": settings.temperature},
            "correctness": correctness,
            "faithfulness": faithfulness,
            "context_quality": context_quality,
            "errors": errors,
        })
        if on_case_complete:
            on_case_complete(merge_judgments(existing, updated))

    LOG.info("Judge 完成 records=%s failures=%s", len(updated), failures)
    return merge_judgments(existing, updated), failures


def judgment_index(golden_by_id: dict[str, dict[str, Any]], raw_by_id: dict[str, dict[str, Any]],
                   judgments: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    return {
        item["case_id"]: item for item in judgments
        if item.get("status") in {"ok", "partial_error"} and item.get("case_id") in raw_by_id
        and item.get("source_fingerprint") == source_fingerprint(golden_by_id[item["case_id"]], raw_by_id[item["case_id"]])
    }


def score_cases(golden_rows: list[dict[str, Any]], raw_rows: list[dict[str, Any]],
                judgments: list[dict[str, Any]] | None = None) -> dict[str, Any]:
    golden_by_id = {row["id"]: row for row in golden_rows}
    if len(golden_by_id) != len(golden_rows):
        raise ValueError("Golden Set 存在重复 id")

    raw_by_id = {row["case_id"]: row for row in raw_rows}
    if len(raw_by_id) != len(raw_rows):
        raise ValueError("原始结果存在重复 case_id")
    unknown_ids = sorted(set(raw_by_id) - set(golden_by_id))
    if unknown_ids:
        raise ValueError(f"原始结果包含 Golden Set 中不存在的 Case：{', '.join(unknown_ids)}")

    details: list[dict[str, Any]] = []
    retrieval_recall_hits = retrieval_recall_total = 0
    selected_recall_hits = selected_recall_total = 0
    evidence_type_hits = evidence_type_total = 0
    per_type_hits: Counter[str] = Counter()
    per_type_total: Counter[str] = Counter()
    citation_valid = citation_total = 0
    safe_hits = safe_total = 0
    route_hits = route_total = 0
    domain_hits = domain_total = 0
    entity_requirement_hits = entity_requirement_total = 0
    source_requirement_hits = source_requirement_total = 0
    propagation_consistency_failures = 0
    request_errors = 0
    dependency_failures = 0
    available_answers = answer_cases = 0
    latencies: list[float] = []
    retrieval_latencies: list[float] = []
    correctness_score = correctness_count = correctness_eligible = 0
    faithful_claims = all_judged_claims = faithfulness_count = faithfulness_eligible = 0
    context_precision_total = context_precision_count = context_precision_eligible = 0
    context_recall_hits = context_recall_total = context_recall_count = context_recall_eligible = 0
    selection_constraint_hits = selection_constraint_total = 0
    judged_by_case = judgment_index(golden_by_id, raw_by_id, judgments or [])

    for case_id, raw in raw_by_id.items():
        golden = golden_by_id[case_id]
        evidence = evidence_list(raw)
        predicted = predict_outcome(raw)
        expected = golden.get("expected_outcome", "ANSWER")
        expected_route = golden.get("expected_intent")
        actual_route = predicted_route(raw)
        routing = routing_snapshot(raw)
        expected_domain = golden.get("expected_domain")
        actual_domain = routing.get("domain") if routing else None
        expected_entity_requirement = golden.get("expected_entity_requirement")
        actual_entity_requirement = routing.get("entityRequirement") if routing else None
        expected_requirement = expected_source_requirement(golden)
        actual_requirement = {
            "knowledge": routing.get("knowledgeRequirement"),
            "community": routing.get("communityRequirement"),
            "state": routing.get("stateRequirement"),
        } if routing else None
        propagation_consistent = propagation_is_consistent(raw)
        if predicted == "ERROR":
            request_errors += 1
        dependency_failure = predicted == "ENTITY_RESOLUTION_UNAVAILABLE"
        if dependency_failure:
            dependency_failures += 1
        if expected == "ANSWER":
            answer_cases += 1
            available_answers += int(predicted == "ANSWER")

        actual_types = {str(item.get("type")) for item in evidence if item.get("type") is not None}
        expected_types = [str(item) for item in golden.get("expected_evidence_types", []) if item]
        type_hit = all(any(evidence_type_matches(required, item) for item in evidence)
                       for required in expected_types)
        if expected_types:
            evidence_type_total += 1
            evidence_type_hits += int(type_hit)
            for evidence_type in expected_types:
                per_type_total[evidence_type] += 1
                per_type_hits[evidence_type] += int(any(evidence_type_matches(evidence_type, item)
                                                        for item in evidence))

        gold_refs = [item for item in (gold_ref(ref) for ref in golden.get("gold_evidence_refs", [])) if item]
        selected_refs = [evidence_ref(item) for item in knowledge_evidence(evidence)]
        matched_selected_refs = [
            ref for ref in gold_refs if any(reference_matches(ref, actual) for actual in selected_refs)
        ]
        ranked_refs = [evidence_ref(item) for item in pre_selection_candidates(raw)]
        matched_retrieval_refs = [
            ref for ref in gold_refs if any(reference_matches(ref, actual) for actual in ranked_refs)
        ]
        retrieval_recall_hits += len(matched_retrieval_refs)
        retrieval_recall_total += len(gold_refs)
        selected_recall_hits += len(matched_selected_refs)
        selected_recall_total += len(gold_refs)

        selection_constraints = selection_constraint_result(golden, raw, evidence)
        if selection_constraints is not None:
            selection_constraint_total += 1
            selection_constraint_hits += int(selection_constraints["passed"])

        answer = raw.get("answer") if isinstance(raw.get("answer"), str) else ""
        citations = [int(number) for number in CITATION_PATTERN.findall(answer)]
        valid_citations = sum(1 for number in citations if 1 <= number <= len(evidence))
        citation_valid += valid_citations
        citation_total += len(citations)

        safe_match = outcome_matches(expected, predicted)
        if expected != "ANSWER":
            safe_total += 1
            safe_hits += int(safe_match)
        if isinstance(expected_route, str) and expected_route:
            route_total += 1
            route_hits += int(actual_route == expected_route)
        if isinstance(expected_domain, str) and expected_domain:
            domain_total += 1
            domain_hits += int(actual_domain == expected_domain)
        if isinstance(expected_entity_requirement, str) and expected_entity_requirement:
            entity_requirement_total += 1
            entity_requirement_hits += int(actual_entity_requirement == expected_entity_requirement)
        if expected_requirement is not None:
            source_requirement_total += 1
            source_requirement_hits += int(actual_requirement == expected_requirement)
        if propagation_consistent is False:
            propagation_consistency_failures += 1

        latency = raw.get("latency_ms")
        if isinstance(latency, (int, float)) and latency >= 0:
            latencies.append(float(latency))
        retrieval_latency_ms = retrieval_latency(raw)
        if retrieval_latency_ms is not None:
            retrieval_latencies.append(retrieval_latency_ms)

        judgment = judged_by_case.get(case_id)
        correctness = judgment.get("correctness") if isinstance(judgment, dict) else None
        faithfulness = judgment.get("faithfulness") if isinstance(judgment, dict) else None
        context_quality = judgment.get("context_quality") if isinstance(judgment, dict) else None
        if expected == "ANSWER":
            correctness_eligible += 1
            faithfulness_eligible += 1
            context_precision_eligible += 1
            context_recall_eligible += 1
        if isinstance(correctness, dict) and isinstance(correctness.get("score"), int):
            correctness_score += correctness["score"]
            correctness_count += 1
        if isinstance(faithfulness, dict) and isinstance(faithfulness.get("claims"), list):
            claims = [claim for claim in faithfulness["claims"]
                      if isinstance(claim, dict) and claim.get("claim_type", "EVIDENCE_FACT") == "EVIDENCE_FACT"]
            faithfulness_count += 1
            all_judged_claims += len(claims)
            faithful_claims += sum(1 for claim in claims if isinstance(claim, dict) and claim.get("supported") is True)
        if isinstance(context_quality, dict):
            contexts = context_quality.get("contexts")
            reference_claims = context_quality.get("reference_claims")
            if isinstance(contexts, list):
                relevance = [item.get("relevant") is True for item in contexts if isinstance(item, dict)]
                context_precision_total += average_precision(relevance)
                context_precision_count += 1
            if isinstance(reference_claims, list):
                context_recall_count += 1
                context_recall_total += len(reference_claims)
                context_recall_hits += sum(
                    1 for claim in reference_claims
                    if isinstance(claim, dict) and claim.get("supported") is True)

        details.append({
            "case_id": case_id,
            "category": golden.get("category"),
            "expected_outcome": expected,
            "predicted_outcome": predicted,
            "safe_outcome_match": safe_match,
            "expected_route": expected_route,
            "predicted_route": actual_route,
            "route_match": actual_route == expected_route if expected_route else None,
            "expected_domain": expected_domain,
            "predicted_domain": actual_domain,
            "domain_match": actual_domain == expected_domain if expected_domain else None,
            "expected_entity_requirement": expected_entity_requirement,
            "predicted_entity_requirement": actual_entity_requirement,
            "entity_requirement_match": actual_entity_requirement == expected_entity_requirement
            if expected_entity_requirement else None,
            "expected_source_requirement": expected_requirement,
            "predicted_source_requirement": actual_requirement,
            "source_requirement_match": actual_requirement == expected_requirement if expected_requirement else None,
            "route_propagation_consistent": propagation_consistent,
            "dependency_failure": dependency_failure,
            "required_evidence_types": expected_types,
            "actual_evidence_types": sorted(actual_types),
            "required_evidence_type_hit": type_hit if expected_types else None,
            "gold_evidence_count": len(gold_refs),
            "matched_retrieval_evidence_count": len(matched_retrieval_refs),
            "matched_selected_evidence_count": len(matched_selected_refs),
            "selection_constraints": selection_constraints,
            "citation_count": len(citations),
            "valid_citation_count": valid_citations,
            "latency_ms": latency,
            "retrieval_latency_ms": retrieval_latency_ms,
            "error": raw.get("error"),
            "correctness_judge": correctness,
            "faithfulness_judge": faithfulness,
            "context_quality_judge": context_quality,
        })

    per_type = {
        name: metric(ratio(per_type_hits[name], per_type_total[name]), per_type_hits[name], per_type_total[name])
        for name in sorted(per_type_total)
    }
    missing_case_ids = [row["id"] for row in golden_rows if row["id"] not in raw_by_id]
    return {
        "schema_version": 7,
        "coverage": {
            "golden_case_count": len(golden_rows),
            "scored_case_count": len(raw_rows),
            "missing_case_ids": missing_case_ids,
        },
        "metrics": {
            "retrieval_recall_at_10": metric(
                ratio(retrieval_recall_hits, retrieval_recall_total),
                retrieval_recall_hits, retrieval_recall_total),
            "context_precision": {
                "value": ratio(context_precision_total, context_precision_count),
                "score_total": round(context_precision_total, 6),
                "judged_case_count": context_precision_count,
                "eligible_case_count": context_precision_eligible,
            },
            "context_recall": {
                **metric(ratio(context_recall_hits, context_recall_total),
                         context_recall_hits, context_recall_total),
                "judged_case_count": context_recall_count,
                "eligible_case_count": context_recall_eligible,
            },
            "selected_evidence_id_recall_at_6": metric(
                ratio(selected_recall_hits, selected_recall_total),
                selected_recall_hits, selected_recall_total),
            "required_evidence_type_hit": metric(ratio(evidence_type_hits, evidence_type_total), evidence_type_hits, evidence_type_total),
            "required_evidence_type_hit_by_type": per_type,
            "selection_constraint_hit": metric(
                ratio(selection_constraint_hits, selection_constraint_total),
                selection_constraint_hits, selection_constraint_total),
            "citation_index_validity": metric(ratio(citation_valid, citation_total), citation_valid, citation_total),
            "safe_outcome_accuracy": metric(ratio(safe_hits, safe_total), safe_hits, safe_total),
            "route_accuracy": metric(ratio(route_hits, route_total), route_hits, route_total),
            "diagnostics": {
                "domain_match": metric(ratio(domain_hits, domain_total), domain_hits, domain_total),
                "entity_requirement_match": metric(ratio(entity_requirement_hits, entity_requirement_total),
                                                   entity_requirement_hits, entity_requirement_total),
                "source_requirement_match": metric(ratio(source_requirement_hits, source_requirement_total),
                                                     source_requirement_hits, source_requirement_total),
                "route_propagation_consistency_failure_count": propagation_consistency_failures,
            },
            "answer_availability": metric(ratio(available_answers, answer_cases), available_answers, answer_cases),
            "entity_resolution_dependency_failure_count": dependency_failures,
            "request_error_count": request_errors,
            "llm_judge_answer_correctness": {
                "value": ratio(correctness_score, 2 * correctness_count),
                "score_total": correctness_score,
                "judged_case_count": correctness_count,
                "eligible_case_count": correctness_eligible,
            },
            "faithfulness": {
                "value": ratio(faithful_claims, all_judged_claims),
                "supported_claim_count": faithful_claims,
                "claim_count": all_judged_claims,
                "judged_case_count": faithfulness_count,
                "eligible_case_count": faithfulness_eligible,
            },
            "latency_ms": {
                "sample_count": len(latencies),
                "p50": percentile(latencies, 0.5),
                "p95": percentile(latencies, 0.95),
                "max": round(max(latencies), 2) if latencies else None,
            },
            "retrieval_latency_ms": {
                "sample_count": len(retrieval_latencies),
                "p95": percentile(retrieval_latencies, 0.95),
            },
        },
        "case_results": details,
    }


def format_ratio(value: float | None) -> str:
    return "N/A" if value is None else f"{value * 100:.1f}%"


def render_report(summary: dict[str, Any]) -> str:
    metrics = summary["metrics"]
    coverage = summary["coverage"]
    rows = [
        "# Healing Planet RAG 评测报告",
        "",
        f"本次评分覆盖 {coverage['scored_case_count']}/{coverage['golden_case_count']} 条 Golden Case。",
        "",
        "## Core RAG Quality",
        "",
        "| 指标 | 结果 |",
        "|---|---:|",
        f"| Retrieval Recall@10 | {format_ratio(metrics['retrieval_recall_at_10']['value'])} ({metrics['retrieval_recall_at_10']['numerator']}/{metrics['retrieval_recall_at_10']['denominator']}) |",
        f"| Context Precision | {format_ratio(metrics['context_precision']['value'])}（Judge 覆盖 {metrics['context_precision']['judged_case_count']}/{metrics['context_precision']['eligible_case_count']}） |",
        f"| Context Recall | {format_ratio(metrics['context_recall']['value'])} ({metrics['context_recall']['numerator']}/{metrics['context_recall']['denominator']}，Judge 覆盖 {metrics['context_recall']['judged_case_count']}/{metrics['context_recall']['eligible_case_count']}) |",
        f"| Faithfulness | {format_ratio(metrics['faithfulness']['value'])} ({metrics['faithfulness']['supported_claim_count']}/{metrics['faithfulness']['claim_count']}，Judge 覆盖 {metrics['faithfulness']['judged_case_count']}/{metrics['faithfulness']['eligible_case_count']}) |",
        f"| LLM-Judge Answer Correctness | {format_ratio(metrics['llm_judge_answer_correctness']['value'])} ({metrics['llm_judge_answer_correctness']['score_total']}/{2 * metrics['llm_judge_answer_correctness']['judged_case_count']}，Judge 覆盖 {metrics['llm_judge_answer_correctness']['judged_case_count']}/{metrics['llm_judge_answer_correctness']['eligible_case_count']}) |",
        "",
        "## Safety & Reliability",
        "",
        "| 指标 | 结果 |",
        "|---|---:|",
        f"| Safe Outcome Accuracy | {format_ratio(metrics['safe_outcome_accuracy']['value'])} ({metrics['safe_outcome_accuracy']['numerator']}/{metrics['safe_outcome_accuracy']['denominator']}) |",
        f"| Answer Availability | {format_ratio(metrics['answer_availability']['value'])} ({metrics['answer_availability']['numerator']}/{metrics['answer_availability']['denominator']}) |",
        f"| P95 End-to-End Latency | {metrics['latency_ms']['p95']} ms |",
        f"| P95 Retrieval Latency | {metrics['retrieval_latency_ms']['p95']} ms（{metrics['retrieval_latency_ms']['sample_count']} 个样本） |",
        "",
        "## Regression Diagnostics",
        "",
        "| 指标 | 结果 |",
        "|---|---:|",
        f"| Route Accuracy | {format_ratio(metrics['route_accuracy']['value'])} ({metrics['route_accuracy']['numerator']}/{metrics['route_accuracy']['denominator']}) |",
        f"| Domain Match（辅助） | {format_ratio(metrics['diagnostics']['domain_match']['value'])} ({metrics['diagnostics']['domain_match']['numerator']}/{metrics['diagnostics']['domain_match']['denominator']}) |",
        f"| Entity Requirement Match（辅助） | {format_ratio(metrics['diagnostics']['entity_requirement_match']['value'])} ({metrics['diagnostics']['entity_requirement_match']['numerator']}/{metrics['diagnostics']['entity_requirement_match']['denominator']}) |",
        f"| Source Requirement Match（辅助） | {format_ratio(metrics['diagnostics']['source_requirement_match']['value'])} ({metrics['diagnostics']['source_requirement_match']['numerator']}/{metrics['diagnostics']['source_requirement_match']['denominator']}) |",
        f"| Route Propagation Consistency Failure（辅助） | {metrics['diagnostics']['route_propagation_consistency_failure_count']} |",
        f"| Required Evidence Type Hit | {format_ratio(metrics['required_evidence_type_hit']['value'])} ({metrics['required_evidence_type_hit']['numerator']}/{metrics['required_evidence_type_hit']['denominator']}) |",
        f"| Selection Constraint Hit | {format_ratio(metrics['selection_constraint_hit']['value'])} ({metrics['selection_constraint_hit']['numerator']}/{metrics['selection_constraint_hit']['denominator']}) |",
        f"| Selected Evidence ID Recall@6 | {format_ratio(metrics['selected_evidence_id_recall_at_6']['value'])} ({metrics['selected_evidence_id_recall_at_6']['numerator']}/{metrics['selected_evidence_id_recall_at_6']['denominator']}) |",
        f"| Citation Index Validity | {format_ratio(metrics['citation_index_validity']['value'])} ({metrics['citation_index_validity']['numerator']}/{metrics['citation_index_validity']['denominator']}) |",
        f"| Entity Resolution Dependency Failure | {metrics['entity_resolution_dependency_failure_count']} |",
        f"| Runner 请求错误 | {metrics['request_error_count']} |",
        "",
        "## 按证据类型",
        "",
        "| Evidence Type | Hit Rate |",
        "|---|---:|",
    ]
    rows.extend(
        f"| {name} | {format_ratio(item['value'])} ({item['numerator']}/{item['denominator']}) |"
        for name, item in metrics["required_evidence_type_hit_by_type"].items()
    )
    rows.extend(["", "## Case 明细", "", "| Case | 路由 | 预期行为 | 实际行为 | Retrieval Recall@10 | Selected ID Recall@6 | 选择约束 | 证据类型 | 引用 |", "|---|---|---|---|---:|---:|---|---|---:|"])
    rows.extend(
        f"| {item['case_id']} | {item['predicted_route'] or 'N/A'} | {item['expected_outcome']} | {item['predicted_outcome']} | "
        f"{item['matched_retrieval_evidence_count']}/{item['gold_evidence_count']} | "
        f"{item['matched_selected_evidence_count']}/{item['gold_evidence_count']} | "
        f"{'通过' if item['selection_constraints'] and item['selection_constraints']['passed'] else ('N/A' if item['selection_constraints'] is None else '未通过')} | "
        f"{'命中' if item['required_evidence_type_hit'] else ('N/A' if item['required_evidence_type_hit'] is None else '未命中')} | "
        f"{item['valid_citation_count']}/{item['citation_count']} |"
        for item in summary["case_results"]
    )
    if coverage["missing_case_ids"]:
        rows.extend(["", "## 覆盖不足", "", "尚未执行的 Case：" + ", ".join(coverage["missing_case_ids"])])
    rows.extend(["", "Retrieval Recall@10 使用 SourceAwareRanker 之后、EvidenceSelector 之前的统一 preSelectionRanked 快照；Selected Evidence ID Recall@6 仅作为精确 ID 回归诊断。", "", "Context Precision 按最终 Evidence 顺序计算平均精度（Average Precision）；Context Recall 按 gold_claims 的证据支持覆盖率计算。Judge 结果由固定模型、固定提示词和 temperature=0 生成，未覆盖的 Answer Case 不计入 Judge 指标分母。", "", "Domain / Entity Requirement / Source Requirement / Route Propagation 为辅助诊断，不改变核心指标口径。Route Accuracy 只比较 expected_intent 与 routing.resolvedIntent；未标注用户意图的域外 Case 不进入其分母。", "", "SAFE_REFUSAL 是结果族标签，可匹配明确的安全拒答子类型；ERROR 和依赖故障不属于正确安全拒答。", ""])
    return "\n".join(rows)


def write_json(path: Path, content: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(content, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = path.with_name(f".{path.name}.tmp")
    with temporary_path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")
    temporary_path.replace(path)


def safe_endpoint(url: str) -> str:
    parsed = urlsplit(url)
    return f"{parsed.scheme}://{parsed.netloc}{parsed.path}" if parsed.scheme and parsed.netloc else "<invalid-url>"


def judge_settings(args: argparse.Namespace) -> JudgeSettings:
    config = load_judge_config(args.judge_config)
    url = args.judge_url or os.getenv("JUDGE_URL") or config.get("url")
    model = args.judge_model or os.getenv("JUDGE_MODEL") or config.get("model")
    api_key_env = args.judge_api_key_env or config.get("api_key_env") or "JUDGE_API_KEY"
    timeout = args.judge_timeout if args.judge_timeout is not None else config.get("timeout", 90.0)
    retries = args.judge_retries if args.judge_retries is not None else config.get("retries", 1)
    if not isinstance(url, str) or not url.strip() or not isinstance(model, str) or not model.strip():
        raise ValueError("启用 --judge 时必须提供 --judge-url/JUDGE_URL 和 --judge-model/JUDGE_MODEL")
    if not isinstance(api_key_env, str) or not api_key_env.strip():
        raise ValueError("Judge 的 api_key_env 必须是非空环境变量名")
    if not isinstance(timeout, (int, float)) or not isinstance(retries, int):
        raise ValueError("Judge 配置中的 timeout 必须是数字，retries 必须是整数")
    api_key = os.getenv(api_key_env)
    if not api_key:
        raise ValueError(f"启用 --judge 时必须设置 API Key 环境变量 {api_key_env}")
    if timeout <= 0 or retries < 0:
        raise ValueError("--judge-timeout 必须大于 0，--judge-retries 不能小于 0")
    return JudgeSettings(url.strip(), api_key, model.strip(), float(timeout), retries)


def main() -> int:
    args = parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s", stream=sys.stdout)
    try:
        golden_rows = load_jsonl(args.golden, "Golden Set")
        raw_rows = load_jsonl(args.raw, "原始结果")
        validate_routing_trace_contract(raw_rows)
        judgments = load_judgments(args.judgments)
        judgment_statuses = Counter(str(item.get("status", "unknown")) for item in judgments)
        LOG.info("评分启动 golden=%s raw=%s judgments=%s statuses=%s", len(golden_rows), len(raw_rows),
                 len(judgments), dict(sorted(judgment_statuses.items())))
        judge_failures = 0
        if args.judge:
            settings = judge_settings(args)
            LOG.info("Judge 配置 model=%s endpoint=%s config=%s", settings.model, safe_endpoint(settings.url),
                     args.judge_config)
            judgments, judge_failures = run_judges(
                golden_rows, raw_rows, settings, judgments, args.refresh_judges,
                on_case_complete=lambda rows: write_jsonl(args.judgments, rows),
            )
            write_jsonl(args.judgments, judgments)
        summary = score_cases(golden_rows, raw_rows, judgments)
    except (OSError, ValueError) as exc:
        print(f"错误：{exc}", file=sys.stderr)
        return 2
    write_json(args.output, summary)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(render_report(summary), encoding="utf-8", newline="\n")
    metrics = summary["metrics"]
    print(f"已评分 {summary['coverage']['scored_case_count']} 条 Case。")
    print(f"Retrieval Recall@10: {format_ratio(metrics['retrieval_recall_at_10']['value'])}")
    print(f"Context Precision: {format_ratio(metrics['context_precision']['value'])}")
    print(f"Context Recall: {format_ratio(metrics['context_recall']['value'])}")
    print(f"Required Evidence Type Hit: {format_ratio(metrics['required_evidence_type_hit']['value'])}")
    print(f"Safe Outcome Accuracy: {format_ratio(metrics['safe_outcome_accuracy']['value'])}")
    print(f"LLM-Judge Answer Correctness: {format_ratio(metrics['llm_judge_answer_correctness']['value'])}")
    print(f"Faithfulness: {format_ratio(metrics['faithfulness']['value'])}")
    print(f"结果已写入：{args.output} 和 {args.report}")
    if args.judge:
        print(f"Judge 结果已写入：{args.judgments}")
    return 1 if judge_failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
