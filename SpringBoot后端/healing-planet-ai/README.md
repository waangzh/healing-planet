# Healing Planet AI · 多模态证据驱动 RAG 服务

> 提供知识检索增强问答与植物病害辅助分析。检索优先、证据可溯源、回答带引用，不执行任何设备操作。

![JDK](https://img.shields.io/badge/JDK-17-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-6DB33F?logo=springboot&logoColor=white)
![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.8-6DB33F?logo=spring&logoColor=white)
![Lucene](https://img.shields.io/badge/Lucene-9.12.2-0D96F6)

独立的 JDK 17 / Spring Boot 3.5 / Spring AI 服务。在第一阶段知识检索之上，第二阶段按需读取 `smart_green_plant` 的实时与历史聚合状态；状态数据不进入 Qdrant，也不执行设备操作。

## 目录

- [简介](#简介)
- [架构概览](#架构概览)
- [核心特性](#核心特性)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [索引流程](#索引流程)
- [API 接口](#api-接口)
- [病害知识准备](#病害知识准备)
- [项目结构](#项目结构)
- [测试与验证](#测试与验证)
- [可观测性](#可观测性)
- [安全与部署注意事项](#安全与部署注意事项)

## 简介

本服务是 AI 层，负责把结构化的植物养护知识、社区经验和实时设备状态转化为可信、带引用的自然语言回答。核心设计原则：

- **检索优先（Retrieval-first）**：答案必须建立在可追溯的证据之上，而非模型空想。
- **证据分区（Evidence partitioning）**：可信知识与不可信社区内容分区注入，社区内容以 `UNTRUSTED_COMMUNITY_CONTENT` 注入，不能覆盖系统指令。
- **确定性计算（Deterministic logic）**：一致性判断、状态聚合等由代码完成，不让 LLM 自行计算。
- **只读编排（Read-only orchestration）**：不落盘图片、不写状态、不执行设备操作。

## 架构概览

```text
Query
  -> QueryAnalyzer + ExplicitConstraintParser
  -> PlantEntityResolver
  -> RetrievalPlanner（每个请求只生成一次计划）
  -> StateAwareEvidenceRetriever
       -> Retrieval Query Groups + Dense/BM25 + logical-evidence RRF
       -> QueryGroup-local Coverage + source-aware Adaptive Recall + 可选 reranker
       -> PlantStateClient / PlantStateAnalyzer
  -> SourceAwareRanker + EvidenceSelector
  -> AnswerabilityEvaluator
  -> GenerationPromptBuilder + PromptContextBuilder
  -> OpenAI 兼容 LLM（带 [E1] 引用）
```

显式来源禁止、权限、实体冲突和安全规则仍是 hard constraint；领域、主题和来源相关性预测只作为
planning / coverage hint。宽召回之后由 Evidence 的相关性、必需来源覆盖和状态完整性决定是否可回答。
必需来源覆盖要求每个 `REQUIRED` 来源分别存在相关 Evidence，不能由其它来源的高相关证据代偿。
Answerability 的 retrieval、rerank、对齐与强恢复阈值属于版本化 `RagRuntimeConfig`，默认值在
`application.example.yml` 的 `app.rag.answerability` 下配置；切换检索模式或 reranker 后应重新校准。
每个请求只捕获一次不可变 `RagRuntimeSnapshot`，检索、reranker、Answerability 和生成共享同一版本。Coverage
按 `RetrievalQueryGroup` 分别检查实体、主题和必需来源的组合，不能用其它植物或其它主题的候选代偿；当 reranker
实际返回分数时，宽松的 `RecallQualificationPolicy` 会在首轮重排后识别“已召回但仍偏弱”的必需来源，再做有界补召回。

## 核心特性

### 第一阶段 · 知识检索

- `plants + plant_care_guides` 按光照、浇水、温度、湿度、施肥和综合养护转换为语义文档。
- `plants` 同时生成一株一条的独立实体文档，写入 `plant_entities`，仅用于名称解析。
- `post + post_tag + tag` 转换为社区经验文档，只摄取 `status = 1`（兼容历史 `status is null`）的帖子。
- BGE-M3 dense embedding + Qdrant 独立 collection（植物实体 / 植物知识 / 社区 / 病害）。
- Lucene 中文字符 n-gram BM25 稀疏检索。
- 每条知识先以一个逻辑证据及其一个或多个 fragment 建模；RRF 按逻辑证据在 dense / BM25
  路径中的最佳 fragment 名次融合，不因同一长文的多个 chunk 重复加分。
- 多个已解析植物会形成确定性的 Retrieval Query Groups；普通查询仍保留单个原始查询组，不使用
  LLM query decomposition。CoverageInspector 在每个组内检查 `source × entity × topic`，存在结构缺口时
  才按缺失来源将 recall Top-K 有界扩展至配置上限。结构覆盖完成后，只有启用并实际取得 reranker 分数时，才会
  对弱的必需来源执行一次或多次有界 corrective recall；它不复用 Answerability 阈值。
- Reranker 先按 logical evidence 限制候选数，再以“每条逻辑证据最多 N 个 fragment + 总 fragment 上限”
  组成输入；同一长帖不能挤占全部 rerank slots。最终 selector 先保留每个必需 Query Group 的证据，再按全局排名补齐。
- Prompt 为每条逻辑证据保留来源、植物和章节等父上下文，并提供主相关片段与有限补充片段；片段数由独立的
  context-assembly 预算控制，避免长文在生成上下文中重新膨胀。
- 社区帖子索引会基于标签、标题和明确植物提及写入 `resolvedPlantIds` 与置信度，并在多植物 Query Group 的
  coverage 中作为软实体归属信号。它只提升/归因候选，不会对社区检索施加硬过滤。
- 可选 BGE reranker、来源可信度 / 帖子质量 / 时效 / 植物匹配排序。
- 已知植物优先使用 `canonicalPlantId` 过滤检索空间，并在融合前校验证据实体；明确点名但无法映射到知识库的植物不会退化为其它植物的同主题结果。
- 同步问答、SSE 流式问答、语义搜索及 Evidence 引用。
- 分页增量索引、植物索引、社区索引、单帖子更新与删除；全量扫描仅用于修复与补数。

### 第二阶段 · 个体化状态感知

- `QueryAnalyzer` 只产生可复用的 soft hint；普通的“温度/湿度/现在/它”不会单独升级为状态检索。
- “多少”只有与植物实例或传感器上下文组合时才视为 telemetry 请求；“现在适宜温度是多少”仍走知识检索。
- `CURRENT / HISTORY / FRESHNESS / DECISION_SUPPORT` 是可组合需求；纯历史问题不会被强制追加实时状态。
- 调用方显式传入的 `COMMUNITY_SEARCH` 作为兼容信号，将社区来源设为 `REQUIRED`，不会被静默忽略。
- `GET /internal/plant-state/{plantInstanceId}` 聚合最新读数、近 24 小时 / 7 天统计与趋势、设备阈值。
- AI 服务使用有限超时的内部 HTTP 客户端按需拉取，不把瞬时状态写入向量库。
- `PlantStateAnalyzer` 将原始指标确定性转换为 `LIVE_STATE` 与 `SENSOR_HISTORY` Evidence。
- 个体化问题同时路由到植物知识与状态数据，植物名称会增强知识检索。
- 状态证据与可信知识、非可信社区内容分区注入，并携带采集时间与陈旧标记。
- 状态检索需要 `userId + plantInstanceId`，IoT 服务再次校验实例归属；状态不可用或被明确禁止时，不使用一般指南冒充即时状态判断。

### 第三阶段 · 多模态病害辅助分析

- `plant_disease_knowledge` 保存症状、视觉特征、诱因、处理、预防和可追溯来源；通过独立 Qdrant collection + Lucene 完成混合检索。
- 复用现有 `/classify` 病害模型，检测服务只输出视觉候选和置信度，不直接决定处理方案。
- 诊断链路固定融合 `VISUAL_OBSERVATION + DISEASE_KNOWLEDGE + LIVE_STATE/SENSOR_HISTORY + SENSOR_CONSISTENCY`。
- `SensorConsistencyAnalyzer` 使用病害诱因和近 7 日湿度 / 温度做确定性支持、冲突或未知判断，不让 LLM 自行计算一致性。
- 输出仍使用统一 Evidence 和 `[E1]` Citation；没有检索到可信病害知识时不产生处理建议。
- 图片不在 AI 服务落盘，只在请求期间转发给检测服务；大小、类型和超时可配置。

## 快速开始

### 1. 环境要求

| 依赖 | 版本 / 说明 |
| --- | --- |
| JDK | 17 |
| Maven | 3.9+ |
| MySQL | Healing Planet 的 `green_community` 数据库 |
| Qdrant | 向量数据库（`docker compose up -d qdrant`） |
| 聊天模型 | OpenAI 兼容的 chat API |
| Embedding | OpenAI 兼容的 BGE-M3 `/v1/embeddings` API（vLLM / Infinity） |
| Reranker（可选） | BGE reranker HTTP 服务，默认关闭 |
| 病害分类服务 | 现有 `POST http://localhost:5000/classify` |

> Java 服务只做 RAG 编排，不在 JVM 内加载模型。

### 2. 启动 Qdrant

```bash
docker compose up -d qdrant
```

### 3. 配置

参考 `.env.example`，通过系统环境变量、部署系统或 IDE 注入配置。Spring Boot 不会自动加载模块目录中的 `.env` 文件，**不要提交真实密钥。**

```bash
mvn spring-boot:run
```

第二阶段还需在两个服务配置同一个随机内部密钥：

```text
healing-planet-ai:  PLANT_STATE_API_KEY
smart_green_plant:  PLANT_INTERNAL_API_KEY
```

首次启动会在 embedding 模型返回向量维度后创建 Qdrant collection。模型维度变更时应使用新 collection 名称或手动迁移，不能直接复用原 collection。模型、归一化策略或维度变更后，还必须同步修改 `RAG_EMBEDDING_MODEL_VERSION`，使已有 chunk 在下一次补偿扫描中重新向量化。

## 配置说明

完整配置见 [`application.example.yml`](src/main/resources/application.example.yml) 与 [`.env.example`](.env.example)。常用项如下：

| 环境变量 | 说明 | 默认值 |
| --- | --- | --- |
| `AI_SERVER_PORT` | 服务端口 | `8010` |
| `AI_HTTP_READ_TIMEOUT` / `AI_HTTP_REACTIVE_READ_TIMEOUT` / `AI_RETRY_MAX_ATTEMPTS` | 聊天模型读超时 / 响应式读超时 / 最大尝试次数 | `60s` / `60s` / `1` |
| `COMMUNITY_DB_URL` | 社区库 JDBC 地址 | `jdbc:mysql://localhost:3306/green_community` |
| `LLM_API_KEY` / `LLM_BASE_URL` / `LLM_MODEL` | 聊天模型 | `change-me` / `https://api.siliconflow.cn` / `Qwen/Qwen3.5-397B-A17B` |
| `EMBEDDING_BASE_URL` / `EMBEDDING_MODEL` | Embedding 模型 | `https://api.siliconflow.cn` / `BAAI/bge-m3` |
| `RAG_INGESTION_BATCH_SIZE` | 每次读取与写入的外层 chunk 批次，范围 50–200 | `100` |
| `RAG_EMBEDDING_MODEL_VERSION` | 向量化版本；模型或 embedding 策略变动时必须修改 | `EMBEDDING_MODEL` |
| `RAG_EMBEDDING_BATCH_MAX_TOKENS` / `RAG_EMBEDDING_BATCH_RESERVE_PERCENTAGE` | Spring AI 内层 token 批处理上限与预留比例 | `8000` / `0.1` |
| `QDRANT_HOST` / `QDRANT_GRPC_PORT` | Qdrant 连接 | `localhost` / `6334` |
| `QDRANT_PLANT_COLLECTION` | 植物知识 collection | `plant_knowledge` |
| `QDRANT_PLANT_ENTITY_COLLECTION` | 植物实体 collection | `plant_entities` |
| `QDRANT_COMMUNITY_COLLECTION` | 社区知识 collection | `community_knowledge` |
| `QDRANT_DISEASE_COLLECTION` | 病害知识 collection | `disease_knowledge` |
| `RAG_INTERNAL_API_KEY` | 内部索引接口密钥 | 空（生产必填） |
| `RAG_DENSE_TOP_K` / `RAG_SPARSE_TOP_K` / `RAG_FINAL_TOP_K` | 检索 Top-K | `30` / `30` / `6` |
| `RAG_ADAPTIVE_RECALL_*` | 覆盖缺口时的有界、按来源扩召回配置 | 最大 `120`，最少逻辑候选 `2` |
| `RAG_RECALL_QUALIFICATION_*` | 首轮 rerank 后判定必需来源是否仍偏弱的宽松阈值；没有 rerank 分数时跳过 | 启用，`0.20` |
| `RERANKER_CANDIDATE_TOP_K` / `RERANKER_MAX_FRAGMENTS_PER_LOGICAL_EVIDENCE` / `RERANKER_MAX_FRAGMENTS_TOTAL` | rerank 的 logical evidence、单证据 fragment 与总 fragment 两级预算 | `20` / `2` / `40` |
| `RAG_CONTEXT_ASSEMBLY_MAX_FRAGMENTS_PER_LOGICAL_EVIDENCE` | 每条最终逻辑证据写入 Prompt 的主/补充 fragment 上限 | `2` |
| `RAG_SIMILARITY_THRESHOLD` | 相似度阈值 | `0.25` |
| `RAG_RETRIEVAL_MODE` | 检索模式：`BM25_ONLY` / `DENSE_ONLY` / `HYBRID_RRF` | `HYBRID_RRF` |
| `RAG_ANSWERABILITY_MIN_RETRIEVAL_RELEVANCE` / `RAG_ANSWERABILITY_MIN_RERANK_RELEVANCE` | Answerability 的 retrieval / rerank 最低相关性 | `0.45` / `0.40` |
| `RAG_ANSWERABILITY_MIN_ALIGNED_SEMANTIC_RELEVANCE` / `RAG_ANSWERABILITY_MIN_ALIGNED_FINAL_RELEVANCE` | 实体或主题对齐证据的 semantic / final 最低相关性 | `0.30` / `0.60` |
| `RAG_ANSWERABILITY_STRONG_RECOVERY_RELEVANCE` | 低领域置信度查询允许由强证据恢复的阈值 | `0.60` |
| `RAG_ENTITY_LLM_MODEL` / `RAG_ENTITY_LLM_TEMPERATURE` / `RAG_ENTITY_LLM_MAX_TOKENS` | 实体消歧专用模型 / 温度 / 最大输出 token | `Qwen/Qwen3.5-4B` / `0.0` / `160` |
| `RAG_ENTITY_LLM_ENABLE_THINKING` | 是否启用实体消歧模型的推理模式 | `false` |
| `RAG_ENTITY_LLM_CONNECT_TIMEOUT_MILLIS` / `RAG_ENTITY_LLM_READ_TIMEOUT_MILLIS` | 实体消歧独立连接 / 读取超时；固定单次尝试 | `1000` / `8000` |
| `RAG_ENTITY_CIRCUIT_FAILURE_THRESHOLD` / `RAG_ENTITY_CIRCUIT_OPEN_MILLIS` | 实体消歧连续失败阈值 / 熔断时长；冷却结束后重新累计失败次数 | `3` / `5000` |
| `RERANKER_ENABLED` / `RERANKER_MODEL` | 重排序开关与模型 | 代码与 `.env.example` 默认 `false`；`application.example.yml` 的环境变量回退值为 `true` / `BAAI/bge-reranker-v2-m3` |
| `PLANT_STATE_BASE_URL` / `PLANT_STATE_API_KEY` | IoT 状态服务 | `http://localhost:8070` / 空 |
| `DISEASE_DETECTOR_BASE_URL` / `DISEASE_DETECTOR_PATH` | 病害检测服务 | `http://localhost:5000` / `/classify` |
| `RAG_ATTACHMENT_TTL_SECONDS` / `RAG_ATTACHMENT_MAX_ENTRIES` | 临时图片缓存 | `900` / `32` |

## 索引流程

应用启动不会扫描业务数据。`/internal/index/full` 是补数/修复扫描：以主键 keyset 分页读取，每批最多 100 个 fragment；只有内容、索引版本或 `embeddingModelVersion` 变化的 fragment 才会调用 embedding 并写入 Qdrant。扫描同时清理已从源库删除的文档。

首次引入该机制或需要补数时，先由数据库发布流程执行 [`V4__rag_embedding_state.sql`](src/main/resources/db/migration/V4__rag_embedding_state.sql)，再显式触发扫描：

```bash
curl -X POST http://localhost:8010/internal/index/full \
  -H "X-Internal-Api-Key: replace-with-a-random-secret"
```

社区实体归属元数据随 `logical-evidence-v2` 写入。部署包含该版本的服务后，应在低峰期显式执行一次
`POST /internal/index/community`，让已有帖子获得 `resolvedPlantIds`；该操作不涉及植物正式知识或设备状态。

帖子发布或修改后调用 `POST /internal/index/post/{postId}`；帖子删除后调用 `DELETE /internal/index/post/{postId}`。两者均可安全地被至少一次投递重复调用：内容及模型版本未变化时不会重新向量化。

生产环境不应靠定时全量扫描同步帖子。社区服务已在创建、更新、删除帖子所在的数据库事务内写入 outbox 事件；部署时需先执行 [`V1__post_index_outbox.sql`](../healing-planet-sys/service/src/main/resources/db/migration/V1__post_index_outbox.sql)。独立发布器在 RabbitMQ publisher confirm 后更新 outbox 状态，消费者成功调用下面的内部接口后才标记 `DELIVERED`；失败会回写同一条 outbox 记录并延迟重试。事件载荷遵循以下契约：

```json
{
  "eventId": "全局唯一 ID",
  "type": "POST_UPSERT 或 POST_DELETE",
  "postId": "post.id",
  "occurredAt": "ISO-8601 时间"
}
```

RabbitMQ 使用持久化 exchange/queue，并为无效消息保留死信队列。确认/重试以 outbox 记录为准，而不是直接在帖子事务中发消息。这样消息重复、延迟或消费者重试不会导致重复向量化；全量扫描仅保留为 outbox 故障后的补偿工具。

## API 接口

### 问答

`POST /api/rag/chat`

```json
{
  "query": "我的绿萝今天需要浇水吗？",
  "userId": 7,
  "plantInstanceId": 102,
  "canonicalPlantId": "可选的 plants.id",
  "intent": "可选；PERSONAL_CARE / GENERAL_CARE / COMMUNITY_SEARCH / DISEASE_DIAGNOSIS"
}
```

响应的 `answer` 使用 `[E1]` 引用，`evidence` 返回对应来源、内容、各阶段分数和元数据；知识证据的元数据含 `logicalEvidenceId`、`fragmentId`、`fragmentRole`、`fragmentIndex`、`fragmentCount`、`fragmentSection` 与 `contextFragments`。其中 `contextFragments` 按“主相关片段 + 补充片段”顺序保留实际写入 Prompt 的有限子集，`id` 仍是具体代表 fragment 的引用锚点。`entityResolution` 返回实体解析诊断，包括 `resolutionKind`、`resolutionMethod`、`canonicalPlantId`、`canonicalPlantIds`、`top1Score`、`top2Score`、`scoreMargin`、`candidateCount` 和 `rejectionReason`。

### 流式问答

`POST /api/rag/chat/stream`，事件顺序为：

- `evidence`：本轮选中的全部证据；
- `entity_resolution`：实体解析诊断；
- `token`：模型增量文本；
- `done`：完成标记。

### 语义搜索

`GET /api/search?q=适合宿舍养又耐阴的植物&canonicalPlantId=可选`

### 检索观测

Actuator 暴露 `health`、`info`、`metrics` 和 `prometheus`。Spring AI 自带的 Observation 用于查看 Embedding 与 VectorStore 调用；项目另外记录以下指标：

```text
healing.planet.rag.retrieval.stage
healing.planet.rag.retrieval.candidates
```

阶段耗时的低基数标签包括 `stage`、`source` 和 `status`。当前阶段包括：

```text
entity_resolve
entity_disambiguation
embedding
dense_search
sparse_search
rrf_fusion
rerank
final_rank
knowledge_total
state_search
plant_state_query
answer_generation
retrieve_total
```

`embedding` 覆盖请求期的查询向量化与随后向量库查询；`plant_state_query` 只覆盖 IoT HTTP 查询，外层 `state_search` 还包含状态分析。同步与流式回答都会在流终止后记录 `answer_generation`。阶段 Timer 发布 P50/P95 与可聚合直方图，可按 `stage`、`source`、`status` 定位 P95。

例如：

```bash
curl "http://localhost:8010/actuator/metrics/healing.planet.rag.retrieval.stage"
curl "http://localhost:8010/actuator/metrics/healing.planet.rag.retrieval.candidates"
curl "http://localhost:8010/actuator/prometheus"
```

指标不会使用原始问题、植物名称或文档 ID 作为 tag，避免泄露用户输入及造成高基数时间序列。

### 索引管理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/internal/index/full` | 全量索引 |
| `POST` | `/internal/index/plants` | 植物实体与植物知识 |
| `POST` | `/internal/index/community` | 仅社区内容 |
| `POST` | `/internal/index/post/{postId}` | 单帖子更新 |
| `DELETE` | `/internal/index/post/{postId}` | 单帖子删除 |
| `POST` | `/internal/index/diseases` | 仅病害知识 |
| `POST` | `/internal/index/disease/{diseaseId}` | 单病害更新 |

> 所有 `/internal/**` 接口需携带 `X-Internal-Api-Key` 请求头。`rag_embedding_state` 是 AI 服务唯一需要写入的表，部署账号应只被授予该表的写权限，其他业务表保持只读权限。

### 多模态病害辅助分析

`POST /api/rag/diagnose`，请求类型为 `multipart/form-data`：

```bash
curl -X POST http://localhost:8010/api/rag/diagnose \
  -F "image=@leaf.jpg" \
  -F "userId=7" \
  -F "plantInstanceId=102" \
  -F "query=这片叶子可能是什么问题？"
```

返回 `answer + evidence[]`。该接口是辅助分析，不将视觉候选表述为确诊，也不执行设备操作。

该接口也用于通用图片问答。`requestedRoute` 可取 `AUTO`、`DISEASE_DIAGNOSIS`、`OCR`、`GENERAL_VISION`；普通问答使用 `AUTO`，手动选择叶片诊断时使用 `DISEASE_DIAGNOSIS`。`query`、`userId` 和 `plantInstanceId` 均可省略：没有问题文字时使用默认图片分析提示，没有植物实例时跳过传感器证据并明确降级。

首次上传会返回临时 `attachmentId`，在有效期内可只提交 `attachmentId + query` 继续追问同一图片。图片与结构化视觉观察仅保存在当前服务实例内存，默认 15 分钟、最多 32 个条目；服务重启或多实例切换后需重新上传。聊天模型必须支持 OpenAI 兼容的图片消息输入，Spring AI 会通过用户消息的 media 字段发送原图。

## 病害知识准备

建表脚本位于 `src/main/resources/db/migration/V3__plant_disease_knowledge.sql`。本项目未引入 Flyway，需由现有数据库发布流程执行。脚本故意不预置未审核的病害处理知识；导入数据时 `source` 必须是可追溯资料，并用 `source_level=TRUSTED/REVIEWED` 标记审核级别。导入后调用病害索引接口。

## 项目结构

```text
src/main/java/com/healingplanet/ai/
├── HealingPlanetAiApplication.java   # 启动入口
├── api/                              # REST 控制器与异常处理
│   ├── RagController.java            # 问答 / 流式 / 搜索
│   ├── MultimodalDiagnosisController.java
│   ├── IndexController.java          # 内部索引管理
│   └── ...
├── config/                           # 配置（AiConfiguration、RagProperties）
├── domain/                           # 领域模型（Evidence、RagResponse 等）
├── query/                            # Query Analysis、显式约束、可组合状态需求
├── ingestion/                        # 知识文档摄取与转换
├── retrieval/                        # 实体解析、检索计划、混合检索、排序与 Evidence 选择
├── evaluation/                       # 检索后 Answerability / Safe Outcome
└── service/                          # RAG 编排、多模态诊断、提示构建
```

## 测试与验证

```bash
mvn test
```

单元测试覆盖 Query Analysis、显式约束、实体解析、RRF、topic coverage、状态分析、Evidence relevance、Answerability 和提示注入隔离。外部 MySQL、Qdrant、IoT、embedding、reranker 与 LLM 的连通性由部署环境健康检查负责。

## 可观测性

项目使用 Prometheus 拉取 `/actuator/prometheus`，Grafana 自动加载 RAG 检索仪表盘。服务运行在宿主机时，启动观测组件：

```bash
docker compose up -d prometheus grafana
```

Prometheus 位于 `http://localhost:9090`，Grafana 位于 `http://localhost:3000`。首次查看仪表盘前必须先请求一次 RAG 接口，指标会在首次记录时创建。Grafana 默认账号来自 `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`；`.env.example` 仅提供本地默认值，生产环境必须由部署系统注入强密码。

仪表盘包含按 `stage`、`source` 筛选的吞吐量、P50、P95、错误率、候选数量和延迟热力图。P50/P95 使用 Prometheus histogram 聚合，例如：

```promql
histogram_quantile(
  0.95,
  sum by (le, stage) (
    rate(healing_planet_rag_retrieval_stage_seconds_bucket{status="ok"}[5m])
  )
)
```

当前 Compose 假设 AI 服务运行在 Docker 宿主机，因此 Prometheus 的目标为 `host.docker.internal:8010`。若以后将 AI 服务加入同一 Compose 网络，应改为对应服务名和端口。生产环境不要将 Actuator 管理端口公开到公网，应使用独立管理端口并限制为 Prometheus 所在私网访问。

## 安全与部署注意事项

> [!WARNING]
> 生产环境必须设置非空 `RAG_INTERNAL_API_KEY`，并在网关层限制 `/internal/**` 只能由内部服务访问。

> [!NOTE]
> 生产环境应由已认证的业务后端或网关填写可信 `userId`，不要直接信任匿名客户端自报身份。

> [!NOTE]
> 不要提交真实密钥（`.env`、`application.yml` 中的密钥）。仓库内只保留 `.env.example` 与 `application.example.yml` 模板。
