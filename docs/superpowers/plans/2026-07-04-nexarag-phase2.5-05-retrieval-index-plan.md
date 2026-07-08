# Phase 2.5-05 检索索引接口专项设计与实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

## 1. 目标

实现 `CHUNKED -> INDEXING -> INDEXED` 阶段的检索索引接口和初版 mock 索引闭环，为后续真实 Milvus 向量索引、Elasticsearch 关键词索引和 Workflow Graph 编排预留稳定接口。

本批次目标不是直接完成真实检索引擎写入，而是在 `nexa-rag-retrieval` 模块先沉淀正确的业务边界：

- 根据文档 ID 查询待索引 chunk。
- 跳过 `skipIndex=1` 或 `SKIP_INDEX` 的父片段。
- 读取文档处理配置中的 `indexConfig`。
- 按配置决定是否写入向量索引和关键词索引。
- 初版使用 mock 适配器生成 `vectorId` 和 `keywordIndexId`。
- 回写 chunk 索引状态、索引 ID 和失败原因。
- 推进文档状态 `CHUNKED -> INDEXING -> INDEXED`。
- 暴露索引清理入口，供 07 重处理清理和删除清理复用。

## 2. 设计依据

### 2.1 Phase 2.5 总体链路

完整产品语义：

```text
UPLOAD -> PARSING -> CHUNKING -> INDEXING -> INDEXED
```

MySQL 稳定状态：

```text
UPLOADED -> QUEUED -> PARSING -> PARSED -> CHUNKING -> CHUNKED -> INDEXING -> INDEXED
```

05 承接 04 的输出：

- `document.status = CHUNKED`。
- `document_chunk.status = PENDING_INDEX` 或 `SKIP_INDEX`。
- `document_chunk.skip_index = 0/1`。
- `document_chunk.text` 已保存切分文本。
- `document_chunk.parent_chunk_id` 已保存父子关系。

05 输出：

- 可索引 chunk 更新为 `INDEXED` 或 `FAILED`。
- `skipIndex=1` 的 chunk 保持或修正为 `SKIP_INDEX`。
- 成功 chunk 回写 `vectorId` 和/或 `keywordIndexId`。
- 文档成功后推进为 `INDEXED`。

### 2.2 模块边界

`nexa-rag-retrieval` 负责：

- 定义文档索引应用服务。
- 定义向量索引、关键词索引、索引清理抽象。
- 根据 `document_chunk` 生成索引请求。
- 调用模型模块生成 embedding。
- 调用向量/关键词索引适配器。
- 通过 `DocumentChunkService` 回写 chunk 索引结果。
- 通过 `DocumentService` 推进文档状态。

`nexa-rag-document` 负责：

- 保存 document 和 document_chunk。
- 暴露 service 接口供 retrieval 查询和更新。
- 不直接写 Milvus、Elasticsearch 或关键词索引。

`nexa-rag-model` 负责：

- 提供 `ModelGateway.embedding(...)`。
- 屏蔽具体 embedding 模型供应商。

`nexa-rag-workflow` 后续只负责调用：

```java
DocumentIndexService.indexDocument(documentId)
```

禁止：

- `retrieval` 依赖 `document.mapper` 或 `document.service.impl`。
- `workflow` 直接操作 Milvus/Elasticsearch。
- `document` 直接调用 `ModelGateway` 或索引 SDK。

## 3. 环境约定

- MinIO 默认地址：`127.0.0.1`。
- MinerU 默认地址：`127.0.0.1`。
- MySQL 默认地址：`192.168.0.134`。
- Redis 默认地址：`192.168.0.134`。
- Elasticsearch 默认地址：`192.168.0.134:9200`。
- Milvus 默认地址：`192.168.0.134:19530`，即 Milvus 默认 gRPC 端口。

本批单元测试不连接外部服务。

Milvus/Elasticsearch 真实连通冒烟放到后续集成测试或真实适配批次，05 只完成接口和 mock 适配。

## 4. Scope

包含：

- `DocumentIndexService`。
- `DocumentIndexServiceImpl`。
- `DocumentIndexResult`。
- `DocumentChunkIndexResult`。
- `IndexConfigSnapshot`。
- `IndexableChunk`。
- `DocumentChunkIndexRepository`。
- `VectorIndexClient`。
- `KeywordIndexClient`。
- `MockVectorIndexClient`。
- `MockKeywordIndexClient`。
- `EmbeddingService` 或 `ChunkEmbeddingService`。
- `Noop/Mock` 索引清理入口。
- document 模块补充必要的 chunk 查询与回写 service 方法。

不包含：

- 真实 Milvus SDK 写入。
- 真实 Elasticsearch SDK 写入。
- 真实 embedding 模型可用性保证。
- Rerank。
- 检索召回接口。
- 聊天 RAG Workflow。
- 分布式索引任务并发控制。
- 批量超大文档的分片索引优化。

## 5. 核心流程

### 5.1 成功流程

```text
DocumentIndexService.indexDocument(documentId)
  -> DocumentService.getRequiredDocument(documentId)
  -> validate status == CHUNKED
  -> mark document status = INDEXING
  -> read IndexConfigSnapshot from processConfigJson
  -> DocumentChunkIndexRepository.listIndexableChunks(documentId)
  -> mark skip chunks as SKIP_INDEX if needed
  -> if index disabled: mark indexable chunks as INDEXED without index IDs
  -> if vector enabled: ChunkEmbeddingService.embed(chunks)
  -> VectorIndexClient.upsert(batch)
  -> if keyword enabled: KeywordIndexClient.upsert(batch)
  -> DocumentChunkIndexRepository.markIndexed(...)
  -> mark document status = INDEXED
  -> return DocumentIndexResult
```

### 5.2 部分失败流程

```text
for each chunk:
  try index
  catch exception:
    mark chunk FAILED and failureReason

if any chunk failed:
  DocumentService.recordProcessFailure(documentId, "INDEX", "文档索引失败", detail)
else:
  document.status = INDEXED
```

初版建议采取“有任意失败则文档失败或重试”的保守策略，避免用户看到部分可检索、部分不可检索的混合状态。

后续可扩展为部分成功可查询和失败 chunk 补偿。

## 6. 类设计

### 6.1 `DocumentIndexService`

包：`com.nexarag.retrieval.service`

职责：对外提供文档索引写入和索引清理入口。

```java
public interface DocumentIndexService {

    DocumentIndexResult indexDocument(Long documentId);

    DocumentIndexCleanupResult cleanupDocumentIndex(Long documentId);
}
```

说明：

- Workflow 06 只依赖这个接口。
- 07 重处理清理和删除清理也复用 `cleanupDocumentIndex`。
- 不暴露 Milvus/Elasticsearch 细节。

### 6.2 `DocumentIndexServiceImpl`

包：`com.nexarag.retrieval.service.impl`

职责：文档索引阶段应用服务，负责编排状态、配置、chunk、embedding 和索引适配器。

依赖：

- `DocumentService`。
- `DocumentChunkIndexRepository`。
- `IndexConfigResolver`。
- `ChunkEmbeddingService`。
- `VectorIndexClient`。
- `KeywordIndexClient`。
- `DocumentIndexCleaner`。

关键规则：

- 只允许 `CHUNKED` 状态进入索引。
- `INDEXED` 状态再次调用时可直接返回已有统计，保持幂等。
- 状态推进使用 `DocumentService.updateById`，初版不强求条件更新；后续可补充 CAS 方法。
- 索引失败时调用 `DocumentService.recordProcessFailure(documentId, "INDEX", ...)`。
- 不在日志中输出 chunk 全文。

### 6.3 `DocumentIndexResult`

包：`com.nexarag.retrieval.dto`

职责：描述一次文档索引结果。

```java
public record DocumentIndexResult(
        Long documentId,
        boolean success,
        int totalChunkCount,
        int indexedChunkCount,
        int skippedChunkCount,
        int failedChunkCount,
        boolean vectorEnabled,
        boolean keywordEnabled,
        String failureReason,
        List<DocumentChunkIndexResult> chunks
) {
}
```

### 6.4 `DocumentChunkIndexResult`

包：`com.nexarag.retrieval.dto`

职责：描述单个 chunk 的索引结果。

```java
public record DocumentChunkIndexResult(
        String chunkId,
        boolean success,
        boolean skipped,
        String vectorId,
        String keywordIndexId,
        String failureReason
) {
}
```

### 6.5 `DocumentIndexCleanupResult`

包：`com.nexarag.retrieval.dto`

职责：描述索引清理结果。

```java
public record DocumentIndexCleanupResult(
        Long documentId,
        int vectorDeletedCount,
        int keywordDeletedCount,
        boolean success,
        String failureReason
) {
}
```

### 6.6 `IndexConfigSnapshot`

包：`com.nexarag.retrieval.config`

职责：把 `document.processConfigJson` 中的 `IndexConfigRequest` 解析成稳定运行时配置。

```java
public record IndexConfigSnapshot(
        boolean enabled,
        boolean vectorEnabled,
        boolean keywordEnabled,
        String embeddingRouteKey,
        String vectorCollection,
        String keywordIndexName
) {
}
```

初版默认：

- `enabled=true`。
- `vectorEnabled=true`。
- `keywordEnabled=true`。
- `embeddingRouteKey=null`，交给 model 模块默认路由。
- `vectorCollection="nexa_document_chunk"`。
- `keywordIndexName="nexa_document_chunk"`。

### 6.7 `IndexConfigResolver`

包：`com.nexarag.retrieval.config`

职责：解析索引配置。

```java
public class IndexConfigResolver {

    IndexConfigSnapshot resolve(Document document);
}
```

规则：

- `processConfigJson` 为空时使用默认值。
- `indexConfig.enabled=false` 时本次不写任何索引，但 chunk 可标记为 `INDEXED`，表示流水线完成。
- `vectorEnabled=false` 时不调用 embedding 和 vector client。
- `keywordEnabled=false` 时不调用 keyword client。
- 配置解析失败抛 `ServiceException`，错误码可复用 `DOCUMENT_PROCESS_CONFIG_INVALID` 或 retrieval 自己错误码。

### 6.8 `IndexableChunk`

包：`com.nexarag.retrieval.model`

职责：retrieval 内部使用的可索引 chunk 快照，避免索引阶段直接修改实体对象。

```java
public record IndexableChunk(
        String chunkId,
        Long documentId,
        Integer chunkOrder,
        String parentChunkId,
        String text,
        String metadataJson,
        Integer tokenCount
) {
}
```

### 6.9 `DocumentChunkIndexRepository`

包：`com.nexarag.retrieval.repository`

职责：封装 retrieval 对 document chunk 的查询和回写。

依赖：`DocumentChunkService`。

方法：

```java
public interface DocumentChunkIndexRepository {

    List<IndexableChunk> listIndexableChunks(Long documentId);

    List<DocumentChunk> listSkippedChunks(Long documentId);

    void markSkipped(Long documentId);

    void markIndexed(String chunkId, String vectorId, String keywordIndexId);

    void markFailed(String chunkId, String failureReason);

    List<DocumentChunk> listIndexedChunks(Long documentId);
}
```

实现建议：`DocumentChunkIndexRepositoryImpl`。

注意：

- 初版可以调用 `DocumentChunkService.listByDocumentId(documentId)` 后在内存中过滤。
- 为避免大量 chunk 内存问题，后续再改成分页查询。
- 如果需要批量更新，优先在 `DocumentChunkService` 增加方法，而不是依赖 mapper。

### 6.10 `ChunkEmbeddingService`

包：`com.nexarag.retrieval.embedding`

职责：把 chunk 文本转换为向量。

```java
public interface ChunkEmbeddingService {

    List<ChunkEmbedding> embed(List<IndexableChunk> chunks, IndexConfigSnapshot config);
}
```

`ChunkEmbedding`：

```java
public record ChunkEmbedding(String chunkId, float[] vector, String modelProfile, Integer tokenCount) {
}
```

初版策略：

- 如果处于 mock 索引模式，可以使用 `MockChunkEmbeddingService` 生成确定性向量，避免单元测试依赖模型服务。
- 未来真实模式使用 `ModelGateway.embedding(...)`。

真实模式设计：

```java
EmbeddingModelRequest.builder()
    .bizType(ModelBizType.RETRIEVAL)
    .bizId(documentId.toString())
    .routeKey(config.embeddingRouteKey())
    .texts(chunks.map(IndexableChunk::text))
    .build()
```

### 6.11 `VectorIndexClient`

包：`com.nexarag.retrieval.index.vector`

职责：向量索引写入和清理抽象。

```java
public interface VectorIndexClient {

    List<VectorIndexWriteResult> upsert(VectorIndexWriteRequest request);

    int deleteByDocumentId(Long documentId);
}
```

`VectorIndexWriteRequest`：

```java
public record VectorIndexWriteRequest(
        String collectionName,
        Long documentId,
        List<VectorIndexDocument> documents
) {
}
```

`VectorIndexDocument`：

```java
public record VectorIndexDocument(
        String chunkId,
        Long documentId,
        String parentChunkId,
        Integer chunkOrder,
        String text,
        String metadataJson,
        float[] vector
) {
}
```

`VectorIndexWriteResult`：

```java
public record VectorIndexWriteResult(String chunkId, String vectorId, boolean success, String failureReason) {
}
```

初版实现：`MockVectorIndexClient`。

规则：

- 生成稳定 ID：`mock-vector-{documentId}-{chunkId}`。
- 不连接 Milvus。
- 单元测试可断言 vectorId。

未来真实实现：`MilvusVectorIndexClient`。

Milvus 默认配置预留：

```yaml
nexa:
  retrieval:
    vector:
      type: mock # mock / milvus
      milvus:
        host: 192.168.0.134
        port: 19530
        collection-name: nexa_document_chunk
        dimension: 1536
```

### 6.12 `KeywordIndexClient`

包：`com.nexarag.retrieval.index.keyword`

职责：关键词索引写入和清理抽象。

```java
public interface KeywordIndexClient {

    List<KeywordIndexWriteResult> upsert(KeywordIndexWriteRequest request);

    int deleteByDocumentId(Long documentId);
}
```

初版实现：`MockKeywordIndexClient`。

规则：

- 生成稳定 ID：`mock-keyword-{documentId}-{chunkId}`。
- 不连接 Elasticsearch。

未来真实实现：`ElasticsearchKeywordIndexClient`。

配置预留：

```yaml
nexa:
  retrieval:
    keyword:
      type: mock # mock / elasticsearch
      elasticsearch:
        host: 192.168.0.134
        port: 9200
        index-name: nexa_document_chunk
```

### 6.13 `DocumentIndexCleaner`

包：`com.nexarag.retrieval.cleanup`

职责：封装索引清理顺序。

```java
public interface DocumentIndexCleaner {

    DocumentIndexCleanupResult cleanup(Long documentId);
}
```

实现：`DocumentIndexCleanerImpl`。

规则：

1. 删除向量索引。
2. 删除关键词索引。
3. 清理成功返回删除数量。
4. 清理失败抛出 `ServiceException` 或返回 `success=false`，由调用方决定是否中断重处理。

07 会基于这个接口做重处理前清理和删除后异步清理。

## 7. document 模块配合改动

如果当前 `DocumentChunkService` 无法支持索引回写，05 需要最小扩展 service 接口，不允许 retrieval 直接依赖 mapper。

建议新增：

```java
List<DocumentChunk> listByDocumentIdAndStatus(Long documentId, ChunkStatus status);

void markChunkIndexed(String chunkId, String vectorId, String keywordIndexId);

void markChunkIndexFailed(String chunkId, String failureReason);

void markDocumentSkippedChunks(Long documentId);
```

初版也可以由 `DocumentChunkIndexRepositoryImpl` 通过 `DocumentChunkService.lambdaUpdate()` 完成，但仍只依赖 service。

## 8. 状态机设计

文档状态：

- 入口状态：`CHUNKED`。
- 执行中：`INDEXING`。
- 成功：`INDEXED`。
- 失败：`QUEUED` 或 `FAILED`，由 `DocumentService.recordProcessFailure` 的重试逻辑决定。

chunk 状态：

- `PENDING_INDEX`：待索引。
- `SKIP_INDEX`：父片段或禁用索引时可跳过。
- `INDEXED`：索引完成。
- `FAILED`：索引失败。

禁用索引的语义：

- `indexConfig.enabled=false` 表示不写外部索引。
- 文档仍可完成入库流水线，状态推进到 `INDEXED`。
- 可索引 chunk 标记为 `INDEXED`，但 `vectorId/keywordIndexId` 为空。
- 这样避免文档永远停在 `CHUNKED`。

## 9. Mock 与真实适配分层

推荐包结构：

```text
nexa-rag-retrieval
└── src/main/java/com/nexarag/retrieval
    ├── cleanup
    ├── config
    ├── dto
    ├── embedding
    ├── index
    │   ├── keyword
    │   └── vector
    ├── model
    ├── repository
    └── service
```

mock 实现：

- `MockChunkEmbeddingService`。
- `MockVectorIndexClient`。
- `MockKeywordIndexClient`。

真实预留：

- `ModelGatewayChunkEmbeddingService`。
- `MilvusVectorIndexClient`。
- `ElasticsearchKeywordIndexClient`。

选择方式：

```yaml
nexa:
  retrieval:
    embedding:
      type: mock # mock / model
    vector:
      type: mock # mock / milvus
    keyword:
      type: mock # mock / elasticsearch
```

05 初版可以先不实现完整 `@ConfigurationProperties`，但类名和接口必须便于后续无破坏替换。

## 10. 错误处理

### 10.1 单个 chunk 失败

- `DocumentChunkIndexRepository.markFailed(chunkId, reason)`。
- `DocumentIndexResult.failedChunkCount + 1`。
- 文档最终调用 `recordProcessFailure`。

### 10.2 embedding 返回数量不匹配

- 视为整批失败。
- 所有本批 chunk 标记 `FAILED`。
- 文档记录 `failureStage=INDEX`。

### 10.3 vector 成功 keyword 失败

初版保守策略：

- chunk 标记 `FAILED`。
- 已写入的 vector 不立即回滚。
- 07 清理补偿负责统一清理。

未来可增加事务型补偿或阶段级清理。

### 10.4 清理失败

- 返回 `success=false` 或抛 `ServiceException`。
- 重处理入口必须中断。
- 删除清理任务可重试。

## 11. 测试设计

### 11.1 retrieval 单元测试

新增：

- `DocumentIndexServiceImplTest`。
- `IndexConfigResolverTest`。
- `MockVectorIndexClientTest`。
- `MockKeywordIndexClientTest`。
- `DocumentIndexCleanerTest`。
- `DocumentChunkIndexRepositoryTest`。

覆盖：

- `CHUNKED` 文档可进入索引并变为 `INDEXED`。
- `PENDING_INDEX + skipIndex=0` 的 chunk 被写入 mock vector/keyword。
- `skipIndex=1` 的 chunk 不写索引，状态为 `SKIP_INDEX`。
- `indexConfig.enabled=false` 时不调用索引 client，但文档仍 `INDEXED`。
- `vectorEnabled=false` 时只写关键词索引。
- `keywordEnabled=false` 时只写向量索引。
- chunk 索引失败后文档进入自动重试或失败。
- cleanup 调用 vector 和 keyword client。

### 11.2 模块测试

```powershell
mvn -pl nexa-rag-retrieval,nexa-rag-document -am test
```

### 11.3 架构边界测试

```powershell
mvn -pl nexa-rag-boot -am test -Dtest=ModuleDependencyTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

### 11.4 外部服务测试

本批不连接 Milvus/Elasticsearch。

后续真实适配冒烟：

- Milvus：`192.168.0.134:19530`。
- Elasticsearch：`192.168.0.134:9200`。

## 12. 实施计划

### Task 1: 基线验证

- [ ] 检查工作区。

```powershell
git status --short --branch
```

- [ ] 运行相关模块测试。

```powershell
mvn -pl nexa-rag-retrieval,nexa-rag-document -am test
```

### Task 2: retrieval 核心 DTO 和配置解析

- [ ] 新增 `DocumentIndexResult`。
- [ ] 新增 `DocumentChunkIndexResult`。
- [ ] 新增 `DocumentIndexCleanupResult`。
- [ ] 新增 `IndexConfigSnapshot`。
- [ ] 新增 `IndexConfigResolver` 和测试。

### Task 3: document chunk 索引回写边界

- [ ] 新增 `IndexableChunk`。
- [ ] 新增 `DocumentChunkIndexRepository`。
- [ ] 实现 `DocumentChunkIndexRepositoryImpl`。
- [ ] 如有必要，扩展 `DocumentChunkService` 的索引查询和回写方法。
- [ ] 补充 repository 测试或 service 测试。

### Task 4: mock embedding 和索引 client

- [ ] 新增 `ChunkEmbeddingService`。
- [ ] 新增 `ChunkEmbedding`。
- [ ] 实现 `MockChunkEmbeddingService`。
- [ ] 新增 `VectorIndexClient`、请求和响应模型。
- [ ] 实现 `MockVectorIndexClient`。
- [ ] 新增 `KeywordIndexClient`、请求和响应模型。
- [ ] 实现 `MockKeywordIndexClient`。

### Task 5: 文档索引服务

- [ ] 新增 `DocumentIndexService`。
- [ ] 实现 `DocumentIndexServiceImpl`。
- [ ] 支持状态推进 `CHUNKED -> INDEXING -> INDEXED`。
- [ ] 支持 disabled/vector-only/keyword-only 配置。
- [ ] 支持失败记录 `failureStage=INDEX`。
- [ ] 补充 `DocumentIndexServiceImplTest`。

### Task 6: 索引清理入口

- [ ] 新增 `DocumentIndexCleaner`。
- [ ] 实现 `DocumentIndexCleanerImpl`。
- [ ] `DocumentIndexService.cleanupDocumentIndex` 委托 cleaner。
- [ ] 补充 `DocumentIndexCleanerTest`。

### Task 7: 验证

- [ ] 运行 retrieval 指定测试。

```powershell
mvn -pl nexa-rag-retrieval -am test -Dtest=DocumentIndexServiceImplTest,IndexConfigResolverTest,MockVectorIndexClientTest,MockKeywordIndexClientTest,DocumentIndexCleanerTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

- [ ] 运行模块测试。

```powershell
mvn -pl nexa-rag-retrieval,nexa-rag-document -am test
```

- [ ] 运行架构边界测试。

```powershell
mvn -pl nexa-rag-boot -am test -Dtest=ModuleDependencyTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

- [ ] 检查空白问题。

```powershell
git diff --check
```

## 13. 编码规范

实施时必须遵守：

- 新增类必须有简体中文 JavaDoc。
- public 方法必须有简体中文 JavaDoc。
- 关键步骤使用编号注释。
- 日志使用简体中文。
- 日志不得输出完整 chunk 文本、embedding 向量、敏感配置。
- 单元测试优先验证真实业务结果。
- 不修改当前批次无关文件。
- retrieval 不依赖 document mapper 或 impl。

## 14. 验收标准

- `nexa-rag-retrieval` 提供稳定 `DocumentIndexService`。
- `CHUNKED` 文档可通过 mock 索引变为 `INDEXED`。
- 可索引 chunk 回写 mock `vectorId` 和 `keywordIndexId`。
- `skipIndex=1` chunk 不写索引。
- `indexConfig` 能控制索引启停、向量索引启停、关键词索引启停。
- cleanup 入口可调用 vector/keyword 清理 client。
- 单元测试通过。
- 架构边界测试通过。
- `git diff --check` 无输出。
