#!/usr/bin/env python3
"""离线计算 Healing Planet RAG 的确定性评测指标。"""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
from http.client import IncompleteRead, RemoteDisconnected
import json
import logging
import math
import os
import random
import re
import sys
import time
from collections import Counter
from dataclasses import dataclass
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
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
    "OUT_OF_SCOPE", "SAFE_REFUSAL",
}
ROUTING_TRACE_FIELDS = {
    "schemaVersion", "includeKnowledge", "includeCommunity", "includeState", "inputIntent", "resolvedIntent", "domain",
    "entityRequirement", "stateEvidenceNeed", "searchQuery", "knowledgeRequirement",
    "communityRequirement", "stateRequirement", "stateNeeds", "topicHints", "plantDomainConfidence",
}
SOURCE_REQUIREMENTS = {"ALLOWED", "FORBIDDEN", "REQUIRED"}
STATE_NEEDS = {"CURRENT", "HISTORY", "FRESHNESS", "DECISION_SUPPORT"}
ANSWERABILITY_RESULTS = {
    "ANSWERABLE", "INSUFFICIENT_EVIDENCE", "ENTITY_AMBIGUOUS", "ENTITY_CONFLICT", "ENTITY_UNKNOWN",
    "STATE_UNAVAILABLE", "STATE_STALE", "OUT_OF_SCOPE", "REQUIRE_USER_ID", "REQUIRE_PLANT_INSTANCE",
}
ENTITY_DEPENDENCY_FAILURES = {
    "llm_connect_timeout", "llm_read_timeout", "llm_connection_failed", "llm_invalid_json",
}
LOG = logging.getLogger("rag_eval.score")
JUDGE_RESPONSE_CONTRACTS = {
    "correctness": "correctness/v1",
    "faithfulness": "faithfulness/v1",
    "context_quality": "context_quality/v1",
}
RETRY_BACKOFF_MAX_SECONDS = 30.0


@dataclass(frozen=True)
class JudgeSettings:
    url: str
    api_key: str
    model: str
    timeout: float
    retries: int
    temperature: float = 0.0
    max_concurrent: int = 3


@dataclass(frozen=True)
class JudgeTask:
    case_id: str
    metric: str
    prompt: str
    validate: Callable[[dict[str, Any]], dict[str, Any]]
    content_fingerprint: str
    input_fingerprint: str


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
                        help="调用三个 LLM Judge，并将结果缓存到 --judgments")
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
    parser.add_argument("--judge-max-concurrent", type=int,
                        help="Judge 全局最大并发请求数，优先级高于本地配置（默认：3）")
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
            if row.get("schema_version") == 4:
                metric_name = row.get("metric")
                status = row.get("status")
                if metric_name not in JUDGE_RESPONSE_CONTRACTS:
                    raise ValueError(f"Judge 结果 {path}:{line_number} 的 v4 metric 无效")
                if not isinstance(row.get("content_fingerprint"), str) \
                        or not isinstance(row.get("input_fingerprint"), str):
                    raise ValueError(f"Judge 结果 {path}:{line_number} 的 v4 fingerprint 无效")
                if status not in {"ok", "error"}:
                    raise ValueError(f"Judge 结果 {path}:{line_number} 的 v4 status 无效")
                if status == "ok" and not isinstance(row.get("result"), dict):
                    raise ValueError(f"Judge 结果 {path}:{line_number} 的 v4 result 无效")
                if status == "error" and not isinstance(row.get("error"), str):
                    raise ValueError(f"Judge 结果 {path}:{line_number} 的 v4 error 无效")
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
    answerability = retrieval_trace(raw).get("answerability")
    structured_result = answerability.get("result") if isinstance(answerability, dict) else None
    structured_outcomes = {
        "ANSWERABLE": "ANSWER",
        "INSUFFICIENT_EVIDENCE": "INSUFFICIENT_KNOWLEDGE",
        "STATE_UNAVAILABLE": "STATE_UNAVAILABLE",
        "STATE_STALE": "SAFE_REFUSAL",
        "OUT_OF_SCOPE": "OUT_OF_SCOPE",
        "ENTITY_AMBIGUOUS": "INSUFFICIENT_KNOWLEDGE",
        "ENTITY_CONFLICT": "INSUFFICIENT_KNOWLEDGE",
        "ENTITY_UNKNOWN": "INSUFFICIENT_KNOWLEDGE",
        "REQUIRE_USER_ID": "REQUIRE_USER_ID",
        "REQUIRE_PLANT_INSTANCE": "REQUIRE_PLANT_INSTANCE",
    }
    if structured_result in structured_outcomes:
        return structured_outcomes[structured_result]
    answer = normalized_text(raw["answer"])
    if "个体化状态分析需要userid" in answer:
        return "REQUIRE_USER_ID"
    if "个体化状态分析需要plantinstanceid" in answer:
        return "REQUIRE_PLANT_INSTANCE"
    if "无法获取这盆植物的最新状态" in answer and "不能可靠判断" in answer:
        return "STATE_UNAVAILABLE"
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


def evidence_ref(evidence: dict[str, Any]) -> tuple[str, str | None, str | None]:
    source_id = value_from_evidence(evidence, "sourceId")
    knowledge_type = value_from_evidence(evidence, "knowledgeType")
    evidence_id = evidence.get("id")
    return (
        str(source_id) if source_id is not None else "",
        str(knowledge_type) if knowledge_type else None,
        str(evidence_id) if evidence_id else None,
    )


def gold_ref(ref: Any) -> tuple[str, str | None, str | None] | None:
    if not isinstance(ref, dict) or ref.get("source_id") is None:
        return None
    knowledge_type = ref.get("knowledge_type")
    evidence_id = ref.get("evidence_id")
    return (
        str(ref["source_id"]),
        str(knowledge_type) if knowledge_type else None,
        str(evidence_id) if evidence_id else None,
    )


def reference_matches(gold: tuple[str, str | None, str | None],
                      actual: tuple[str, str | None, str | None]) -> bool:
    return gold[0] == actual[0] \
        and (gold[1] is None or gold[1] == actual[1]) \
        and (gold[2] is None or gold[2] == actual[2])


def evidence_group_ref(evidence: dict[str, Any]) -> tuple[str, str | None]:
    source_id, knowledge_type, _ = evidence_ref(evidence)
    return source_id, knowledge_type


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
        elif routing.get("schemaVersion") != 4:
            invalid.append(f"{raw.get('case_id', '<unknown>')} schemaVersion 不是 4")
        source_values = [routing.get(name) for name in (
            "knowledgeRequirement", "communityRequirement", "stateRequirement")]
        if any(value not in SOURCE_REQUIREMENTS for value in source_values):
            invalid.append(f"{raw.get('case_id', '<unknown>')} 来源要求未使用 ALLOWED/FORBIDDEN/REQUIRED")
        state_needs = routing.get("stateNeeds")
        parsed_needs = set(state_needs.split(",")) if isinstance(state_needs, str) and state_needs else set()
        if not parsed_needs.issubset(STATE_NEEDS):
            invalid.append(f"{raw.get('case_id', '<unknown>')} stateNeeds 非法")
        topic_hints = routing.get("topicHints")
        if topic_hints is not None and not isinstance(topic_hints, str):
            invalid.append(f"{raw.get('case_id', '<unknown>')} topicHints 非法")
        confidence = routing.get("plantDomainConfidence")
        if not isinstance(confidence, (int, float)) or isinstance(confidence, bool) or not 0 <= confidence <= 1:
            invalid.append(f"{raw.get('case_id', '<unknown>')} plantDomainConfidence 非法")
        if routing.get("domain") not in {"PLANT_HINT", "UNKNOWN_HINT"}:
            invalid.append(f"{raw.get('case_id', '<unknown>')} domain 不是 soft hint")
        answerability = retrieval_trace(raw).get("answerability")
        if not isinstance(answerability, dict) or not isinstance(answerability.get("result"), str) \
                or not isinstance(answerability.get("reason"), str):
            invalid.append(f"{raw.get('case_id', '<unknown>')} 缺少 answerability result/reason")
        elif answerability.get("result") not in ANSWERABILITY_RESULTS:
            invalid.append(f"{raw.get('case_id', '<unknown>')} answerability result 非法")
    if invalid:
        raise ValueError("原始结果不满足 Retrieval Trace v4；请使用新版服务重新执行 run_eval.py：" + "; ".join(invalid))


def expected_source_requirement(golden: dict[str, Any]) -> dict[str, str] | None:
    value = golden.get("expected_source_requirement")
    if not isinstance(value, dict):
        return None
    expected = {name: value.get(name) for name in ("knowledge", "community", "state")}
    if any(not isinstance(mode, str) or not mode for mode in expected.values()):
        return None
    return expected


def answerability_result(raw: dict[str, Any]) -> str | None:
    answerability = retrieval_trace(raw).get("answerability")
    result = answerability.get("result") if isinstance(answerability, dict) else None
    return result if isinstance(result, str) and result else None


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
    group_counts = Counter(evidence_group_ref(item) for item in selected)
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


def retry_delay(error: HTTPError | None, attempt: int) -> float:
    retry_after = error.headers.get("Retry-After") if error is not None and error.headers else None
    if retry_after:
        try:
            return min(RETRY_BACKOFF_MAX_SECONDS, max(0.0, float(retry_after)))
        except ValueError:
            try:
                retry_at = parsedate_to_datetime(retry_after)
                if retry_at.tzinfo is None:
                    retry_at = retry_at.replace(tzinfo=timezone.utc)
                return min(RETRY_BACKOFF_MAX_SECONDS,
                           max(0.0, (retry_at - datetime.now(timezone.utc)).total_seconds()))
            except (TypeError, ValueError, OverflowError):
                pass
    exponential = min(RETRY_BACKOFF_MAX_SECONDS, float(2 ** (attempt - 1)))
    return min(RETRY_BACKOFF_MAX_SECONDS, exponential + random.uniform(0.0, min(1.0, exponential * 0.25)))


def call_judge(prompt: str, settings: JudgeSettings) -> dict[str, Any]:
    payload = json.dumps({
        "model": settings.model,
        "temperature": settings.temperature,
        "messages": [{"role": "user", "content": prompt}],
    }, ensure_ascii=False).encode("utf-8")
    last_error: Exception | None = None
    for attempt in range(1, settings.retries + 2):
        request = Request(settings.url, data=payload, headers={
            "Authorization": "Bearer " + settings.api_key,
            "Content-Type": "application/json",
            "Accept": "application/json",
        }, method="POST")
        try:
            with urlopen(request, timeout=settings.timeout) as response:
                body = json.loads(response.read().decode("utf-8"))
            return parse_json_content(extract_message_content(body))
        except HTTPError as exc:
            last_error = exc
            retryable = exc.code == 429 or 500 <= exc.code < 600
            if not retryable or attempt > settings.retries:
                raise RuntimeError(f"Judge 调用失败：HTTP {exc.code} {exc.reason}") from exc
            delay = retry_delay(exc, attempt)
        # Judge 可能以 chunked 响应返回；上游在响应体结束前断开时，
        # http.client 会抛出 IncompleteRead/RemoteDisconnected，应按可重试网络错误处理。
        except (URLError, OSError, IncompleteRead, RemoteDisconnected, ValueError) as exc:
            last_error = exc
            if attempt > settings.retries:
                break
            delay = retry_delay(None, attempt)
        LOG.warning("Judge 请求失败 attempt=%s/%s type=%s message=%s retry_in=%.2fs", attempt,
                    settings.retries + 1, type(last_error).__name__, str(last_error), delay)
        time.sleep(delay)
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


def legacy_judge_input_fingerprint(case: dict[str, Any], raw: dict[str, Any], settings: JudgeSettings,
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


def make_judge_task(case_id: str, metric_name: str, prompt: str,
                    validate: Callable[[dict[str, Any]], dict[str, Any]],
                    settings: JudgeSettings | None) -> JudgeTask:
    response_contract = JUDGE_RESPONSE_CONTRACTS[metric_name]
    content_fingerprint = fingerprint({
        "metric": metric_name,
        "prompt": prompt,
        "response_contract": response_contract,
    })
    input_fingerprint = "" if settings is None else fingerprint({
        "endpoint_fingerprint": fingerprint(settings.url),
        "request": {
            "model": settings.model,
            "temperature": settings.temperature,
            "messages": [{"role": "user", "content": prompt}],
        },
        "response_contract": response_contract,
    })
    return JudgeTask(case_id, metric_name, prompt, validate, content_fingerprint, input_fingerprint)


def render_correctness_judge_task(case: dict[str, Any], raw: dict[str, Any], template: str,
                                  settings: JudgeSettings | None = None) -> JudgeTask:
    prompt = render_prompt(template, {
        "QUESTION": str(case.get("query", "")),
        "GOLD_CLAIMS": format_claims(case.get("gold_claims")),
        "REFERENCE_ANSWER": str(case.get("reference_answer", "")),
        "GENERATED_ANSWER": str(raw.get("answer", "")),
    })
    return make_judge_task(case["id"], "correctness", prompt, validate_correctness, settings)


def render_faithfulness_judge_task(case: dict[str, Any], raw: dict[str, Any], template: str,
                                   settings: JudgeSettings | None = None) -> JudgeTask:
    prompt = render_prompt(template, {
        "QUESTION": str(case.get("query", "")),
        "SYSTEM_POLICY": system_policy_context(case, raw),
        "EVIDENCE": format_evidence(evidence_list(raw)),
        "GENERATED_ANSWER": str(raw.get("answer", "")),
    })
    return make_judge_task(case["id"], "faithfulness", prompt, validate_faithfulness, settings)


def render_context_judge_task(case: dict[str, Any], raw: dict[str, Any], template: str,
                              settings: JudgeSettings | None = None) -> JudgeTask:
    claims = case.get("gold_claims") if isinstance(case.get("gold_claims"), list) else []
    evidence = evidence_list(raw)
    prompt = render_prompt(template, {
        "QUESTION": str(case.get("query", "")),
        "REFERENCE_ANSWER": str(case.get("reference_answer", "")),
        "GOLD_CLAIMS": format_indexed_claims(claims),
        "EVIDENCE": format_evidence(evidence),
    })
    return make_judge_task(
        case["id"], "context_quality", prompt,
        lambda value: validate_context_quality(value, len(evidence), len(claims)), settings)


def render_judge_tasks(case: dict[str, Any], raw: dict[str, Any], settings: JudgeSettings | None,
                       correctness_prompt: str, faithfulness_prompt: str,
                       context_prompt: str) -> tuple[JudgeTask, JudgeTask, JudgeTask]:
    return (
        render_correctness_judge_task(case, raw, correctness_prompt, settings),
        render_faithfulness_judge_task(case, raw, faithfulness_prompt, settings),
        render_context_judge_task(case, raw, context_prompt, settings),
    )


def source_fingerprint(case: dict[str, Any], raw: dict[str, Any]) -> str:
    return fingerprint({
        "case": {
            "id": case["id"], "query": case.get("query"), "gold_claims": case.get("gold_claims"),
            "reference_answer": case.get("reference_answer"), "expected_outcome": case.get("expected_outcome"),
        },
        "raw": {"answer": raw.get("answer"), "evidence": evidence_list(raw)},
    })


def judgment_cache_key(item: dict[str, Any]) -> tuple[Any, ...]:
    if item.get("schema_version") == 4:
        return 4, item.get("case_id"), item.get("metric"), item.get("input_fingerprint")
    return item.get("schema_version"), item.get("case_id"), item.get("input_fingerprint")


def merge_judgments(existing: list[dict[str, Any]], updated: list[dict[str, Any]]) -> list[dict[str, Any]]:
    updated_keys = {judgment_cache_key(item) for item in updated}
    retained = [item for item in existing if judgment_cache_key(item) not in updated_keys]
    return retained + updated


def judgment_row(task: JudgeTask, settings: JudgeSettings, result: dict[str, Any] | None,
                 error: str | None) -> dict[str, Any]:
    return {
        "schema_version": 4,
        "case_id": task.case_id,
        "metric": task.metric,
        "content_fingerprint": task.content_fingerprint,
        "input_fingerprint": task.input_fingerprint,
        "judged_at": datetime.now(timezone.utc).isoformat(),
        "status": "ok" if error is None else "error",
        "judge": {
            "endpoint": safe_endpoint(settings.url),
            "model": settings.model,
            "temperature": settings.temperature,
        },
        "result": result,
        "error": error,
    }


def execute_judge_task(task: JudgeTask, settings: JudgeSettings) -> dict[str, Any]:
    if not task.input_fingerprint:
        raise ValueError("JudgeTask 缺少 input_fingerprint")
    return task.validate(call_judge(task.prompt, settings))


def run_judges(golden_rows: list[dict[str, Any]], raw_rows: list[dict[str, Any]], settings: JudgeSettings,
               existing: list[dict[str, Any]], refresh: bool,
               on_task_complete: Callable[[list[dict[str, Any]]], None] | None = None) -> tuple[list[dict[str, Any]], int]:
    correctness_prompt = prompt_text("correctness-judge.txt")
    faithfulness_prompt = prompt_text("faithfulness-judge.txt")
    context_prompt = prompt_text("context-judge.txt")
    golden_by_id = {case["id"]: case for case in golden_rows}
    existing_v4 = [item for item in existing if item.get("schema_version") == 4]
    cached = {
        (item.get("case_id"), item.get("metric"), item.get("input_fingerprint")): item
        for item in existing_v4 if item.get("status") == "ok"
    }
    updated: list[dict[str, Any]] = []
    failures = 0
    pending: list[JudgeTask] = []
    candidates = [raw for raw in raw_rows if golden_by_id[raw["case_id"]].get("expected_outcome", "ANSWER") == "ANSWER"
                  and predict_outcome(raw) == "ANSWER"]
    LOG.info("Judge 启动 candidates=%s existing_cache=%s refresh=%s max_concurrent=%s model=%s endpoint=%s",
             len(candidates), len(existing_v4), refresh, settings.max_concurrent, settings.model,
             safe_endpoint(settings.url))

    for raw in candidates:
        case = golden_by_id[raw["case_id"]]
        for task in render_judge_tasks(
                case, raw, settings, correctness_prompt, faithfulness_prompt, context_prompt):
            cache_key = (task.case_id, task.metric, task.input_fingerprint)
            if not refresh and cache_key in cached:
                LOG.info("case=%s metric=%s Judge 缓存命中", task.case_id, task.metric)
                continue
            pending.append(task)

    LOG.info("Judge 待执行 tasks=%s", len(pending))
    with ThreadPoolExecutor(max_workers=settings.max_concurrent, thread_name_prefix="rag-judge") as executor:
        future_to_task = {executor.submit(execute_judge_task, task, settings): task for task in pending}
        for future in as_completed(future_to_task):
            task = future_to_task[future]
            try:
                result = future.result()
                row = judgment_row(task, settings, result, None)
                LOG.info("case=%s metric=%s Judge 完成", task.case_id, task.metric)
            except (RuntimeError, ValueError) as exc:
                failures += 1
                row = judgment_row(task, settings, None, str(exc))
                LOG.warning("case=%s metric=%s Judge 失败: %s", task.case_id, task.metric, exc)
            updated.append(row)
            if on_task_complete:
                # 运行中保留旧行；全部任务完成后再由最终写入统一为 v4，避免中断时损失旧结果。
                on_task_complete(merge_judgments(existing, updated))

    judgments = merge_judgments(existing_v4, updated)
    LOG.info("Judge 完成 records=%s failures=%s", len(judgments), failures)
    return judgments, failures


def judgment_index(golden_by_id: dict[str, dict[str, Any]], raw_by_id: dict[str, dict[str, Any]],
                   judgments: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    correctness_prompt = prompt_text("correctness-judge.txt")
    faithfulness_prompt = prompt_text("faithfulness-judge.txt")
    context_prompt = prompt_text("context-judge.txt")
    expected_content: dict[tuple[str, str], str] = {}
    for case_id, raw in raw_by_id.items():
        case = golden_by_id[case_id]
        if case.get("expected_outcome", "ANSWER") != "ANSWER" or predict_outcome(raw) != "ANSWER":
            continue
        for task in render_judge_tasks(
                case, raw, None, correctness_prompt, faithfulness_prompt, context_prompt):
            expected_content[(case_id, task.metric)] = task.content_fingerprint

    indexed: dict[str, dict[str, Any]] = {}
    for item in judgments:
        case_id = item.get("case_id")
        metric_name = item.get("metric")
        if item.get("schema_version") != 4 or item.get("status") != "ok" \
                or not isinstance(case_id, str) or case_id not in raw_by_id \
                or metric_name not in JUDGE_RESPONSE_CONTRACTS \
                or item.get("content_fingerprint") != expected_content.get((case_id, metric_name)) \
                or not isinstance(item.get("result"), dict):
            continue
        indexed.setdefault(case_id, {"case_id": case_id})[metric_name] = item["result"]

    # 旧聚合格式仅用于未启用 --judge 时的只读兼容；只填补没有 v4 结果的 metric。
    for item in judgments:
        case_id = item.get("case_id")
        if item.get("schema_version") == 4 or item.get("status") not in {"ok", "partial_error"} \
                or not isinstance(case_id, str) or case_id not in raw_by_id \
                or item.get("source_fingerprint") != source_fingerprint(golden_by_id[case_id], raw_by_id[case_id]):
            continue
        judge = item.get("judge")
        model = judge.get("model") if isinstance(judge, dict) else None
        temperature = judge.get("temperature") if isinstance(judge, dict) else None
        if not isinstance(model, str) or not isinstance(temperature, (int, float)):
            continue
        legacy_settings = JudgeSettings("", "", model, 1.0, 0, float(temperature), 1)
        expected_legacy_fingerprint = legacy_judge_input_fingerprint(
            golden_by_id[case_id], raw_by_id[case_id], legacy_settings,
            correctness_prompt, faithfulness_prompt, context_prompt)
        if item.get("input_fingerprint") != expected_legacy_fingerprint:
            continue
        target = indexed.setdefault(case_id, {"case_id": case_id})
        for metric_name in JUDGE_RESPONSE_CONTRACTS:
            if metric_name not in target and isinstance(item.get(metric_name), dict):
                target[metric_name] = item[metric_name]
    return indexed


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
    answerability_hits = answerability_total = 0
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
        expected_answerability = golden.get("expected_answerability")
        actual_answerability = answerability_result(raw)
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
        if isinstance(expected_answerability, str) and expected_answerability:
            answerability_total += 1
            answerability_hits += int(actual_answerability == expected_answerability)
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
            "expected_answerability": expected_answerability,
            "predicted_answerability": actual_answerability,
            "answerability_match": actual_answerability == expected_answerability if expected_answerability else None,
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
            "answerability_accuracy": metric(ratio(answerability_hits, answerability_total),
                                               answerability_hits, answerability_total),
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
        f"| Answerability Accuracy | {format_ratio(metrics['answerability_accuracy']['value'])} ({metrics['answerability_accuracy']['numerator']}/{metrics['answerability_accuracy']['denominator']}) |",
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
    rows.extend(["", "Retrieval Recall@10 使用 SourceAwareRanker 之后、EvidenceSelector 之前的统一 preSelectionRanked 快照；Selected Evidence ID Recall@6 仅作为精确 ID 回归诊断。", "", "Context Precision 按最终 Evidence 顺序计算平均精度（Average Precision）；Context Recall 按 gold_claims 的证据支持覆盖率计算。Judge 结果由固定模型、固定提示词和 temperature=0 生成，未覆盖的 Answer Case 不计入 Judge 指标分母。", "", "Answerability Accuracy 直接比较 Retrieval Trace v4 的结构化 answerability；Source Requirement / Route Propagation 为辅助诊断。Route Accuracy 只比较 expected_intent 与 routing.resolvedIntent。", "", "SAFE_REFUSAL 是结果族标签，可匹配明确的安全拒答子类型；ERROR 和依赖故障不属于正确安全拒答。", ""])
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
    requested_max_concurrent = getattr(args, "judge_max_concurrent", None)
    max_concurrent = (requested_max_concurrent if requested_max_concurrent is not None
                      else config.get("max_concurrent", 3))
    if not isinstance(url, str) or not url.strip() or not isinstance(model, str) or not model.strip():
        raise ValueError("启用 --judge 时必须提供 --judge-url/JUDGE_URL 和 --judge-model/JUDGE_MODEL")
    if not isinstance(api_key_env, str) or not api_key_env.strip():
        raise ValueError("Judge 的 api_key_env 必须是非空环境变量名")
    if not isinstance(timeout, (int, float)) or type(retries) is not int or type(max_concurrent) is not int:
        raise ValueError("Judge 配置中的 timeout 必须是数字，retries 和 max_concurrent 必须是整数")
    api_key = os.getenv(api_key_env)
    if not api_key:
        raise ValueError(f"启用 --judge 时必须设置 API Key 环境变量 {api_key_env}")
    if timeout <= 0 or retries < 0 or max_concurrent <= 0:
        raise ValueError("--judge-timeout 必须大于 0，--judge-retries 不能小于 0，--judge-max-concurrent 必须大于 0")
    return JudgeSettings(url.strip(), api_key, model.strip(), float(timeout), retries,
                         max_concurrent=max_concurrent)


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
            LOG.info("Judge 配置 model=%s endpoint=%s max_concurrent=%s config=%s", settings.model,
                     safe_endpoint(settings.url), settings.max_concurrent, args.judge_config)
            judgments, judge_failures = run_judges(
                golden_rows, raw_rows, settings, judgments, args.refresh_judges,
                on_task_complete=lambda rows: write_jsonl(args.judgments, rows),
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
