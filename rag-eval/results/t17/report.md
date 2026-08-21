# Healing Planet RAG 评测报告

本次评分覆盖 10/114 条 Golden Case。

## Core RAG Quality

| 指标 | 结果 |
|---|---:|
| Retrieval Recall@10 | 100.0% (10/10) |
| Context Precision | 88.9%（Judge 覆盖 9/10） |
| Context Recall | 88.9% (8/9，Judge 覆盖 9/10) |
| Faithfulness | 100.0% (14/14，Judge 覆盖 9/10) |
| LLM-Judge Answer Correctness | 83.3% (15/18，Judge 覆盖 9/10) |

## Safety & Reliability

| 指标 | 结果 |
|---|---:|
| Safe Outcome Accuracy | N/A (0/0) |
| Answer Availability | 90.0% (9/10) |
| P95 End-to-End Latency | 124375.11 ms |
| P95 Retrieval Latency | 3678.97 ms（10 个样本） |

## Regression Diagnostics

| 指标 | 结果 |
|---|---:|
| Route Accuracy | 100.0% (10/10) |
| Required Evidence Type Hit | 100.0% (10/10) |
| Selection Constraint Hit | N/A (0/0) |
| Selected Evidence ID Recall@6 | 100.0% (10/10) |
| Citation Index Validity | 100.0% (12/12) |
| Entity Resolution Dependency Failure | 0 |
| Runner 请求错误 | 1 |

## 按证据类型

| Evidence Type | Hit Rate |
|---|---:|
| CARE_GUIDE | 100.0% (10/10) |

## Case 明细

| Case | 路由 | 预期行为 | 实际行为 | Retrieval Recall@10 | Selected ID Recall@6 | 选择约束 | 证据类型 | 引用 |
|---|---|---|---|---:|---:|---|---|---:|
| care_001 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 2/2 |
| care_002 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 1/1 |
| care_003 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 1/1 |
| care_004 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 1/1 |
| care_005 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 2/2 |
| care_006 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 2/2 |
| care_007 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 1/1 |
| care_008 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 1/1 |
| care_009 | GENERAL_CARE | ANSWER | ANSWER | 1/1 | 1/1 | N/A | 命中 | 1/1 |
| care_010 | GENERAL_CARE | ANSWER | ERROR | 1/1 | 1/1 | N/A | 命中 | 0/0 |

## 覆盖不足

尚未执行的 Case：care_011, care_012, state_001, state_002, state_003, state_004, state_005, state_006, state_007, state_008, source_001, source_002, source_003, source_004, source_005, multi_001, multi_002, multi_003, multi_004, multi_005, safe_001, safe_002, safe_003, safe_004, safe_005, safe_006, robust_001, robust_002, robust_003, robust_004, entity_001, entity_002, entity_003, entity_004, entity_005, entity_006, entity_007, entity_008, entity_009, entity_010, entity_011, entity_012, entity_013, state_extra_001, state_extra_002, state_extra_003, state_extra_004, state_extra_005, state_extra_006, state_extra_007, state_extra_008, state_extra_009, state_extra_010, state_extra_011, state_extra_012, state_extra_013, state_extra_014, state_extra_015, state_extra_016, state_extra_017, source_extra_001, source_extra_002, source_extra_003, source_extra_004, source_extra_005, source_extra_006, source_extra_007, multi_extra_001, multi_extra_002, multi_extra_003, multi_extra_004, multi_extra_005, multi_extra_006, multi_extra_007, rerank_001, rerank_002, rerank_003, rerank_004, rerank_005, rerank_006, rerank_007, rerank_008, safe_extra_001, safe_extra_002, safe_extra_003, safe_extra_004, safe_extra_005, gen_001, gen_002, gen_003, selection_broad_001, selection_focused_001, selection_mixed_001, selection_broad_002, route_mixed_clause_001, route_mixed_clause_002, route_community_only_001, route_knowledge_only_001, route_only_official_001, route_only_community_001, selection_two_community_001, selection_two_community_002, route_mixed_topic_split_001, route_community_negation_001

Retrieval Recall@10 使用 SourceAwareRanker 之后、EvidenceSelector 之前的统一 preSelectionRanked 快照；Selected Evidence ID Recall@6 仅作为精确 ID 回归诊断。

Context Precision 按最终 Evidence 顺序计算平均精度（Average Precision）；Context Recall 按 gold_claims 的证据支持覆盖率计算。Judge 结果由固定模型、固定提示词和 temperature=0 生成，未覆盖的 Answer Case 不计入 Judge 指标分母。

SAFE_REFUSAL 是结果族标签，可匹配明确的安全拒答子类型；ERROR 和依赖故障不属于正确安全拒答。
