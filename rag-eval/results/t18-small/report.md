# Healing Planet RAG 评测报告

本次评分覆盖 30/30 条 Golden Case。

## Core RAG Quality

| 指标 | 结果 |
|---|---:|
| Retrieval Recall@10 | 63.6% (21/33) |
| Context Precision | 84.1%（Judge 覆盖 23/28） |
| Context Recall | 82.1% (23/28，Judge 覆盖 23/28) |
| Faithfulness | 93.4% (57/61，Judge 覆盖 23/28) |
| LLM-Judge Answer Correctness | 84.8% (39/46，Judge 覆盖 23/28) |

## Safety & Reliability

| 指标 | 结果 |
|---|---:|
| Safe Outcome Accuracy | 100.0% (2/2) |
| Answer Availability | 82.1% (23/28) |
| P95 End-to-End Latency | 43285.14 ms |
| P95 Retrieval Latency | 971.1 ms（30 个样本） |

## Regression Diagnostics

| 指标 | 结果 |
|---|---:|
| Route Accuracy | 83.3% (25/30) |
| Required Evidence Type Hit | 67.9% (19/28) |
| Selection Constraint Hit | N/A (0/0) |
| Selected Evidence ID Recall@6 | 63.6% (21/33) |
| Citation Index Validity | 100.0% (44/44) |
| Entity Resolution Dependency Failure | 1 |
| Runner 请求错误 | 0 |

## 按证据类型

| Evidence Type | Hit Rate |
|---|---:|
| CARE_GUIDE:FERTILIZING | 100.0% (4/4) |
| CARE_GUIDE:GENERAL_CARE | 100.0% (1/1) |
| CARE_GUIDE:HUMIDITY | 83.3% (5/6) |
| CARE_GUIDE:LIGHT | 100.0% (1/1) |
| CARE_GUIDE:TEMPERATURE | 66.7% (2/3) |
| CARE_GUIDE:WATERING | 66.7% (2/3) |
| COMMUNITY_POST | 40.0% (6/15) |

## Case 明细

| Case | 路由 | 预期行为 | 实际行为 | Retrieval Recall@10 | Selected ID Recall@6 | 选择约束 | 证据类型 | 引用 |
|---|---|---|---|---:|---:|---|---|---:|
| 1 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 1/1 |
| 2 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 1/1 |
| 3 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 1/1 |
| 4 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 2/2 |
| 5 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 1/1 |
| 6 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 1/1 |
| 7 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 9/9 |
| 8 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 1/1 |
| 9 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 1/1 |
| 10 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 1/1 |
| 11 | COMMUNITY_SEARCH | ANSWER | ENTITY_RESOLUTION_UNAVAILABLE | 0/1 | 0/1 | N/A | 未命中 | 0/0 |
| 12 | COMMUNITY_SEARCH | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 5/5 |
| 13 | COMMUNITY_SEARCH | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 5/5 |
| 14 | GENERAL_CARE | ANSWER | ANSWER | 0/1 | 0/1 | N/A | 未命中 | 2/2 |
| 15 | COMMUNITY_SEARCH | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 2/2 |
| 16 | GENERAL_CARE | ANSWER | ANSWER | 0/1 | 0/1 | N/A | 未命中 | 1/1 |
| 17 | GENERAL_CARE | ANSWER | ANSWER | 0/1 | 0/1 | N/A | 未命中 | 0/0 |
| 18 | GENERAL_CARE | ANSWER | ANSWER | 0/1 | 0/1 | N/A | 未命中 | 1/1 |
| 19 | COMMUNITY_SEARCH | ANSWER | INSUFFICIENT_KNOWLEDGE | 0/1 | 0/1 | N/A | 未命中 | 0/0 |
| 20 | COMMUNITY_SEARCH | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 1/1 |
| 21 | COMMUNITY_SEARCH | ANSWER | INSUFFICIENT_KNOWLEDGE | 0/2 | 0/2 | N/A | 未命中 | 0/0 |
| 22 | COMMUNITY_SEARCH | ANSWER | ANSWER | 2/2 | 2/2 | N/A | 命中 | 3/3 |
| 23 | COMMUNITY_SEARCH | ANSWER | INSUFFICIENT_KNOWLEDGE | 0/2 | 0/2 | N/A | 未命中 | 0/0 |
| 24 | COMMUNITY_SEARCH | ANSWER | ANSWER | 2/2 | 2/2 | N/A | 命中 | 2/2 |
| 25 | COMMUNITY_SEARCH | ANSWER | INSUFFICIENT_KNOWLEDGE | 0/2 | 0/2 | N/A | 未命中 | 0/0 |
| 26 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 1/1 |
| 27 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 1/1 |
| 28 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 1/1 |
| 29 | GENERAL_CARE | SAFE_REFUSAL | INSUFFICIENT_KNOWLEDGE | 0/0 | 0/0 | N/A | N/A | 0/0 |
| 30 | GENERAL_CARE | SAFE_REFUSAL | INSUFFICIENT_KNOWLEDGE | 0/0 | 0/0 | N/A | N/A | 0/0 |

Retrieval Recall@10 使用 SourceAwareRanker 之后、EvidenceSelector 之前的统一 preSelectionRanked 快照；Selected Evidence ID Recall@6 仅作为精确 ID 回归诊断。

Context Precision 按最终 Evidence 顺序计算平均精度（Average Precision）；Context Recall 按 gold_claims 的证据支持覆盖率计算。Judge 结果由固定模型、固定提示词和 temperature=0 生成，未覆盖的 Answer Case 不计入 Judge 指标分母。

SAFE_REFUSAL 是结果族标签，可匹配明确的安全拒答子类型；ERROR 和依赖故障不属于正确安全拒答。
