# Healing Planet RAG 评测报告

本次评分覆盖 114/114 条 Golden Case。

| 指标 | 结果 |
|---|---:|
| Knowledge Recall@6 | 94.2% (98/104) |
| Precision@6 | 21.2% (98/462，覆盖 77 个有 gold_evidence_refs 的 Case) |
| Required Evidence Type Hit | 98.0% (99/101) |
| Selection Constraint Hit | 83.3% (5/6) |
| Citation Index Validity | 100.0% (212/212) |
| Safe Outcome Accuracy | 100.0% (15/15) |
| Answer Availability | 93.9% (93/99) |
| Dependency Failure | 0 |
| Answer Correctness | 93.5% (174/186，Judge 覆盖 93/99) |
| Faithfulness | 99.5% (369/371，Judge 覆盖 93/99) |
| Hallucination Rate | 0.5% |
| False Refusal | 6 |
| Runner 请求错误 | 5 |
| P50 / P95 / Max Latency | 23017.47 / 141729.69 / 180469.7 ms |

## 按证据类型

| Evidence Type | Hit Rate |
|---|---:|
| CARE_GUIDE | 100.0% (23/23) |
| CARE_GUIDE:FERTILIZING | 100.0% (1/1) |
| CARE_GUIDE:GENERAL_CARE | 83.3% (5/6) |
| CARE_GUIDE:HUMIDITY | 100.0% (2/2) |
| CARE_GUIDE:LIGHT | 100.0% (9/9) |
| CARE_GUIDE:TEMPERATURE | 100.0% (6/6) |
| CARE_GUIDE:WATERING | 100.0% (21/21) |
| COMMUNITY_POST | 97.1% (33/34) |
| LIVE_STATE | 100.0% (21/21) |
| SENSOR_HISTORY | 100.0% (8/8) |

## Case 明细

| Case | 预期行为 | 实际行为 | Recall 命中 | 选择约束 | 证据类型 | 引用 |
|---|---|---|---:|---|---|---:|
| care_001 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 2/2 |
| care_002 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| care_003 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| care_004 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| care_005 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 2/2 |
| care_006 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 2/2 |
| care_007 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| care_008 | ANSWER | ERROR | 1/1 | N/A | 命中 | 0/0 |
| care_009 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| care_010 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| care_011 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 2/2 |
| care_012 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 2/2 |
| state_001 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 3/3 |
| state_002 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 1/1 |
| state_003 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 1/1 |
| state_004 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 2/2 |
| state_005 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 2/2 |
| state_006 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 1/1 |
| state_007 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 1/1 |
| state_008 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 1/1 |
| source_001 | ANSWER | ERROR | 1/1 | N/A | 命中 | 0/0 |
| source_002 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 3/3 |
| source_003 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| source_004 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 4/4 |
| source_005 | ANSWER | ANSWER | 0/1 | N/A | 命中 | 7/7 |
| multi_001 | ANSWER | ANSWER | 1/2 | N/A | 命中 | 3/3 |
| multi_002 | ANSWER | ANSWER | 2/2 | N/A | 命中 | 5/5 |
| multi_003 | ANSWER | ANSWER | 2/2 | N/A | 命中 | 5/5 |
| multi_004 | ANSWER | ANSWER | 2/2 | N/A | 命中 | 9/9 |
| multi_005 | ANSWER | ANSWER | 2/2 | N/A | 命中 | 4/4 |
| safe_001 | REQUIRE_USER_ID | REQUIRE_USER_ID | 0/0 | N/A | N/A | 0/0 |
| safe_002 | REQUIRE_PLANT_INSTANCE | REQUIRE_PLANT_INSTANCE | 0/0 | N/A | N/A | 0/0 |
| safe_003 | STATE_UNAVAILABLE | STATE_UNAVAILABLE | 1/1 | N/A | 命中 | 0/0 |
| safe_004 | STATE_UNAVAILABLE | STATE_UNAVAILABLE | 1/1 | N/A | 命中 | 0/0 |
| safe_005 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | N/A | 0/0 |
| safe_006 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | N/A | 0/0 |
| robust_001 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| robust_002 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| robust_003 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| robust_004 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| entity_001 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| entity_002 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| entity_003 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| entity_004 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| entity_005 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| entity_006 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| entity_007 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| entity_008 | ANSWER | ERROR | 1/1 | N/A | 命中 | 0/0 |
| entity_009 | ANSWER | ANSWER | 2/2 | N/A | 命中 | 2/2 |
| entity_010 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | N/A | 0/0 |
| entity_011 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | N/A | 0/0 |
| entity_012 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | N/A | 0/0 |
| entity_013 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | N/A | 0/0 |
| state_extra_001 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 1/1 |
| state_extra_002 | ANSWER | ERROR | 0/0 | N/A | 命中 | 0/0 |
| state_extra_003 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 1/1 |
| state_extra_004 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 1/1 |
| state_extra_005 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 1/1 |
| state_extra_006 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 1/1 |
| state_extra_007 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 1/1 |
| state_extra_008 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 2/2 |
| state_extra_009 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 2/2 |
| state_extra_010 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 1/1 |
| state_extra_011 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 1/1 |
| state_extra_012 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 1/1 |
| state_extra_013 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 1/1 |
| state_extra_014 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 1/1 |
| state_extra_015 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 1/1 |
| state_extra_016 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 1/1 |
| state_extra_017 | ANSWER | ANSWER | 0/0 | N/A | 命中 | 1/1 |
| source_extra_001 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 3/3 |
| source_extra_002 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 2/2 |
| source_extra_003 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 2/2 |
| source_extra_004 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| source_extra_005 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| source_extra_006 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 2/2 |
| source_extra_007 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| multi_extra_001 | ANSWER | ANSWER | 2/2 | N/A | 命中 | 3/3 |
| multi_extra_002 | ANSWER | ANSWER | 2/2 | N/A | 命中 | 2/2 |
| multi_extra_003 | ANSWER | ANSWER | 2/2 | N/A | 命中 | 3/3 |
| multi_extra_004 | ANSWER | ANSWER | 2/2 | N/A | 命中 | 3/3 |
| multi_extra_005 | ANSWER | ANSWER | 2/2 | N/A | 命中 | 3/3 |
| multi_extra_006 | ANSWER | ANSWER | 2/2 | N/A | 命中 | 2/2 |
| multi_extra_007 | ANSWER | ANSWER | 2/2 | N/A | 命中 | 5/5 |
| rerank_001 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| rerank_002 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| rerank_003 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| rerank_004 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| rerank_005 | ANSWER | ANSWER | 2/2 | N/A | 命中 | 2/2 |
| rerank_006 | ANSWER | ANSWER | 2/2 | N/A | 命中 | 3/3 |
| rerank_007 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| rerank_008 | ANSWER | ANSWER | 2/2 | N/A | 命中 | 2/2 |
| safe_extra_001 | REQUIRE_USER_ID | REQUIRE_USER_ID | 0/0 | N/A | N/A | 0/0 |
| safe_extra_002 | REQUIRE_PLANT_INSTANCE | REQUIRE_PLANT_INSTANCE | 0/0 | N/A | N/A | 0/0 |
| safe_extra_003 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | N/A | 0/0 |
| safe_extra_004 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | N/A | 0/0 |
| safe_extra_005 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | N/A | 0/0 |
| gen_001 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| gen_002 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 3/3 |
| gen_003 | ANSWER | ANSWER | 2/2 | N/A | 命中 | 2/2 |
| selection_broad_001 | ANSWER | ANSWER | 4/4 | 通过 | 命中 | 7/7 |
| selection_focused_001 | ANSWER | ANSWER | 1/1 | 通过 | 命中 | 1/1 |
| selection_mixed_001 | ANSWER | ANSWER | 1/1 | 通过 | 命中 | 12/12 |
| selection_broad_002 | ANSWER | ANSWER | 4/4 | 通过 | 命中 | 5/5 |
| route_mixed_clause_001 | ANSWER | ANSWER | 1/2 | N/A | 命中 | 14/14 |
| route_mixed_clause_002 | ANSWER | ERROR | 2/2 | N/A | 命中 | 0/0 |
| route_community_only_001 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 6/6 |
| route_knowledge_only_001 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 7/7 |
| route_only_official_001 | ANSWER | ANSWER | 0/1 | N/A | 未命中 | 1/1 |
| route_only_community_001 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |
| selection_two_community_001 | ANSWER | INSUFFICIENT_KNOWLEDGE | 0/2 | 未通过 | 未命中 | 0/0 |
| selection_two_community_002 | ANSWER | ANSWER | 1/1 | 通过 | 命中 | 3/3 |
| route_mixed_topic_split_001 | ANSWER | ANSWER | 2/2 | N/A | 命中 | 3/3 |
| route_community_negation_001 | ANSWER | ANSWER | 1/1 | N/A | 命中 | 1/1 |

依赖故障根据结构化 rejectionReason 分类，并作为未命中计入固定 Golden Set 的 Recall 与 Evidence Type Hit 分母；Answer Availability 同样计入失败。

Judge 结果由固定模型、固定提示词和 temperature=0 生成；未覆盖的 Answer Case 不计入 Judge 指标分母。
