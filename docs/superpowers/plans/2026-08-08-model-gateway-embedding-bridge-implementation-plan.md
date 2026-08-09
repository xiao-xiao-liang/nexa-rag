# Spring AI MilvusVectorStore 完整切换 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以 Spring AI `MilvusVectorStore` 完整替换 retrieval 模块手写的向量化、Milvus 写入、检索和按文档删除链路；业务及框架调用模型时均只通过 `ModelGateway`。

**Architecture:** 新产生的业务 `chunkId` 统一采用标准 UUID（36 字符），直接作为关系库主键、Spring AI `Document.id` 和 Milvus 主键。保留现有 `DocumentChunk`、文档状态机、关键词索引、章节导航和检索结果模型；新建 `ModelGatewayEmbeddingModel` 作为 Spring AI 与 `ModelGateway` 的唯一适配器；新建 `SpringAiDocumentVectorStore` 负责映射 `IndexableChunk` 并调用 `VectorStore`。移除手写 `EmbeddingServiceImpl`、`MilvusVectorIndexClient` 及其“先生成向量再 upsert”的 DTO 链。旧 Milvus collection 的 schema 不复用：停写后删除，再用同名 collection 建立 Spring AI schema。

**Tech Stack:** Java 21、Spring AI 1.1.2、`spring-ai-milvus-store`、Milvus SDK 2.6.6、Spring Boot 3、ModelGateway、JUnit 5、Mockito、Testcontainers（或开发 Milvus 集成测试）。

---

## 已确认的事实与不可变约束

- 现有业务模块只能经 `ModelGateway` 调模型；厂商适配、熔断、降级、监控和审计仍归 Gateway。`EmbeddingClientFactory` 是 Gateway 内 provider 实现细节，仍保留。
- 此次批准删除旧 collection，接受完整切换；不做旧 schema 的兼容读取、双写或回滚开关。
- Spring AI 1.1.2 的正确依赖为 `org.springframework.ai:spring-ai-milvus-store`；它依赖 legacy `MilvusServiceClient`，当前项目固定的 `milvus-sdk-java 2.6.6` 仍提供该 API，仍须以集成测试验证兼容性。
- Spring AI 自动 schema 只包含 `id/content/metadata/embedding`，`add` 的写入语义是 `insert`，不可以直接复用旧 schema 的 `chunk_id/document_id/.../vector` 字段。
- Spring AI Milvus schema 的默认主键最大 36 字符。`DocumentChunkIdGenerator` 必须从 `chunk_<documentId>_<32 位 UUID>`（约 58 字符）改为标准 UUID（36 字符）；`chunkId` 与 `Document.id` 因而具有相同值和相同业务含义。
- 用户将在切换窗口自行 `TRUNCATE` 既有关系库文档块数据，collection 也已确认可删除；本计划不包含历史 `chunkId` / `parentChunkId` 迁移、双 ID 映射、重建历史文档或任何 `TRUNCATE` / `DROP` 操作。
- 每次文档索引必须先删除该 `documentId` 的旧向量，再写入全量当前块；这使 `insert` 达到当前 `upsert` 的业务效果。删除或写入失败必须抛出，让现有索引任务重试，禁止留下静默半成功状态。
- `VectorStore.delete(Filter.Expression)` 不返回 Milvus 删除计数。`DocumentIndexCleanupResult.vectorDeletedCount` 改为“本次删除操作覆盖的数据库已索引块数（期望删除数）”，不再声称是 Milvus 实际返回数。
- `nexa.retrieval.vector.dimension` 从“0 时首写探测”改为固定 `1024`。建 collection 前后均以该值校验 Gateway 返回向量长度；不得以 `EmbeddingModel.dimensions()` 默认探测调用绕过治理。
- 向量 collection 名保持 `nexa_document_chunk`，但它在切换窗口内会被精确删除并用框架 schema 同名重建。当前已清空向量数据不等于 schema 已迁移，仍必须执行 drop + create。
- 历史文档块由用户在切换窗口清理；切换完成后仅处理新导入文档，不提供历史文档重建或 ID 迁移能力。
- Embedding 主备、跨模型语义空间版本、索引全量切换仍不在本期，保留 `TODO.md` 已有待办。

## 新 collection 的逻辑映射

| Spring AI 字段 | 取值 | 说明 |
| --- | --- | --- |
| `id` | `IndexableChunk.chunkId` | 标准 UUID（36 字符）；与业务片段一一对应，是唯一的向量主键。 |
| `content` | `IndexableChunk.indexContent` | 与原链路一致：标题路径等增强内容参与 embedding。 |
| `metadata.documentId` | `Long` | 用于按文档删除的 filter。 |
| `metadata.parentChunkId/chunkOrder/sectionId` | 原值（非空才写） | 保留父子、排序与章节关系。 |
| `metadata.text` | `IndexableChunk.text` | 检索返回给 RAG 的原正文，不返回增强索引文本。 |
| `metadata.metadataJson` | 原 metadata JSON 字符串 | 保存既有扩展信息；不把不受控 JSON 展开为 Milvus filter 字段。 |

`SpringAiDocumentVectorStore` 将对 `documentId` 使用 Spring AI Filter Expression 删除，框架会转换为 Milvus JSON metadata filter。检索时直接将框架返回的 `Document.id` 作为 `VectorIndexSearchResult.chunkId`，因此上层候选融合、重排、章节扩展不需要改协议。

## Task 1: 添加依赖并锁定 Gateway Embedding 桥接契约

**Files:**

- Modify: `nexa-rag-retrieval/pom.xml`
- Create: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/embedding/ModelGatewayEmbeddingModel.java`
- Create: `nexa-rag-retrieval/src/test/java/com/nexarag/retrieval/embedding/ModelGatewayEmbeddingModelTest.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/config/RetrievalProperties.java`
- Modify: `nexa-rag-boot/src/main/resources/application.yml`

- [ ] 添加 BOM 管理的 `spring-ai-milvus-store`（不添加 starter，避免框架根据 `spring.ai.*` 自动装配出绕过 Gateway 的 Bean），保留直接 `milvus-sdk-java` 版本为 2.6.6。
- [ ] 先写单元测试：多个文本按原顺序转发到 `ModelGateway.embedding`；请求固定 `ModelBizType.RETRIEVAL`、配置的 `routeKey`、不读取 `EmbeddingOptions.model`；响应必须一一对应输入并保留 model profile 和总 token。
- [ ] 为 Gateway 空响应、数量不匹配、空向量写失败测试；异常不可吞没，日志不得打印文本和向量正文。
- [ ] 实现 `EmbeddingModel.call(EmbeddingRequest)`、`embed(Document)` 和显式 `dimensions()`。`dimensions()` 仅返回配置维度，非正数时报配置错误且验证 `ModelGateway` 未被调用。
- [ ] 将 `vector.dimension` 标为 `@Min(1)`（或等价启动校验），并将 application.yml 的 `0` 改成已确认的 `1024`；桥接在 Gateway 响应中校验每个向量长度均为 1024。

Run: `mvn -pl nexa-rag-retrieval -am -Dtest=ModelGatewayEmbeddingModelTest test`

## Task 2: 用 Spring AI 封装文档向量存储与 metadata 映射

**Files:**

- Create: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/index/vector/SpringAiDocumentVectorStore.java`
- Create: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/index/vector/SpringAiMilvusVectorStoreConfiguration.java`
- Create: `nexa-rag-retrieval/src/test/java/com/nexarag/retrieval/index/vector/SpringAiDocumentVectorStoreTest.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/DocumentChunkIdGenerator.java`
- Create: `nexa-rag-document/src/test/java/com/nexarag/document/splitter/DocumentChunkIdGeneratorTest.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/config/RetrievalProperties.java`

- [ ] 新增 retrieval 内部接口（例如 `DocumentVectorStore`）：`replaceDocument(documentId, chunks)`、`search(query, topK)`、`deleteByDocumentId(documentId)`；它接受业务块/文本而非 `float[]`，防止任何业务调用方绕过 `EmbeddingModel`。
- [ ] 创建手动 `MilvusServiceClient` 和 `MilvusVectorStore` Bean：连接参数仍从 `nexa.retrieval.vector` 读取，显式使用 `ModelGatewayEmbeddingModel`，设置 collection、embedding dimension、COSINE、`initializeSchema=true`。初始化必须只针对已确认删除旧 collection 的新 schema。
- [ ] `replaceDocument` 先按 `metadata.documentId` 删除，后按 `embedding.maxBatchSize` 分批 `vectorStore.add`；任一批失败即抛出。重试从删除开始，保证没有混杂旧块。
- [ ] `DocumentChunkIdGenerator` 改为 `UUID.randomUUID().toString()`；测试以 `UUID.fromString(id)` 与 `id.length() == 36` 锁定格式。`SpringAiDocumentVectorStore` 直接把 `chunkId` 设为 `Document.id`；写入成功后返回 `chunkId -> chunkId` 给数据库回写，不引入第二套 vector ID，也不在 metadata 冗余存储 chunk ID。
- [ ] `search` 用 `SearchRequest` 调用框架，直接以返回 `Document.id` 作为 `VectorIndexSearchResult.chunkId`。严格校验 metadata 类型/必填 `documentId`；损坏记录记录脱敏告警并跳过，不能返回伪造业务块。
- [ ] `deleteByDocumentId` 使用 metadata filter；在执行前由 repository 取得 indexed chunk 数作为 expected count 并返回。测试要断言 filter key 为 `documentId`，而非旧 schema 字段名 `document_id`。

Run: `mvn -pl nexa-rag-retrieval -am -Dtest=SpringAiDocumentVectorStoreTest test`

## Task 3: 替换文档索引与对话召回调用链

**Files:**

- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/service/impl/DocumentIndexServiceImpl.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/service/impl/DocumentIndexCleanerImpl.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/retriever/vector/MilvusConversationRetriever.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/repository/ChunkIndexRepository.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/repository/ChunkIndexRepositoryImpl.java`
- Modify/Create: 对应 `DocumentIndexServiceImplTest`、`DocumentIndexCleanerTest`、`MilvusConversationRetrieverTest`

- [ ] `DocumentIndexServiceImpl` 删除 `EmbeddingService` 与旧 `VectorIndexClient` 注入；向量阶段改为单次 `replaceDocument`，然后按返回的 vector ID 标记每一个 chunk。关键词与章节导航时序不变。
- [ ] `MilvusConversationRetriever` 删除直接 `ModelGateway.embedding` 和 `float[]` 请求构造，只向 `DocumentVectorStore.search(question, limit)` 请求；模型调用仍由内部桥接执行。保持分数阈值、rank、`RetrievalChunk` 映射与 channel 名称 `MILVUS`。
- [ ] `DocumentIndexCleanerImpl` 通过新存储删除向量；先读 `listIndexedChunks(documentId).size()` 作为预期删除数，并更新 JavaDoc/测试，明确不是 Milvus 实际 delete count。关键词与导航的“即使一项失败仍继续”语义不变。
- [ ] 所有写入失败继续将 chunk 标为失败并由既有任务重试；不得为了 insert 改成“只更新成功块”。

Run: `mvn -pl nexa-rag-retrieval -am -Dtest=DocumentIndexServiceImplTest,DocumentIndexCleanerTest,MilvusConversationRetrieverTest test`

## Task 4: 删除旧手写向量链路并做静态防回退检查

**Files:**

- Delete: `EmbeddingService.java`、`EmbeddingServiceImpl.java`、`ChunkEmbedding.java`
- Delete: `VectorIndexClient.java`、`MilvusVectorIndexClient.java`、`VectorIndexWriteRequest.java`、`VectorIndexDocument.java`、旧 `VectorIndexWriteResult`（若无其他使用者）
- Delete/Rewrite: 对应旧单测 `EmbeddingServiceImplTest`、`MilvusVectorIndexClientTest`
- Modify: `RetrievalProperties`、`IndexConfigSnapshot`、`IndexConfigResolver`（移除不再生效的 `embeddingRouteKey` / per-document vector collection 伪配置，保留确有用途的字段）
- Modify: `docs/operations/structured-section-rebuild.md`（若其中记录了旧 schema）

- [ ] `rg` 确认 retrieval 中不存在 `MilvusClientV2`、`UpsertReq`、`SearchReq`、`EmbeddingService`、`VectorIndexWriteRequest` 的生产引用。
- [ ] 保留 `io.milvus` 的唯一用途为 Spring AI Store 所需的兼容依赖；不能保留手写 schema/SDK 的死代码。
- [ ] 不删除 `EmbeddingClientFactory` 或 Gateway provider；它不属于 VectorStore 迁移范围。

Run: `mvn -pl nexa-rag-retrieval -am test`

## Task 5: 一次性 collection 切换验证

**Files:**

- Create: `docs/operations/spring-ai-milvus-cutover.md`
- Create: `nexa-rag-boot/src/test/java/.../SpringAiMilvusVectorStoreIntegrationTest.java`

- [ ] 运行文档记录由用户自行完成的切换前置清理：停止索引写入、清理关系库既有文档块、精确删除 `<database>.nexa_document_chunk` collection；应用代码和启动流程不得自动执行这些破坏性操作。
- [ ] 清理完成后部署新代码，以 `1024` 维启动并确认新 schema；新的文档处理流程生成 UUID `chunkId` 后写入并检索，验证 `Document.id == chunkId`、章节/父子 metadata、按 documentId 删除和同一文档重写不产生重复。
- [ ] 集成测试连接独立测试 collection：验证 schema 初始化、写入两块、按 query 召回 `Document.id` 作为业务 `chunkId` 及其 `sectionId/text`、按 documentId 删除、重写同一文档不产生重复、Gateway bridge 被调用；严禁使用开发 collection。
- [ ] 文档中写明：drop collection 为破坏性操作，实际执行前需要本次已确认的授权及精确数据库/collection 双重核对；本计划本身不执行 drop。

## 最终验证与验收

- [ ] `mvn -pl nexa-rag-retrieval -am test`
- [ ] `mvn -pl nexa-rag-boot -am -Dtest=SpringAiMilvusVectorStoreIntegrationTest test`（仅在独立 Milvus 测试环境可用时）
- [ ] `mvn -pl nexa-rag-retrieval dependency:tree -Dincludes=org.springframework.ai:spring-ai-milvus-store,io.milvus:milvus-sdk-java`
- [ ] `rg -n "MilvusClientV2|UpsertReq|SearchReq|EmbeddingService|VectorIndexWriteRequest" nexa-rag-retrieval/src/main/java` 无命中。
- [ ] `git diff --check`
- [ ] `git status --short`

验收标准：新 collection 只含 Spring AI schema；写入和查询各只经过 `ModelGatewayEmbeddingModel -> ModelGateway`；检索能恢复原业务 `chunkId`、文档、章节、父子和正文信息；按文档清理和重建可重复执行；未改变关键词/章节导航的失败聚合语义。

## 非本期内容

- Embedding 主备的语义空间校验、索引版本与原子切换（见 `TODO.md`）。
- 旧 collection 双写、灰度读取或回滚开关（用户已确认完整切换）。
- 原生多供应商 ChatModel 接入（仍是路线图 P2）。
