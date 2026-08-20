# Healing Planet RAG 评测报告

本次评分覆盖 53/53 条 Golden Case。

| 指标 | 结果 |
|---|---:|
| Knowledge Recall@6 | 100.0% (40/40) |
| Required Evidence Type Hit | 100.0% (39/39) |
| Citation Index Validity | 100.0% (82/82) |
| Safe Outcome Accuracy | 70.0% (7/10) |
| Answer Availability | 86.0% (37/43) |
| Dependency Failure | 9 |
| Answer Correctness | 90.5% (67/74，Judge 覆盖 37/43) |
| Faithfulness | 98.0% (99/101，Judge 覆盖 37/43) |
| Hallucination Rate | 2.0% |
| False Refusal | 6 |
| Runner 请求错误 | 0 |
| P50 / P95 / Max Latency | 7609.77 / 119522.04 / 181370.63 ms |

## 按证据类型

| Evidence Type | Hit Rate |
|---|---:|
| CARE_GUIDE | 100.0% (22/22) |
| CARE_GUIDE:LIGHT | 100.0% (2/2) |
| CARE_GUIDE:TEMPERATURE | 100.0% (1/1) |
| CARE_GUIDE:WATERING | 100.0% (4/4) |
| COMMUNITY_POST | 100.0% (10/10) |
| LIVE_STATE | 100.0% (7/7) |
| SENSOR_HISTORY | 100.0% (2/2) |

## Case 明细

| Case | 预期行为 | 实际行为 | Recall 命中 | 证据类型 | 引用 |
|---|---|---|---:|---|---:|
| care_001 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_002 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_003 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_004 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_005 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_006 | ANSWER | ANSWER | 1/1 | 命中 | 6/6 |
| care_007 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_008 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_009 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_010 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_011 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_012 | ANSWER | ANSWER | 1/1 | 命中 | 2/2 |
| state_001 | ANSWER | ANSWER | 1/1 | 命中 | 2/2 |
| state_002 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| state_003 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| state_004 | ANSWER | ANSWER | 1/1 | 命中 | 2/2 |
| state_005 | ANSWER | ANSWER | 1/1 | 命中 | 2/2 |
| state_006 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| state_007 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| state_008 | ANSWER | ANSWER | 0/0 | 命中 | 1/1 |
| source_001 | ANSWER | ANSWER | 1/1 | 命中 | 4/4 |
| source_002 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| source_003 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| source_004 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| source_005 | ANSWER | ANSWER | 1/1 | 命中 | 7/7 |
| multi_001 | ANSWER | ANSWER | 2/2 | 命中 | 3/3 |
| multi_002 | ANSWER | ANSWER | 2/2 | 命中 | 5/5 |
| multi_003 | ANSWER | ANSWER | 2/2 | 命中 | 3/3 |
| multi_004 | ANSWER | ANSWER | 2/2 | 命中 | 15/15 |
| multi_005 | ANSWER | ANSWER | 2/2 | 命中 | 5/5 |
| safe_001 | REQUIRE_USER_ID | REQUIRE_USER_ID | 0/0 | N/A | 0/0 |
| safe_002 | REQUIRE_PLANT_INSTANCE | REQUIRE_PLANT_INSTANCE | 0/0 | N/A | 0/0 |
| safe_003 | STATE_UNAVAILABLE | STATE_UNAVAILABLE | 1/1 | 命中 | 0/0 |
| safe_004 | STATE_UNAVAILABLE | STATE_UNAVAILABLE | 1/1 | 命中 | 0/0 |
| safe_005 | INSUFFICIENT_KNOWLEDGE | ENTITY_RESOLUTION_UNAVAILABLE | 0/0 | N/A | 0/0 |
| safe_006 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | 0/0 |
| robust_001 | ANSWER | ENTITY_RESOLUTION_UNAVAILABLE | 0/1 | 未命中 | 0/0 |
| robust_002 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| robust_003 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| robust_004 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| entity_001 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| entity_002 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| entity_003 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| entity_004 | ANSWER | ENTITY_RESOLUTION_UNAVAILABLE | 0/1 | 未命中 | 0/0 |
| entity_005 | ANSWER | ENTITY_RESOLUTION_UNAVAILABLE | 0/1 | 未命中 | 0/0 |
| entity_006 | ANSWER | ENTITY_RESOLUTION_UNAVAILABLE | 0/1 | 未命中 | 0/0 |
| entity_007 | ANSWER | ENTITY_RESOLUTION_UNAVAILABLE | 0/1 | 未命中 | 0/0 |
| entity_008 | ANSWER | ENTITY_RESOLUTION_UNAVAILABLE | 0/1 | 未命中 | 0/0 |
| entity_009 | ANSWER | ANSWER | 2/2 | 命中 | 2/2 |
| entity_010 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | 0/0 |
| entity_011 | INSUFFICIENT_KNOWLEDGE | ENTITY_RESOLUTION_UNAVAILABLE | 0/0 | N/A | 0/0 |
| entity_012 | INSUFFICIENT_KNOWLEDGE | ENTITY_RESOLUTION_UNAVAILABLE | 0/0 | N/A | 0/0 |
| entity_013 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | 0/0 |

依赖故障根据结构化 rejectionReason 分类，不计入检索 Recall 与 Evidence Type Hit 分母；Answer Availability 仍计入失败。

Judge 结果由固定模型、固定提示词和 temperature=0 生成；未覆盖的 Answer Case 不计入 Judge 指标分母。
