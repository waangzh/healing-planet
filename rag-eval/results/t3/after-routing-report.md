# Healing Planet RAG 评测报告

本次评分覆盖 40/40 条 Golden Case。

| 指标 | 结果 |
|---|---:|
| Knowledge Recall@6 | 75.6% (31/41) |
| Required Evidence Type Hit | 83.3% (30/36) |
| Citation Index Validity | 100.0% (68/68) |
| Safe Outcome Accuracy | 100.0% (6/6) |
| Answer Correctness | N/A (0/0，Judge 覆盖 0/34) |
| Faithfulness | N/A (0/0，Judge 覆盖 0/34) |
| Hallucination Rate | N/A |
| False Refusal | 1 |
| Runner 请求错误 | 0 |
| P50 / P95 / Max Latency | 32087.59 / 59029.15 / 66855.42 ms |

## 按证据类型

| Evidence Type | Hit Rate |
|---|---:|
| CARE_GUIDE | 96.8% (30/31) |
| COMMUNITY_POST | 50.0% (5/10) |
| LIVE_STATE | 100.0% (8/8) |
| SENSOR_HISTORY | 100.0% (8/8) |

## Case 明细

| Case | 预期行为 | 实际行为 | Recall 命中 | 证据类型 | 引用 |
|---|---|---|---:|---|---:|
| care_001 | ANSWER | ANSWER | 1/1 | 命中 | 3/3 |
| care_002 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_003 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_004 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_005 | ANSWER | ANSWER | 1/1 | 命中 | 3/3 |
| care_006 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_007 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_008 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_009 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_010 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| care_011 | ANSWER | ANSWER | 1/1 | 命中 | 3/3 |
| care_012 | ANSWER | ANSWER | 1/1 | 命中 | 2/2 |
| state_001 | ANSWER | ANSWER | 1/1 | 命中 | 4/4 |
| state_002 | ANSWER | ANSWER | 0/1 | 命中 | 2/2 |
| state_003 | ANSWER | ANSWER | 0/1 | 命中 | 2/2 |
| state_004 | ANSWER | ANSWER | 1/1 | 命中 | 3/3 |
| state_005 | ANSWER | ANSWER | 1/1 | 命中 | 3/3 |
| state_006 | ANSWER | ANSWER | 0/1 | 命中 | 3/3 |
| state_007 | ANSWER | ANSWER | 0/1 | 命中 | 2/2 |
| state_008 | ANSWER | ANSWER | 1/1 | 命中 | 4/4 |
| source_001 | ANSWER | ANSWER | 1/1 | 命中 | 4/4 |
| source_002 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| source_003 | ANSWER | ANSWER | 1/1 | 命中 | 4/4 |
| source_004 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| source_005 | ANSWER | ANSWER | 1/1 | 命中 | 2/2 |
| multi_001 | ANSWER | ANSWER | 1/2 | 未命中 | 1/1 |
| multi_002 | ANSWER | ANSWER | 1/2 | 未命中 | 1/1 |
| multi_003 | ANSWER | ANSWER | 1/2 | 未命中 | 4/4 |
| multi_004 | ANSWER | ANSWER | 1/2 | 未命中 | 3/3 |
| multi_005 | ANSWER | ANSWER | 1/2 | 未命中 | 1/1 |
| safe_001 | REQUIRE_USER_ID | REQUIRE_USER_ID | 0/0 | N/A | 0/0 |
| safe_002 | REQUIRE_PLANT_INSTANCE | REQUIRE_PLANT_INSTANCE | 0/0 | N/A | 0/0 |
| safe_003 | STATE_UNAVAILABLE | STATE_UNAVAILABLE | 1/1 | 命中 | 0/0 |
| safe_004 | STATE_UNAVAILABLE | STATE_UNAVAILABLE | 1/1 | 命中 | 0/0 |
| safe_005 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | 0/0 |
| safe_006 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | 0/0 |
| robust_001 | ANSWER | INSUFFICIENT_KNOWLEDGE | 0/1 | 未命中 | 0/0 |
| robust_002 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| robust_003 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| robust_004 | ANSWER | ANSWER | 1/1 | 命中 | 2/2 |

Judge 结果由固定模型、固定提示词和 temperature=0 生成；未覆盖的 Answer Case 不计入 Judge 指标分母。
