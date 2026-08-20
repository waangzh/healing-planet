# Healing Planet RAG 评测报告

本次评分覆盖 13/53 条 Golden Case。

| 指标 | 结果 |
|---|---:|
| Knowledge Recall@6 | 100.0% (7/7) |
| Required Evidence Type Hit | 100.0% (6/6) |
| Citation Index Validity | 100.0% (7/7) |
| Safe Outcome Accuracy | 100.0% (7/7) |
| Answer Correctness | N/A (0/0，Judge 覆盖 0/6) |
| Faithfulness | N/A (0/0，Judge 覆盖 0/6) |
| Hallucination Rate | N/A |
| False Refusal | 0 |
| Runner 请求错误 | 0 |
| P50 / P95 / Max Latency | 205.56 / 24565.84 / 24659.96 ms |

## 按证据类型

| Evidence Type | Hit Rate |
|---|---:|
| CARE_GUIDE:LIGHT | 100.0% (2/2) |
| CARE_GUIDE:TEMPERATURE | 100.0% (1/1) |
| CARE_GUIDE:WATERING | 100.0% (3/3) |

## Case 明细

| Case | 预期行为 | 实际行为 | Recall 命中 | 证据类型 | 引用 |
|---|---|---|---:|---|---:|
| entity_001 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| entity_002 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| entity_003 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| entity_004 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| entity_005 | ANSWER | ANSWER | 1/1 | 命中 | 1/1 |
| entity_006 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | 0/0 |
| entity_007 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | 0/0 |
| entity_008 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | 0/0 |
| entity_009 | ANSWER | ANSWER | 2/2 | 命中 | 2/2 |
| entity_010 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | 0/0 |
| entity_011 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | 0/0 |
| entity_012 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | 0/0 |
| entity_013 | INSUFFICIENT_KNOWLEDGE | INSUFFICIENT_KNOWLEDGE | 0/0 | N/A | 0/0 |

## 覆盖不足

尚未执行的 Case：care_001, care_002, care_003, care_004, care_005, care_006, care_007, care_008, care_009, care_010, care_011, care_012, state_001, state_002, state_003, state_004, state_005, state_006, state_007, state_008, source_001, source_002, source_003, source_004, source_005, multi_001, multi_002, multi_003, multi_004, multi_005, safe_001, safe_002, safe_003, safe_004, safe_005, safe_006, robust_001, robust_002, robust_003, robust_004

Judge 结果由固定模型、固定提示词和 temperature=0 生成；未覆盖的 Answer Case 不计入 Judge 指标分母。
