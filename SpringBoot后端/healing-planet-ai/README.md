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
- [安全与部署注意事项](#安全与部署注意事项)

## 简介

本服务是 AI 层，负责把结构化的植物养护知识、社区经验和实时设备状态转化为可信、带引用的自然语言回答。核心设计原则：

- **检索优先（Retrieval-first）**：答案必须建立在可追溯的证据之上，而非模型空想。
- **证据分区（Evidence partitioning）**：可信知识与不可信社区内容分区注入，社区内容以 `UNTRUSTED_COMMUNITY_CONTENT` 注入，不能覆盖系统指令。
- **确定性计算（Deterministic logic）**：一致性判断、状态聚合等由代码完成，不让 LLM 自行计算。
- **只读编排（Read-only orchestration）**：不落盘图片、不写状态、不执行设备操作。

## 架构概览

```text
                         ┌─────────────────────────────────────────────┐
  POST /api/rag/chat ───▶│ QueryRouter（意图路由）                       │
  POST /api/rag/diagnose─▶  GENERAL_CARE / PERSONAL_CARE /               │
                         │  COMMUNITY_SEARCH / DISEASE_DIAGNOSIS         │
                         └───────────────┬─────────────────────────────┘
                                         │
              ┌──────────────────────────┼──────────────────────────┐
              ▼                          ▼                          ▼
   ┌────────────────────┐   ┌────────────────────────┐   ┌─────────────────────┐
   │ 稠密检索            │   │ 稀疏检索                │   │ 状态/视觉证据        │
   │ BGE-M3 + Qdrant    │   │ Lucene BM25 中文 n-gram  │   │ PlantStateClient    │
   └────────────────────┘   └────────────────────────┘   │ DiseaseDetectorClient│
              │                          │              └─────────────────────┘
              └──────────┬───────────────┘                          │
                         ▼                                          ▼
                 ┌──────────────┐                    ┌─────────────────────────┐
                 │ RRF 融合      │                    │ PlantStateAnalyzer      │
                 │ + BGE reranker│                    │ SensorConsistencyAnalyzer│
                 └──────┬───────┘                    └────────────┬────────────┘
                        └───────────────┬─────────────────────────┘
                                        ▼
                        ┌───────────────────────────────┐
                        │ PromptContextBuilder（证据分区）│
                        └───────────────┬───────────────┘
                                        ▼
                        ┌───────────────────────────────┐
                        │ OpenAI 兼容 LLM（带 [E1] 引用）│
                        └───────────────────────────────┘
```

## 核心特性

### 第一阶段 · 知识检索

- `plants + plant_care_guides` 按光照、浇水、温度、湿度、施肥和综合养护转换为语义文档。
- `plants` 同时生成一株一条的独立实体文档，写入 `plant_entities`，仅用于名称解析。
- `post + post_tag + tag` 转换为社区经验文档，只摄取 `status = 1`（兼容历史 `status is null`）的帖子。
- BGE-M3 dense embedding + Qdrant 独立 collection（植物实体 / 植物知识 / 社区 / 病害）。
- Lucene 中文字符 n-gram BM25 稀疏检索。
- RRF 融合、可选 BGE reranker、来源可信度 / 帖子质量 / 时效 / 植物匹配排序。
- 已知植物优先使用 `canonicalPlantId` 过滤检索空间，并在融合前校验证据实体；明确点名但无法映射到知识库的植物不会退化为其它植物的同主题结果。
- 同步问答、SSE 流式问答、语义搜索及 Evidence 引用。
- 全量索引、植物索引、社区索引、单帖子更新与删除。

### 第二阶段 · 个体化状态感知

- 规则型 Query Router 区分普通养护、社区检索和个体化状态问题；调用方也可显式传 `intent`。
- `GET /internal/plant-state/{plantInstanceId}` 聚合最新读数、近 24 小时 / 7 天统计与趋势、设备阈值。
- AI 服务使用有限超时的内部 HTTP 客户端按需拉取，不把瞬时状态写入向量库。
- `PlantStateAnalyzer` 将原始指标确定性转换为 `LIVE_STATE` 与 `SENSOR_HISTORY` Evidence。
- 个体化问题同时路由到植物知识与状态数据，植物名称会增强知识检索。
- 状态证据与可信知识、非可信社区内容分区注入，并携带采集时间与陈旧标记。
- `userId + plantInstanceId` 必填，IoT 服务再次校验实例归属；状态不可用时只做知识降级并明确证据不足。

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

复制 `.env.example` 并通过系统环境变量或 IDE 注入配置。**不要提交真实密钥。**

```bash
cp .env.example .env   # 按需修改
mvn spring-boot:run
```

第二阶段还需在两个服务配置同一个随机内部密钥：

```text
healing-planet-ai:  PLANT_STATE_API_KEY
smart_green_plant:  PLANT_INTERNAL_API_KEY
```

首次启动会在 embedding 模型返回向量维度后创建 Qdrant collection。模型维度变更时应使用新 collection 名称或手动迁移，不能直接复用原 collection。

## 配置说明

完整配置见 [`application.example.yml`](src/main/resources/application.example.yml) 与 [`.env.example`](.env.example)。常用项如下：

| 环境变量 | 说明 | 默认值 |
| --- | --- | --- |
| `AI_SERVER_PORT` | 服务端口 | `8010` |
| `AI_HTTP_READ_TIMEOUT` / `AI_HTTP_REACTIVE_READ_TIMEOUT` / `AI_RETRY_MAX_ATTEMPTS` | 聊天模型读超时 / 响应式读超时 / 最大尝试次数 | `60s` / `60s` / `1` |
| `COMMUNITY_DB_URL` | 社区库 JDBC 地址 | `jdbc:mysql://localhost:3306/green_community` |
| `LLM_API_KEY` / `LLM_BASE_URL` / `LLM_MODEL` | 聊天模型 | `change-me` / `https://api.siliconflow.cn` / `Qwen/Qwen3.5-397B-A17B` |
| `EMBEDDING_BASE_URL` / `EMBEDDING_MODEL` | Embedding 模型 | `https://api.siliconflow.cn` / `BAAI/bge-m3` |
| `QDRANT_HOST` / `QDRANT_GRPC_PORT` | Qdrant 连接 | `localhost` / `6334` |
| `QDRANT_PLANT_COLLECTION` | 植物知识 collection | `plant_knowledge` |
| `QDRANT_PLANT_ENTITY_COLLECTION` | 植物实体 collection | `plant_entities` |
| `QDRANT_COMMUNITY_COLLECTION` | 社区知识 collection | `community_knowledge` |
| `QDRANT_DISEASE_COLLECTION` | 病害知识 collection | `disease_knowledge` |
| `RAG_INTERNAL_API_KEY` | 内部索引接口密钥 | 空（生产必填） |
| `RAG_DENSE_TOP_K` / `RAG_SPARSE_TOP_K` / `RAG_FINAL_TOP_K` | 检索 Top-K | `30` / `30` / `6` |
| `RAG_SIMILARITY_THRESHOLD` | 相似度阈值 | `0.25` |
| `RERANKER_ENABLED` / `RERANKER_MODEL` | 重排序开关与模型 | `false` / `BAAI/bge-reranker-v2-m3` |
| `PLANT_STATE_BASE_URL` / `PLANT_STATE_API_KEY` | IoT 状态服务 | `http://localhost:8070` / 空 |
| `DISEASE_DETECTOR_BASE_URL` / `DISEASE_DETECTOR_PATH` | 病害检测服务 | `http://localhost:5000` / `/classify` |
| `RAG_ATTACHMENT_TTL_SECONDS` / `RAG_ATTACHMENT_MAX_ENTRIES` | 临时图片缓存 | `900` / `32` |

## 索引流程

应用启动不会扫描并重新向量化业务数据。服务就绪后显式触发一次全量索引：

```bash
curl -X POST http://localhost:8010/internal/index/full \
  -H "X-Internal-Api-Key: replace-with-a-random-secret"
```

帖子发布或修改后调用 `POST /internal/index/post/{postId}`；帖子删除后调用 `DELETE /internal/index/post/{postId}`。文档 ID 是确定性的，重复调用会更新同一条向量和稀疏索引。

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

响应的 `answer` 使用 `[E1]` 引用，`evidence` 返回对应来源、内容、各阶段分数和元数据；`entityResolution` 返回实体解析诊断，包括 `resolutionKind`、`resolutionMethod`、`canonicalPlantId`、`canonicalPlantIds`、`top1Score`、`top2Score`、`scoreMargin`、`candidateCount` 和 `rejectionReason`。

### 流式问答

`POST /api/rag/chat/stream`，事件顺序为：

- `evidence`：本轮选中的全部证据；
- `entity_resolution`：实体解析诊断；
- `token`：模型增量文本；
- `done`：完成标记。

### 语义搜索

`GET /api/search?q=适合宿舍养又耐阴的植物&canonicalPlantId=可选`

### 检索观测

Actuator 暴露 `health`、`info` 和 `metrics`。Spring AI 自带的 Observation 用于查看 Embedding 与 VectorStore 调用；项目另外记录以下指标：

```text
healing.planet.rag.retrieval.stage
healing.planet.rag.retrieval.candidates
```

阶段耗时的低基数标签包括 `stage`、`source` 和 `status`。当前阶段包括：

```text
entity_resolve
dense_search
sparse_search
rrf_fusion
rerank
final_rank
knowledge_total
state_search
retrieve_total
```

例如：

```bash
curl "http://localhost:8010/actuator/metrics/healing.planet.rag.retrieval.stage"
curl "http://localhost:8010/actuator/metrics/healing.planet.rag.retrieval.candidates"
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

> 所有 `/internal/**` 接口需携带 `X-Internal-Api-Key` 请求头。

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
├── ingestion/                        # 知识文档摄取与转换
├── retrieval/                        # 混合检索、重排序、状态/一致性分析
└── service/                          # RAG 编排、多模态诊断、提示构建
```

## 测试与验证

```bash
mvn test
```

单元测试只覆盖关键纯逻辑：语义文档转换、RRF 融合、路由、状态分析和提示注入隔离。外部 MySQL、Qdrant、IoT、embedding、reranker 与 LLM 的连通性由部署环境健康检查负责。

## 安全与部署注意事项

> [!WARNING]
> 生产环境必须设置非空 `RAG_INTERNAL_API_KEY`，并在网关层限制 `/internal/**` 只能由内部服务访问。

> [!NOTE]
> 生产环境应由已认证的业务后端或网关填写可信 `userId`，不要直接信任匿名客户端自报身份。

> [!NOTE]
> 不要提交真实密钥（`.env`、`application.yml` 中的密钥）。仓库内只保留 `.env.example` 与 `application.example.yml` 模板。
