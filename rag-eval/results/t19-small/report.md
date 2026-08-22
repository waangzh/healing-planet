# Healing Planet RAG 评测报告

本次评分覆盖 30/30 条 Golden Case。

## Core RAG Quality

| 指标 | 结果 |
|---|---:|
| Retrieval Recall@10 | 97.0% (32/33) |
| Context Precision | 91.9%（Judge 覆盖 27/28） |
| Context Recall | 94.4% (34/36，Judge 覆盖 27/28) |
| Faithfulness | 98.4% (121/123，Judge 覆盖 27/28) |
| LLM-Judge Answer Correctness | 88.9% (48/54，Judge 覆盖 27/28) |

## Safety & Reliability

| 指标 | 结果 |
|---|---:|
| Safe Outcome Accuracy | 100.0% (2/2) |
| Answer Availability | 96.4% (27/28) |
| P95 End-to-End Latency | 99902.64 ms |
| P95 Retrieval Latency | 295.29 ms（29 个样本） |

## Regression Diagnostics

| 指标 | 结果 |
|---|---:|
| Route Accuracy | 100.0% (29/29) |
| Domain Match（辅助） | 100.0% (1/1) |
| Entity Requirement Match（辅助） | 100.0% (1/1) |
| Source Requirement Match（辅助） | 100.0% (7/7) |
| Route Propagation Consistency Failure（辅助） | 0 |
| Required Evidence Type Hit | 89.3% (25/28) |
| Selection Constraint Hit | N/A (0/0) |
| Selected Evidence ID Recall@6 | 90.9% (30/33) |
| Citation Index Validity | 100.0% (65/65) |
| Entity Resolution Dependency Failure | 0 |
| Runner 请求错误 | 0 |

## 按证据类型

| Evidence Type | Hit Rate |
|---|---:|
| CARE_GUIDE:FERTILIZING | 100.0% (4/4) |
| CARE_GUIDE:GENERAL_CARE | 100.0% (1/1) |
| CARE_GUIDE:HUMIDITY | 100.0% (6/6) |
| CARE_GUIDE:LIGHT | 100.0% (1/1) |
| CARE_GUIDE:TEMPERATURE | 100.0% (3/3) |
| CARE_GUIDE:WATERING | 100.0% (3/3) |
| COMMUNITY_POST | 80.0% (12/15) |

## Case 明细

| Case | 路由 | 预期行为 | 实际行为 | Retrieval Recall@10 | Selected ID Recall@6 | 选择约束 | 证据类型 | 引用 |
|---|---|---|---|---:|---:|---|---|---:|
| 1 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 2/2 |
| 2 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 2/2 |
| 3 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 1/1 |
| 4 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 2/2 |
| 5 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 2/2 |
| 6 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 1/1 |
| 7 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 7/7 |
| 8 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 1/1 |
| 9 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 3/3 |
| 10 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 2/2 |
| 11 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 2/2 |
| 12 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 6/6 |
| 13 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 2/2 |
| 14 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 0/1 | N/A | 未命中 | 2/2 |
| 15 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 2/2 |
| 16 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 0/1 | N/A | 未命中 | 1/1 |
| 17 | GENERAL_CARE | ANSWER | INSUFFICIENT_KNOWLEDGE | 0/1 | 0/1 | N/A | 未命中 | 0/0 |
| 18 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 4/4 |
| 19 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 2/2 |
| 20 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 2/2 |
| 21 | GENERAL_CARE | ANSWER | ANSWER | 2/2 | 2/2 | N/A | 命中 | 3/3 |
| 22 | GENERAL_CARE | ANSWER | ANSWER | 2/2 | 2/2 | N/A | 命中 | 3/3 |
| 23 | GENERAL_CARE | ANSWER | ANSWER | 2/2 | 2/2 | N/A | 命中 | 3/3 |
| 24 | GENERAL_CARE | ANSWER | ANSWER | 2/2 | 2/2 | N/A | 命中 | 2/2 |
| 25 | GENERAL_CARE | ANSWER | ANSWER | 2/2 | 2/2 | N/A | 命中 | 3/3 |
| 26 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 2/2 |
| 27 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 1/1 |
| 28 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 2/2 |
| 29 | GENERAL_CARE | SAFE_REFUSAL | INSUFFICIENT_KNOWLEDGE | 0/0 | 0/0 | N/A | N/A | 0/0 |
| 30 | GENERAL_CARE | OUT_OF_SCOPE | OUT_OF_SCOPE | 0/0 | 0/0 | N/A | N/A | 0/0 |

Retrieval Recall@10 使用 SourceAwareRanker 之后、EvidenceSelector 之前的统一 preSelectionRanked 快照；Selected Evidence ID Recall@6 仅作为精确 ID 回归诊断。

Context Precision 按最终 Evidence 顺序计算平均精度（Average Precision）；Context Recall 按 gold_claims 的证据支持覆盖率计算。Judge 结果由固定模型、固定提示词和 temperature=0 生成，未覆盖的 Answer Case 不计入 Judge 指标分母。

Domain / Entity Requirement / Source Requirement / Route Propagation 为辅助诊断，不改变核心指标口径。Route Accuracy 只比较 expected_intent 与 routing.resolvedIntent；未标注用户意图的域外 Case 不进入其分母。

SAFE_REFUSAL 是结果族标签，可匹配明确的安全拒答子类型；ERROR 和依赖故障不属于正确安全拒答。
