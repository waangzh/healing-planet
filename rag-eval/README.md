# Healing Planet RAG Evaluation Sets

评测数据分为互不混用的两个集合：

- `golden.jsonl`：114 条开发回归集，保留原内容和既有默认行为。它用于日常修复验证；不要再为单个失败现象向其中追加定向 Case。
- `holdout.jsonl`：30 条冻结盲测集，ID 固定为字符串 `"1"` 至 `"30"`，不携带类型含义。它只用于里程碑或发布前的泛化评估；不得根据其单条结果修改检索、路由、提示词或答案策略。

`dataset-manifest.json` 固定两个集合及 `source-catalog.json` 的 SHA-256。开始评测或更新索引前先执行 `python .\test\validate_datasets.py`；校验失败表示 Case 或对应数据库来源快照已被改动，应建立新的版本化集合，不应原地改写 Holdout。

回归测试统一放在 `test` 目录，可从本目录执行：

```powershell
python -m unittest discover -s .\test -p "test_*.py"
```

开发回归集前 53 条是不可替换的 `core`；后续用例扩大了个体状态、多证据、来源感知、实体解析、rerank 困难检索、安全结果、通用养护、口语鲁棒性、生成质量和 Evidence Selection 等切片。来源快照绑定当前数据库中的绿萝、白掌、空气凤梨、龟背竹社区帖与正式指南。

每行均可直接映射为 `RagChatRequest`：`user_id`、`plant_instance_id`、`canonical_plant_id` 分别对应 API 的 `userId`、`plantInstanceId`、`canonicalPlantId`；未出现的字段应传 `null`。`input_intent` 保持 `null`，用于测量当前 `QueryRouter` 的真实路由行为。`state_fixture` 只供 Eval Profile 的 Plant State Stub 使用，生产服务不应读取该目录。

Golden 采用“最小充分证据覆盖”原则：`gold_claims` 只包含直接回答问题所必需的事实、会改变结论的冲突或不确定性，以及用户明确要求说明的维度；`expected_evidence_types` 只包含得出这些结论所必需的证据类型。检索结果中额外出现的背景指南或社区内容不自动成为必答项，回答无需为了消费全部证据而机械复述。只有用户明确询问正式指南，或指南确实参与当前判断、冲突消解时，才将对应指南写入必答 Claim 和必需证据类型。

评测脚本默认调用 `/api/rag/chat/stream`，消费 `evidence`、`entity_resolution`、`retrieval_trace`、`token`、`error` 和 `done` SSE 事件后重新组装答案；实体诊断字段与检索快照会同时写入 raw 结果顶层。这样可以在模型开始输出后持续接收内容，减少一次性等待完整响应导致的超时。需要诊断同步接口时可追加 `--no-stream`。

`eval` Profile 默认开启检索快照，记录路由与实体解析、Dense/Sparse 原始 Top K、各来源 RRF 候选、knowledge type 过滤、rerank 前后顺序、最终入选原因以及各阶段耗时。`GLOBAL_RANKING`、`SOURCE_RETENTION`、`ENTITY_QUOTA` 分别表示全局排序、来源保留和多实体配额。候选快照包含正文，只能用于受控评测产物；非 eval Profile 默认关闭。失败阶段会在服务端 eval 日志中记录阶段、来源、实体范围、耗时和异常。

Runner 完成后执行：

```powershell
python .\score.py
```

运行 Holdout 时必须显式指定输入和独立输出目录，避免将结果误作开发回归：

```powershell
python .\run_eval.py --golden .\holdout.jsonl --limit 30 --output .\results\holdout\raw.jsonl
python .\score.py --golden .\holdout.jsonl --raw .\results\holdout\raw.jsonl --output .\results\holdout\score.json --report .\results\holdout\report.md --judgments .\results\holdout\judgments.jsonl
```

实体识别批次应写入独立目录，避免覆盖历史结果：

```powershell
python .\run_eval.py --id entity_001 --id entity_002 --id entity_003 --id entity_004 --id entity_005 --id entity_006 --id entity_007 --id entity_008 --id entity_009 --id entity_010 --id entity_011 --id entity_012 --id entity_013 --output .\results\t5-entity\raw.jsonl
python .\score.py --raw .\results\t5-entity\raw.jsonl --output .\results\t5-entity\score.json --report .\results\t5-entity\report.md --judgments .\results\t5-entity\judgments.jsonl
```

100 条扩展集应同样使用独立目录，例如 `results/t11-expanded`；rerank A/B 必须固定同一批 raw Case，并同时保存实体解析与初始候选诊断，避免候选差异污染比较。

`score.py` 默认读取 `golden.jsonl` 与 `results/raw.jsonl`；通过 `--golden` 可切换到 Holdout，但不会再次调用 RAG 服务。它会写入 `results/score.json`（机器可读）和 `results/report.md`（人工审阅），计算 Knowledge Recall@6、Precision@6、Required Evidence Type Hit、Selection Constraint Hit、Citation Index Validity、Safe Outcome Accuracy、Answer Availability、依赖故障、False Refusal 与 P50/P95 延迟。`Precision@6` 仅覆盖存在 `gold_evidence_refs` 的 Case：按 `sourceId + knowledgeType` 去重后，计算最终知识 Evidence Top-6 中命中的相关证据数除以固定分母 `6`，再做宏平均；如果某条 Case 实际返回少于 6 条知识证据，缺失槽位仍按未命中处理。`selection_expectations` 可对最终 Evidence 设置 `sourceId + knowledgeType` 最大重复数、正式指南主题覆盖数、必需/禁止主题、社区来源数量与 rerank 前逻辑组最小 Chunk 数。实体消歧故障优先根据结构化 `rejectionReason` 分类为 `ENTITY_RESOLUTION_UNAVAILABLE`；这类 Case 仍降低 Answer Availability，应答 Case 仍计入 False Refusal，并作为未命中计入固定数据集的 Recall、Precision 和 Evidence Type Hit 分母。这样不同运行的指标分母一致，可直接比较端到端结果。

接入两个 Judge 时，将 `judge.example.json` 复制为本机的 `judge.local.json`，填写 OpenAI 兼容 `chat/completions` 完整地址和模型名；该本地文件已被 Git 忽略，且不能保存 API Key。

```json
{
  "url": "https://your-llm-host/v1/chat/completions",
  "model": "your-judge-model",
  "api_key_env": "JUDGE_API_KEY"
}
```

再设置 API Key 环境变量并执行：

```powershell
$env:JUDGE_API_KEY = '...'
python .\score.py --judge
```

Judge 固定使用 `temperature=0`，提示词保存在 `prompts/correctness-judge.txt` 与 `prompts/faithfulness-judge.txt`。Faithfulness Judge 会区分 `EVIDENCE_FACT` 与 `SYSTEM_POLICY`，来源标注、正式指南优先级等系统策略不计入事实 Claim 的 Faithfulness 分母。每完成一个 Case，结果都会原子写入 `results/judgments.jsonl`；因此 Ctrl+C 后再次以相同输入执行时，已完成且状态为 `ok` 的 Case 会命中缓存，仅重评尚未完成、失败或输入发生变化的 Case。提示词变化会自动失效旧缓存，追加 `--refresh-judges` 可强制重评。命令行参数和同名环境变量优先于本地 JSON 配置。

金标来源见 `source-catalog.json`。本版本已只读查询本机 `green_community` 数据库，并绑定真实的 `plants.id`、`plant_care_guides` 主题字段和 `post.id`；数据库凭据不写入评测文件。运行评测时仍建议先导出并冻结一份独立索引快照，避免业务库内容变化导致结果漂移。

状态 Fixture 使用 AI 服务 `PlantState` 的 camelCase JSON 契约。评测时钟固定为 `fixtures/eval-clock.json` 的 `2026-08-17T10:00:00+08:00`；否则 `PlantStateAnalyzer` 当前基于 `Instant.now()` 的 30 分钟陈旧判断会使 `state_007` 和 `state_008` 漂移。

启动 AI 服务进行状态评测时，在 `SpringBoot后端/healing-planet-ai` 目录执行：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=eval"
```

`eval` Profile 会将 Plant State Client 替换为固定 Stub：仅 `userId=7` 时按 `fixtures` 目录中包含 `plantInstanceId` 和 `current` 指标的 JSON 加载状态；当前覆盖实例 `102` 至 `113`。`plant-state-unavailable.json` 不含 `current`，用于让实例 `999` 保持无状态。它同时将评测时钟固定为 `2026-08-17T10:00:00+08:00`。默认 Fixture 路径相对此目录为 `../../rag-eval/fixtures`，可通过 `RAG_EVAL_FIXTURE_DIRECTORY` 覆盖。
