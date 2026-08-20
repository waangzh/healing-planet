# Healing Planet RAG 评测报告

本次评分覆盖 100/100 条 Golden Case。

| 指标 | 结果 |
|---|---:|
| Knowledge Recall@6 | 100.0% (83/83) |
| Required Evidence Type Hit | 100.0% (87/87) |
| Citation Index Validity | 100.0% (138/138) |
| Safe Outcome Accuracy | 73.3% (11/15) |
| Answer Availability | 100.0% (85/85) |
| Dependency Failure | 4 |
| Answer Correctness | 90.0% (153/170，Judge 覆盖 85/85) |
| Faithfulness | 95.9% (211/220，Judge 覆盖 85/85) |
| Hallucination Rate | 4.1% |
| False Refusal | 0 |
| Runner 请求错误 | 0 |
| P50 / P95 / Max Latency | 4959.42 / 39324.75 / 65812.79 ms |

## 按证据类型

| Evidence Type | Hit Rate |
|---|---:|
| CARE_GUIDE | 100.0% (23/23) |
| CARE_GUIDE:FERTILIZING | 100.0% (1/1) |
| CARE_GUIDE:GENERAL_CARE | 100.0% (1/1) |
| CARE_GUIDE:LIGHT | 100.0% (7/7) |
| CARE_GUIDE:TEMPERATURE | 100.0% (4/4) |
| CARE_GUIDE:WATERING | 100.0% (19/19) |
| COMMUNITY_POST | 100.0% (25/25) |
| LIVE_STATE | 100.0% (21/21) |
| SENSOR_HISTORY | 100.0% (8/8) |

## Case 明细

| Case | 预期行为 | 实际行为 | Recall 命中 | 证据类型 | 引用 |
|---|---|---|---:|---|---:|
| care_001 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_002 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_003 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_004 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_005 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_006 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_007 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_008 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_009 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_010 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_011 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_012 | ANSWER | ANSWER | 1/1 | 命中 | 2/2 |
| state_001 | ANSWER | ANSWER | 1/1 | 命中 | 2/2 |
| state_002 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| state_003 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| state_004 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| state_005 | ANSWER | ANSWER | 1/1 | 命中 | 2/2 |
| state_006 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| state_007 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| state_008 | ANSWER | ANSWER | 0/0 | 命中 | 2/2 |
| source_001 | ANSWER | ANSWER | 1/1 | 命中 | 4/4 |
| source_002 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| source_003 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| source_004 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| source_005 | ANSWER | ANSWER | 1/1 | 命中 | 6/6 |
| multi_001 | ANSWER | ANSWER | 2/2 | 命中 | 3/3 |
| multi_002 | ANSWER | ANSWER | 2/2 | 命中 | 5/5 |
| multi_003 | ANSWER | ANSWER | 2/2 | 命中 | 2/2 |
| multi_004 | ANSWER | ANSWER | 2/2 | 命中 | 8/8 |
| multi_005 | ANSWER | ANSWER | 2/2 | 命中 | 2/2 |
| safe_001 | REQUIRE_USER_ID | REQUIRE_USER_ID | 0/0 | N/A | 0/0 |
| safe_002 | REQUIRE_PLANT_INSTANCE | REQUIRE_PLANT_INSTANCE | 0/0 | N/A | 0/0 |
| safe_003 | STATE_UNAVAILABLE | STATE_UNAVAILABLE | 1/1 | 命中 | 0/0 |
| safe_004 | STATE_UNAVAILABLE | STATE_UNAVAILABLE | 1/1 | 命中 | 0/0 |
| safe_005 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | 0/0 |
| safe_006 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | 0/0 |
| robust_001 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| robust_002 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| robust_003 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| robust_004 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| entity_001 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| entity_002 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| entity_003 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| entity_004 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| entity_005 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| entity_006 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| entity_007 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| entity_008 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| entity_009 | ANSWER | ANSWER | 2/2 | 命中 | 2/2 |
| entity_010 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | 0/0 |
| entity_011 | INSUFFICIENT_KNOWLEDGE | ENTITY_RESOLUTION_UNAVAILABLE | 0/0 | N/A | 0/0 |
| entity_012 | INSUFFICIENT_KNOWLEDGE | ENTITY_RESOLUTION_UNAVAILABLE | 0/0 | N/A | 0/0 |
| entity_013 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | 0/0 |
| state_extra_001 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| state_extra_002 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| state_extra_003 | ANSWER | ANSWER | 0/0 | 命中 | 2/2 |
| state_extra_004 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| state_extra_005 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| state_extra_006 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| state_extra_007 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| state_extra_008 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| state_extra_009 | ANSWER | ANSWER | 1/1 | 命中 | 2/2 |
| state_extra_010 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| state_extra_011 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| state_extra_012 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| state_extra_013 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| state_extra_014 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| state_extra_015 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| state_extra_016 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| state_extra_017 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| source_extra_001 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| source_extra_002 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| source_extra_003 | ANSWER | ANSWER | 1/1 | 命中 | 2/2 |
| source_extra_004 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| source_extra_005 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| source_extra_006 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| source_extra_007 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| multi_extra_001 | ANSWER | ANSWER | 2/2 | 命中 | 3/3 |
| multi_extra_002 | ANSWER | ANSWER | 2/2 | 命中 | 2/2 |
| multi_extra_003 | ANSWER | ANSWER | 2/2 | 命中 | 7/7 |
| multi_extra_004 | ANSWER | ANSWER | 2/2 | 命中 | 3/3 |
| multi_extra_005 | ANSWER | ANSWER | 2/2 | 命中 | 2/2 |
| multi_extra_006 | ANSWER | ANSWER | 2/2 | 命中 | 2/2 |
| multi_extra_007 | ANSWER | ANSWER | 2/2 | 命中 | 2/2 |
| rerank_001 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| rerank_002 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| rerank_003 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| rerank_004 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| rerank_005 | ANSWER | ANSWER | 2/2 | 命中 | 2/2 |
| rerank_006 | ANSWER | ANSWER | 2/2 | 命中 | 3/3 |
| rerank_007 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| rerank_008 | ANSWER | ANSWER | 2/2 | 命中 | 2/2 |
| safe_extra_001 | REQUIRE_USER_ID | REQUIRE_USER_ID | 0/0 | N/A | 0/0 |
| safe_extra_002 | REQUIRE_PLANT_INSTANCE | REQUIRE_PLANT_INSTANCE | 0/0 | N/A | 0/0 |
| safe_extra_003 | INSUFFICIENT_KNOWLEDGE | ENTITY_RESOLUTION_UNAVAILABLE | 0/0 | N/A | 0/0 |
| safe_extra_004 | INSUFFICIENT_KNOWLEDGE | ENTITY_RESOLUTION_UNAVAILABLE | 0/0 | N/A | 0/0 |
| safe_extra_005 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | 0/0 |
| gen_001 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| gen_002 | ANSWER | ANSWER | 1/1 | 命中 | 3/3 |
| gen_003 | ANSWER | ANSWER | 2/2 | 命中 | 3/3 |

依赖故障根据结构化 rejectionReason 分类，并作为未命中计入固定 Golden Set 的 Recall 与 Evidence Type Hit 分母；Answer Availability 同样计入失败。

Judge 结果由固定模型、固定提示词和 temperature=0 生成；未覆盖的 Answer Case 不计入 Judge 指标分母。
