# Healing Planet RAG 评测报告

本次评分覆盖 15/100 条 Golden Case。

| 指标 | 结果 |
|---|---:|
| Knowledge Recall@6 | 100.0% (2/2) |
| Required Evidence Type Hit | 100.0% (2/2) |
| Citation Index Validity | 100.0% (1/1) |
| Safe Outcome Accuracy | 66.7% (10/15) |
| Answer Availability | N/A (0/0) |
| Dependency Failure | 4 |
| Answer Correctness | N/A (0/0，Judge 覆盖 0/0) |
| Faithfulness | N/A (0/0，Judge 覆盖 0/0) |
| Hallucination Rate | N/A |
| False Refusal | 0 |
| Runner 请求错误 | 0 |
| P50 / P95 / Max Latency | 202.93 / 15303.45 / 31533.28 ms |

## 按证据类型

| Evidence Type | Hit Rate |
|---|---:|
| CARE_GUIDE | 100.0% (2/2) |

## Case 明细

| Case | 预期行为 | 实际行为 | Recall 命中 | 证据类型 | 引用 |
|---|---|---|---:|---|---:|
| safe_001 | REQUIRE_USER_ID | REQUIRE_USER_ID | 0/0 | N/A | 0/0 |
| safe_002 | REQUIRE_PLANT_INSTANCE | REQUIRE_PLANT_INSTANCE | 0/0 | N/A | 0/0 |
| safe_003 | STATE_UNAVAILABLE | STATE_UNAVAILABLE | 1/1 | 命中 | 0/0 |
| safe_004 | STATE_UNAVAILABLE | STATE_UNAVAILABLE | 1/1 | 命中 | 0/0 |
| safe_005 | INSUFFICIENT_KNOWLEDGE | ENTITY_RESOLUTION_UNAVAILABLE | 0/0 | N/A | 0/0 |
| safe_006 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | 0/0 |
| entity_010 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | 0/0 |
| entity_011 | INSUFFICIENT_KNOWLEDGE | ENTITY_RESOLUTION_UNAVAILABLE | 0/0 | N/A | 0/0 |
| entity_012 | INSUFFICIENT_KNOWLEDGE | ANSWER | 0/0 | N/A | 1/1 |
| entity_013 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | 0/0 |
| safe_extra_001 | REQUIRE_USER_ID | REQUIRE_USER_ID | 0/0 | N/A | 0/0 |
| safe_extra_002 | REQUIRE_PLANT_INSTANCE | REQUIRE_PLANT_INSTANCE | 0/0 | N/A | 0/0 |
| safe_extra_003 | INSUFFICIENT_KNOWLEDGE | ENTITY_RESOLUTION_UNAVAILABLE | 0/0 | N/A | 0/0 |
| safe_extra_004 | INSUFFICIENT_KNOWLEDGE | ENTITY_RESOLUTION_UNAVAILABLE | 0/0 | N/A | 0/0 |
| safe_extra_005 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | 0/0 |

## 覆盖不足

尚未执行的 Case：care_001, care_002, care_003, care_004, care_005, care_006, care_007, care_008, care_009, care_010, care_011, care_012, state_001, state_002, state_003, state_004, state_005, state_006, state_007, state_008, source_001, source_002, source_003, source_004, source_005, multi_001, multi_002, multi_003, multi_004, multi_005, robust_001, robust_002, robust_003, robust_004, entity_001, entity_002, entity_003, entity_004, entity_005, entity_006, entity_007, entity_008, entity_009, state_extra_001, state_extra_002, state_extra_003, state_extra_004, state_extra_005, state_extra_006, state_extra_007, state_extra_008, state_extra_009, state_extra_010, state_extra_011, state_extra_012, state_extra_013, state_extra_014, state_extra_015, state_extra_016, state_extra_017, source_extra_001, source_extra_002, source_extra_003, source_extra_004, source_extra_005, source_extra_006, source_extra_007, multi_extra_001, multi_extra_002, multi_extra_003, multi_extra_004, multi_extra_005, multi_extra_006, multi_extra_007, rerank_001, rerank_002, rerank_003, rerank_004, rerank_005, rerank_006, rerank_007, rerank_008, gen_001, gen_002, gen_003

依赖故障根据结构化 rejectionReason 分类，并作为未命中计入固定 Golden Set 的 Recall 与 Evidence Type Hit 分母；Answer Availability 同样计入失败。

Judge 结果由固定模型、固定提示词和 temperature=0 生成；未覆盖的 Answer Case 不计入 Judge 指标分母。
