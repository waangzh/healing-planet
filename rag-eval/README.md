# Healing Planet RAG 评测

本目录包含两套评测集：

- `golden.jsonl`：114 条开发回归集，用于日常评测。
- `holdout.jsonl`：30 条冻结盲测集，只用于里程碑或发布前评测，不应根据单条结果调参。

不要为单条运行结果调参或改写冻结数据。语义修订必须同时更新 manifest、校验规则，并记录修订原因。运行前先校验数据及来源快照：

```powershell
python .\test\validate_datasets.py
python -m unittest discover -s .\test -p "test_*.py"
```

## 运行评测

先启动启用评测跟踪和固定状态 Fixture 的 AI 服务：

```powershell
cd .\SpringBoot后端\healing-planet-ai
mvn spring-boot:run "-Dspring-boot.run.profiles=eval"
```

然后在 `rag-eval` 目录运行 Golden：

```powershell
python .\run_eval.py
python .\score.py
```

结果默认写入：

- `results/raw.jsonl`：服务原始响应与检索 Trace。
- `results/score.json`：机器可读指标。
- `results/report.md`：人工审阅报告。
- `results/judgments.jsonl`：Judge 缓存。

运行冻结 Holdout 时必须使用独立输出目录：

```powershell
python .\run_eval.py --golden .\holdout.jsonl --limit 30 --output .\results\holdout\raw.jsonl
python .\score.py --golden .\holdout.jsonl --raw .\results\holdout\raw.jsonl --output .\results\holdout\score.json --report .\results\holdout\report.md --judgments .\results\holdout\judgments.jsonl
```

如需调用同步接口进行诊断，在 `run_eval.py` 后追加 `--no-stream`。

## 启用 LLM Judge

将 `judge.example.json` 复制为已被 Git 忽略的 `judge.local.json`，填写 OpenAI-compatible `chat/completions` 地址和模型名：

```json
{
  "url": "https://your-llm-host/v1/chat/completions",
  "model": "your-judge-model",
  "api_key_env": "JUDGE_API_KEY"
}
```

设置密钥并评分：

```powershell
$env:JUDGE_API_KEY = '...'
python .\score.py --judge
```

Judge 结果支持断点续评；提示词或输入变化会自动失效对应缓存。需要全部重评时追加 `--refresh-judges`。

## 当前指标

- 核心质量：Retrieval Recall@10、Context Precision、Context Recall、Faithfulness、Answer Correctness。
- 安全与可靠性：Safe Outcome Accuracy、Answer Availability、P95 端到端延迟、P95 检索延迟。
- 回归诊断：Route Accuracy、必需证据类型命中、选择约束命中、Selected Evidence ID Recall@6、引用索引有效性及错误分类。

Retrieval Recall@10 使用 `preSelectionRanked`，因此旧 raw 文件不能作为新版检索召回率和检索延迟基线；升级服务后需重新执行 `run_eval.py`。本实现借鉴 RAGAS、RAGChecker 和 ALCE 的指标定义，但不引入它们的运行时依赖。

`eval` Profile 默认从 `../../rag-eval/fixtures` 加载状态 Fixture，并将评测时钟固定为 `2026-08-17T10:00:00+08:00`。如需修改 Fixture 路径，设置 `RAG_EVAL_FIXTURE_DIRECTORY`。
