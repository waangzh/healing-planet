# Healing Planet AI：Multimodal Evidence-Aware RAG

独立的 JDK 17 / Spring Boot 3.5 / Spring AI 服务。在第一阶段知识检索之上，第二阶段按需读取 `smart_green_plant` 的实时与历史聚合状态；状态数据不进入 Qdrant，也不执行设备操作。

## 第一阶段能力

- `plants + plant_care_guides` 按光照、浇水、温度、湿度、施肥和综合养护转换为语义文档；
- `post + post_tag + tag` 转换为社区经验文档，只摄取 `status = 1`（兼容历史 `status is null`）的帖子；
- BGE-M3 dense embedding + Qdrant 两个 collection；
- Lucene 中文字符 n-gram BM25 sparse retrieval；
- RRF 融合、可选 BGE reranker、来源可信度/帖子质量/时效/植物匹配排序；
- 同步问答、SSE 流式问答、语义搜索及 Evidence 引用；
- 全量索引、植物索引、社区索引、单帖子更新与删除；
- 社区内容以 `UNTRUSTED_COMMUNITY_CONTENT` 注入，不能覆盖系统指令。

## 第二阶段能力

- 规则型 Query Router 区分普通养护、社区检索和个体化状态问题；调用方也可显式传 `intent`；
- `GET /internal/plant-state/{plantInstanceId}` 聚合最新读数、近 24 小时/7 天统计与趋势、设备阈值；
- AI 服务使用有限超时的内部 HTTP 客户端按需拉取，不把瞬时状态写入向量库；
- `PlantStateAnalyzer` 将原始指标确定性转换为 `LIVE_STATE` 与 `SENSOR_HISTORY` Evidence；
- 个体化问题同时路由到植物知识与状态数据，植物名称会增强知识检索；
- 状态证据与可信知识、非可信社区内容分区注入，并携带采集时间与陈旧标记；
- `userId + plantInstanceId` 必填，IoT 服务再次校验实例归属；状态不可用时只做知识降级并明确证据不足。

## 第三阶段能力

- `plant_disease_knowledge` 保存症状、视觉特征、诱因、处理、预防和可追溯来源；通过独立 Qdrant collection + Lucene 完成混合检索。
- 复用现有 `/classify` 病害模型，检测服务只输出视觉候选和置信度，不直接决定处理方案。
- 诊断链路固定融合 `VISUAL_OBSERVATION + DISEASE_KNOWLEDGE + LIVE_STATE/SENSOR_HISTORY + SENSOR_CONSISTENCY`。
- `SensorConsistencyAnalyzer` 使用病害诱因和近7日湿度/温度做确定性支持、冲突或未知判断，不让 LLM 自行计算一致性。
- 输出仍使用统一 Evidence 和 `[E1]` Citation；没有检索到可信病害知识时不产生处理建议。
- 图片不在 AI 服务落盘，只在请求期间转发给检测服务；大小、类型和超时可配置。

## 运行依赖

1. JDK 17 和 Maven 3.9+。
2. Healing Planet 的 `green_community` MySQL 数据库。
3. Qdrant。可在本目录执行：

   ```bash
   docker compose up -d qdrant
   ```

4. OpenAI 兼容的聊天模型 API。
5. OpenAI 兼容的 BGE-M3 `/v1/embeddings` API，例如 vLLM 或 Infinity。Java 服务只做 RAG 编排，不在 JVM 内加载模型。
6. 可选的 BGE reranker HTTP 服务。默认关闭；启用后请求体为 `{model, query, documents}`，响应为 `results[{index,relevance_score}]`。
7. 现有病害分类服务，默认 `POST http://localhost:5000/classify`，响应至少包含 `class_name`/`crop_name`/`label`，可选 `confidence`。

复制 `.env.example` 并通过系统环境变量或 IDE 注入配置。不要提交真实密钥。随后执行：

```bash
mvn spring-boot:run
```

第二阶段还需在两个服务配置同一个随机内部密钥：

```text
healing-planet-ai: PLANT_STATE_API_KEY
smart_green_plant: PLANT_INTERNAL_API_KEY
```

生产环境应由已认证的业务后端或网关填写可信 `userId`，不要直接信任匿名客户端自报身份。

首次启动会在 embedding 模型返回向量维度后创建 Qdrant collection。模型维度变更时应使用新 collection 名称或手动迁移，不能直接复用原 collection。

## 索引流程

应用启动不会扫描并重新向量化业务数据。服务就绪后显式触发一次全量索引：

```bash
curl -X POST http://localhost:8010/internal/index/full \
  -H "X-Internal-Api-Key: replace-with-a-random-secret"
```

帖子发布或修改后调用 `POST /internal/index/post/{postId}`；帖子删除后调用 `DELETE /internal/index/post/{postId}`。文档 ID 是确定性的，重复调用会更新同一条向量和稀疏索引。

## 接口

### 问答

`POST /api/rag/chat`

```json
{
  "query": "我的绿萝今天需要浇水吗？",
  "userId": 7,
  "plantInstanceId": 102,
  "canonicalPlantId": "可选的 plants.id",
  "intent": "可选；PERSONAL_CARE / GENERAL_CARE / COMMUNITY_SEARCH"
}
```

响应的 `answer` 使用 `[E1]` 引用，`evidence` 返回对应来源、内容、各阶段分数和元数据。

### 流式问答

`POST /api/rag/chat/stream`，事件顺序为：

- `evidence`：本轮选中的全部证据；
- `token`：模型增量文本；
- `done`：完成标记。

### 语义搜索

`GET /api/search?q=适合宿舍养又耐阴的植物&canonicalPlantId=可选`

### 索引管理

- `POST /internal/index/full`
- `POST /internal/index/plants`
- `POST /internal/index/community`
- `POST /internal/index/post/{postId}`
- `DELETE /internal/index/post/{postId}`
- `POST /internal/index/diseases`
- `POST /internal/index/disease/{diseaseId}`

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

该接口也用于通用图片问答。`requestedRoute` 可取 `AUTO`、`DISEASE_DIAGNOSIS`、`OCR`、`GENERAL_VISION`；
普通问答使用 `AUTO`，手动选择叶片诊断时使用 `DISEASE_DIAGNOSIS`。`query`、`userId` 和
`plantInstanceId` 均可省略：没有问题文字时使用默认图片分析提示，没有植物实例时跳过传感器证据并明确降级。

首次上传会返回临时 `attachmentId`，在有效期内可只提交 `attachmentId + query` 继续追问同一图片。
图片与结构化视觉观察仅保存在当前服务实例内存，默认 15 分钟、最多 32 个条目；服务重启或多实例切换后需重新上传。
聊天模型必须支持 OpenAI 兼容的图片消息输入，Spring AI 会通过用户消息的 media 字段发送原图。

## 病害知识准备

建表脚本位于 `src/main/resources/db/migration/V3__plant_disease_knowledge.sql`。本项目未引入 Flyway，需由现有数据库发布流程执行。脚本故意不预置未审核的病害处理知识；导入数据时 `source` 必须是可追溯资料，并用 `source_level=TRUSTED/REVIEWED` 标记审核级别。导入后调用病害索引接口。

生产环境必须设置非空 `RAG_INTERNAL_API_KEY`，并在网关层限制 `/internal/**` 只能由内部服务访问。

## 必要验证

```bash
mvn test
```

单元测试只覆盖关键纯逻辑：语义文档转换、RRF 融合、路由、状态分析和提示注入隔离。外部 MySQL、Qdrant、IoT、embedding、reranker 与 LLM 的连通性由部署环境健康检查负责。
