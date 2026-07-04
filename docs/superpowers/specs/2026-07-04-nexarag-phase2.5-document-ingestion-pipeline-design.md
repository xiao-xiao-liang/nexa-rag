# NexaRAG Phase 2.5 真实文档入库流水线专项设计

## 1. 背景

阶段二已经完成文档领域基础闭环，包括文档状态机、文档片段模型、Controller 骨架、parser/storage 抽象、切分器抽象和基础测试。阶段二明确不实现真实上传、MinIO、MinerU、Tika、真实切分器、Redis 排队、索引写入和 Workflow Graph。

Phase 2.5 用于承接这些真实能力，并把它们放回正确的模块边界中：基础设施能力由 `nexa-rag-infra` 提供，文档领域能力由 `nexa-rag-document` 提供，索引能力由 `nexa-rag-retrieval` 提供，`nexa-rag-workflow` 只负责组合这些能力并搭建文档入库工作流。

本设计参考了 `E:\Code\Projects\Hollis\LLMentor\know-engine` 的文档入库实现。`know-engine` 使用上传、MinIO、MinerU ZIP 解析、Markdown/Excel 切分、Spring Event 异步向量化和补偿任务串起文档处理链路。NexaRAG 不原样照搬该实现，而是借鉴其中的文件哈希、MinIO 原文和解析后文件分层、MinerU ZIP 产物处理、父子片段、Excel 结构化切分、事务提交后再进入索引阶段、补偿任务兜底等经验，并按 NexaRAG 的多模块架构重新设计。

## 2. 目标

Phase 2.5 的目标是完成真实文档入库流水线设计，使用户上传文件后可以自动进入后台处理，并最终变为可检索文档。

目标如下：

- 提供真实 Multipart 文档上传入口。
- 将原始文件保存到 MinIO。
- 支持默认处理配置，并允许上传时传入嵌套配置对象。
- 上传接口在文档入队后立即返回，不阻塞等待解析、切分和索引完成。
- 使用 Redis 保存实时排队信息、等待人数、队列位置和租约。
- 初版整条文档处理流水线只排一次，未来预留阶段级队列。
- 使用 Spring AI Alibaba Graph 实现文档入库 Workflow。
- Workflow 只编排能力，不下沉业务实现。
- 支持 MinerU 解析 Word/PDF 为 Markdown。
- 支持 Tika 解析 PPT/TXT 等文本类文件。
- 支持 Markdown、Excel/CSV、正则文本等真实切分器。
- 支持文档重处理前清理旧 chunk、向量索引和关键词索引。
- 支持文档删除后的异步资源清理任务。
- 保持模块依赖方向清晰，业务模块不反向依赖 `workflow`。

## 3. 非目标

Phase 2.5 不要求一次性完成以下能力的完整生产级闭环，但必须在设计中预留扩展位置：

- 多租户权限模型。
- 文档版本管理。
- 分布式多实例 Worker 的完整一致性协议。
- 精细化阶段级队列的完整实现。
- 大文件分片上传。
- 前端上传页和进度页。
- 聊天 RAG Workflow。
- Sa-Token 登录鉴权。

## 4. 总体架构

真实文档入库采用“上传入口 + Redis 排队态 + 本地 Worker + Workflow Graph + 模块能力服务”的架构。

```text
POST /api/documents/upload
  -> DocumentUploadService 上传服务
  -> FileStorageService 保存 MinIO 原始文件
  -> DocumentService 创建 document(status=UPLOADED)
  -> ProcessConfigDefaults 合并默认处理配置
  -> DocumentService.submitProcess(documentId)
  -> document(status=QUEUED)
  -> DocumentProcessTaskDispatcher 写入 Redis 队列态
  -> 返回 documentId/status/queueInfo

后台 Worker
  -> 获取流水线任务租约
  -> DocumentIngestionWorkflow.run(documentId)
  -> ParsingNode
  -> ChunkingNode
  -> IndexingNode
  -> document(status=INDEXED)
```

对用户而言，产品语义是：

```text
上传 -> 自动解析 -> 自动切分 -> 自动索引 -> 可检索
```

对系统而言，稳定状态流是：

```text
UPLOADED -> QUEUED -> PARSING -> PARSED -> CHUNKING -> CHUNKED -> INDEXING -> INDEXED
```

失败时统一进入：

```text
FAILED
```

`UPLOAD` 是入口阶段，不作为 Graph Node。Graph 从 `PARSING` 开始，避免把 Multipart 上传、文件流处理和 HTTP 请求生命周期混进 Workflow。

## 5. 模块边界

### 5.1 nexa-rag-infra

职责：

- MinIO 文件存储适配。
- MinerU 解析器适配。
- Tika 解析器适配。
- Redis 队列和租约适配。
- 未来 MQ 适配。

边界：

- `infra` 不依赖 `document`、`retrieval`、`workflow`、`chat`、`auth`。
- `infra` 只暴露技术抽象和适配结果，不把 MinIO SDK、Redis SDK、MinerU HTTP 细节泄漏给业务模块。

### 5.2 nexa-rag-document

职责：

- 上传入口和上传服务。
- 文档状态推进、失败记录、重试次数维护。
- 处理配置默认值和配置快照。
- 调用 `infra` 的 storage/parser 抽象完成解析。
- 真实切分器实现。
- `document_chunk` 落库。
- 重处理前旧 chunk 清理。
- 文档删除后的资源清理任务发布入口。

边界：

- `document` 可以依赖 `infra` 抽象。
- `document` 不依赖 `workflow`。
- `document` 不直接写 Elasticsearch、Milvus 或关键词索引。
- `document` 不直接实现 Redis Worker 调度逻辑。

### 5.3 nexa-rag-retrieval

职责：

- 定义文档索引写入接口。
- 定义向量索引写入、关键词索引写入和索引清理接口。
- 根据 `DocumentChunk` 写入索引并回写 `vectorId`、`keywordIndexId`。
- 处理部分 chunk 索引失败，并向上返回失败原因和失败阶段。

边界：

- `retrieval` 可以依赖 `document` 的 service 接口和 entity。
- `retrieval` 禁止依赖 `document.mapper` 和 `document.service.impl`。
- Elasticsearch、Milvus 等技术适配保持在 `retrieval` 内部或其技术适配子包中。

### 5.4 nexa-rag-workflow

职责：

- 定义 `DocumentIngestionWorkflow`。
- 定义 `ParsingNode`、`ChunkingNode`、`IndexingNode`。
- 定义文档入库 StateKeys 和状态读写工具。
- 调用 `document`、`retrieval` 提供的服务接口。
- 在节点失败时调用统一失败记录服务。

边界：

- `workflow` 只做状态读取、服务调用、状态写入和流程分派。
- `workflow` 不直接读写 Mapper。
- `workflow` 不直接操作 MinIO。
- `workflow` 不直接调用 MinerU/Tika。
- `workflow` 不直接写 ES/Milvus。
- `workflow` 日志只记录节点进入、退出、documentId、状态和失败阶段，不输出完整文档内容。

### 5.5 nexa-rag-boot

职责：

- 注册配置属性。
- 放置 Flyway 迁移脚本。
- 装配本地 Worker、Redis 队列适配和 Graph Bean。
- 补充架构边界测试。
- 作为最终运行入口。

## 6. API 设计

### 6.1 上传接口

接口：

```text
POST /api/documents/upload
Content-Type: multipart/form-data
```

参数：

- `file`：上传文件，类型为 `MultipartFile`。
- `request`：上传配置对象，建议由 JSON Part 承载。

请求对象：

```java
public record UploadDocumentRequest(
        String title,
        String description,
        SplitConfigRequest splitConfig,
        ParseConfigRequest parseConfig,
        IndexConfigRequest indexConfig
) {
}
```

默认值规则：

- `splitConfig` 为空时由服务层补默认切分配置。
- `parseConfig` 为空时由服务层补默认解析配置。
- `indexConfig` 为空时由服务层补默认索引配置。
- 合并后的配置写入 `document.processConfigJson`，作为本次处理快照。

响应对象：

```java
public record UploadDocumentResponse(
        Long documentId,
        String status,
        Integer queuePosition,
        Integer waitingCount
) {
}
```

响应语义：

- 上传成功并入队后立即返回。
- 返回状态通常为 `QUEUED`。
- 后台继续执行解析、切分和索引。

### 6.2 处理状态接口

沿用阶段二接口：

```text
GET /api/documents/{documentId}/process-status
```

增强返回：

- 稳定状态来自 MySQL `document.status`。
- 实时排队位置、等待人数、租约剩余时间来自 Redis。
- 如果 Redis 队列态不存在，只返回稳定状态，不将其视为错误。

### 6.3 重新处理接口

沿用阶段二接口：

```text
POST /api/documents/{documentId}/process
```

行为：

- 允许用户传入新的处理配置。
- 服务层合并默认值后写入 `processConfigJson`。
- 入队前先调用 document/retrieval 清理旧 chunk、向量索引和关键词索引。
- 重新进入 `QUEUED`。

## 7. 配置设计

配置路径：

```yaml
nexa:
  document:
    pipeline:
      mode: local # local / mq
      queue-mode: pipeline
      max-concurrency: 2
      max-retry-count: 3
```

字段说明：

- `mode=local`：使用本地线程池 Worker 执行任务，Redis 保存实时队列态和租约。
- `mode=mq`：未来使用真正 MQ 或 Redis Stream 执行任务，不改变上传接口和 Workflow。
- `queue-mode=pipeline`：初版整条文档流水线只入队一次。
- `queue-mode=stage`：未来按 PARSE、CHUNK、INDEX 阶段拆队列。
- `max-concurrency`：单应用实例最大并发处理文档数。
- `max-retry-count`：默认最大重试次数，写入文档表快照。

## 8. Redis 排队设计

### 8.1 初版队列粒度

初版只排一次，任务粒度为整条文档入库流水线：

```text
document-pipeline:{documentId}
```

一次任务覆盖：

```text
PARSING -> CHUNKING -> INDEXING
```

不在初版为 PARSE、CHUNK、INDEX 分别入队，避免把调度系统复杂度提前引入。

### 8.2 Redis 保存内容

Redis 保存实时态，MySQL 保存稳定态。

Redis 保存：

- 等待队列。
- 执行中租约。
- 排队位置。
- 等待人数。
- Worker 标识。
- 租约过期时间。
- 运行态重试次数副本。

MySQL `document` 保存：

- 文档稳定状态。
- 失败阶段。
- 失败原因。
- 失败详情。
- `retryCount`。
- `maxRetryCount`。
- 处理配置快照。

### 8.3 Redis Key 建议

```text
nexa:document:pipeline:waiting            # ZSET，等待队列，score 为入队时间
nexa:document:pipeline:running            # HASH，documentId -> lease 信息
nexa:document:pipeline:lease:{documentId} # STRING，租约 token，带 TTL
nexa:document:pipeline:retry:{documentId} # STRING，运行态重试次数副本，带 TTL
```

队列位置计算：

- `queuePosition = ZRANK(waiting, documentId) + 1`。
- `waitingCount = ZCARD(waiting)`。

租约规则：

- Worker 获取任务时从 waiting 移除 documentId。
- Worker 写入 running 和 lease key。
- lease TTL 到期表示任务可能被 Worker 异常中断。
- 补偿任务扫描 running 中租约缺失的 documentId，并按 MySQL 稳定状态决定是否重新入队。

### 8.4 未来阶段级队列

预留阶段级队列名称：

```text
nexa:document:stage:parse:waiting
nexa:document:stage:chunk:waiting
nexa:document:stage:index:waiting
```

未来切到 `queue-mode=stage` 时：

- PARSE 阶段完成后投递 CHUNK 队列。
- CHUNK 阶段完成后投递 INDEX 队列。
- 每个阶段可以独立配置并发、限流和重试。
- MySQL 文档状态仍然是稳定状态来源。

## 9. 文档解析设计

### 9.1 解析接口

归属：`nexa-rag-infra`。

```java
public interface DocumentParser {

    boolean supports(DocumentParseRequest request);

    DocumentParseResult parse(DocumentParseRequest request);
}
```

解析请求包含：

- 文档ID。
- 文件类型。
- 原始文件地址。
- 原始文件名。
- 解析配置。

解析结果包含：

```java
public record DocumentParseResult(
        String parsedFileUrl,
        String contentType,
        Map<String, Object> metadata
) {
}
```

### 9.2 MinerU 解析器

`MinerUDocumentParser` 支持：

- `PDF`。
- `WORD`。

处理步骤：

1. 从 `FileStorageService` 读取原始文件。
2. 调用 MinerU `/file_parse`。
3. 使用 ZIP 模式接收 Markdown 和图片。
4. 解压 ZIP，并做路径穿越防护。
5. 上传图片到 MinIO。
6. 替换 Markdown 中图片地址为 MinIO 地址。
7. 可选生成图片描述并写入 Markdown 图片 alt 文本。
8. 上传最终 Markdown 到 MinIO。
9. 返回 `parsedFileUrl`、`contentType=text/markdown` 和解析元数据。

MinIO 路径建议：

```text
original/{documentId}/{fileName}
parsed/{documentId}/{baseName}.md
parsed/{documentId}/images/{imageName}
```

### 9.3 Tika 解析器

`TikaDocumentParser` 支持：

- `PPT`。
- `TEXT`。
- 未来可支持其他 Tika 能稳定抽取文本的格式。

处理步骤：

1. 从存储读取原始文件。
2. 使用 Tika 抽取文本。
3. 保存为 `.txt` 或 `.md` 标准文本文件。
4. 上传到 MinIO。
5. 返回解析后文件地址。

不建议默认把 Excel 交给 Tika 解析成普通文本。Excel/CSV 结构性强，切分阶段应由 `ExcelDocumentSplitter` 专门处理。

### 9.4 Markdown 透传解析器

`PassthroughMarkdownParser` 支持：

- `MARKDOWN`。

处理规则：

- 原始 Markdown 可以直接作为解析后文件使用。
- 也可以复制到 `parsed/{documentId}/` 路径，保证后续统一读取 `parsedFileUrl`。

### 9.5 Excel/CSV 解析阶段

`EXCEL/CSV` 解析阶段只做：

- 文件类型校验。
- 编码和基础结构校验。
- 设置 `parsedFileUrl` 为原文件地址或标准化后的文件地址。

真正的表格结构处理放在切分阶段。

## 10. 文档切分设计

### 10.1 切分上下文

现有 `DocumentSplitter.split(String content, SplitConfigRequest config)` 只适合纯文本，不适合 Excel/CSV 等真实文件。Phase 2.5 建议升级为上下文式接口。

```java
public record DocumentSplitContext(
        Long documentId,
        FileType fileType,
        String originalFileUrl,
        String parsedFileUrl,
        String content,
        byte[] fileBytes,
        SplitConfigRequest config
) {
}
```

新接口：

```java
public interface DocumentSplitter {

    SplitStrategy strategy();

    List<ChunkDraft> split(DocumentSplitContext context);
}
```

### 10.2 ChunkDraft 扩展

现有 `ChunkDraft` 建议扩展为：

```java
public record ChunkDraft(
        String text,
        String chunkId,
        String parentChunkId,
        Integer tokenCount,
        Map<String, Object> metadata,
        boolean skipIndex
) {
}
```

字段说明：

- `chunkId`：片段业务ID。
- `parentChunkId`：父片段ID，用于父子分片检索扩展。
- `tokenCount`：Token 数量，初版可为空，后续由模型模块精确统计。
- `metadata`：标题层级、sheet 名、行号范围、页码、原文件名等元数据。
- `skipIndex`：是否跳过索引，父片段可保存完整上下文但不参与向量索引。

### 10.3 Markdown 父子切分器

`MarkdownParentDocumentSplitter` 借鉴 `know-engine` 的 Markdown 父子切分能力。

职责：

- 按 Markdown 标题层级切分。
- 支持 `titleLevel`、`chunkSize`、`overlap`。
- 保护代码块，避免代码块内标题误判。
- 超出 `chunkSize` 的片段保留完整父片段，并标记 `skipIndex=true`。
- 子片段写入 `parentChunkId`。
- 元数据写入标题层级和标题路径。

### 10.4 Markdown 同级切分器

`MarkdownBrotherDocumentSplitter` 负责同级标题切分。

适用场景：

- 短文档。
- FAQ。
- 不需要父子召回扩展的文档。

### 10.5 正则文本切分器

`RegexTextDocumentSplitter` 支持：

- 正则切分。
- 分隔符切分。
- 普通纯文本按长度切分。

适用文件：

- `TEXT`。
- Tika 解析后的 PPT/TXT 文本。

### 10.6 Excel/CSV 切分器

`ExcelDocumentSplitter` 借鉴 `know-engine` 的 `ExcelSplitter`。

能力：

- 支持 `.xlsx`、`.xls`、`.csv`。
- 支持键值对模式。
- 支持 HTML 表格模式。
- CSV 支持 BOM 和编码识别。
- 按 `chunkSize` 分块。
- 保证同一行不会被拆到不同片段。
- 元数据写入 sheet 名、行号范围、表头、模式。

Excel/CSV 切分输入优先读取 `originalFileUrl` 对应字节。如果未来解析阶段产生标准化文件，则可读取 `parsedFileUrl`。

### 10.7 默认策略

默认策略由服务层统一补齐：

- `MARKDOWN`：`PARENT_MARKDOWN`。
- `PDF/WORD`：解析后为 Markdown，默认 `PARENT_MARKDOWN`。
- `PPT/TEXT`：默认 `REGEX_TEXT`。
- `EXCEL/CSV`：默认 `EXCEL`。

## 11. Workflow 设计

### 11.1 Graph 节点

```text
DocumentIngestionWorkflow
  -> ParsingNode
  -> ChunkingNode
  -> IndexingNode
```

### 11.2 ParsingNode

职责：

1. 读取 `documentId`。
2. 校验文档状态为 `QUEUED` 或允许重试状态。
3. 推进状态为 `PARSING`。
4. 调用 document 模块解析服务。
5. 解析服务内部选择 infra parser，并保存 `parsedFileUrl`。
6. 成功后推进状态为 `PARSED`。

禁止：

- 直接调用 MinerU/Tika。
- 直接操作 MinIO。
- 直接更新 Mapper。

### 11.3 ChunkingNode

职责：

1. 校验文档状态为 `PARSED`。
2. 推进状态为 `CHUNKING`。
3. 调用 document 模块切分服务。
4. 切分服务读取解析后内容或原始表格文件。
5. 保存 `DocumentChunk`。
6. 成功后推进状态为 `CHUNKED`。

禁止：

- 直接写 `DocumentChunkMapper`。
- 直接构造切分器细节。

### 11.4 IndexingNode

职责：

1. 校验文档状态为 `CHUNKED`。
2. 推进状态为 `INDEXING`。
3. 调用 retrieval 模块索引服务。
4. retrieval 写入向量索引和关键词索引。
5. retrieval 回写 chunk 索引ID和状态。
6. 成功后推进状态为 `INDEXED`。

禁止：

- 直接调用 ES/Milvus SDK。
- 直接生成 embedding。
- 直接更新 chunk mapper。

### 11.5 Workflow 状态

Graph State 至少包含：

- `documentId`。
- `currentStage`。
- `retryCount`。
- `failureStage`。
- `failureReason`。

节点之间不传递完整文档内容，避免大对象进入 Graph State。

## 12. 索引与清理设计

### 12.1 索引写入

`nexa-rag-retrieval` 提供：

```java
public interface DocumentIndexService {

    DocumentIndexResult indexDocument(Long documentId);

    void cleanupDocumentIndex(Long documentId);
}
```

索引写入职责：

- 查询 `PENDING_INDEX` 且 `skipIndex=0` 的 chunk。
- 调用模型模块或 embedding 网关生成向量。
- 写入向量索引。
- 写入关键词索引。
- 回写 `vectorId`、`keywordIndexId` 和 chunk 状态。
- 对 `skipIndex=1` 的父片段标记为 `SKIP_INDEX`。

### 12.2 重处理清理

重新处理前必须清理：

- 当前文档旧 chunk。
- 当前文档旧向量索引。
- 当前文档旧关键词索引。

顺序建议：

1. retrieval 清理索引。
2. document 逻辑删除旧 chunk 或物理删除旧 chunk。
3. document 重置文档处理字段。
4. document 重新入队。

如果索引清理失败，不进入新一轮处理，避免新旧索引混用。

### 12.3 文档删除清理

删除文档时：

- document 先做逻辑删除并记录清理状态。
- 发布资源清理任务。
- 清理任务异步删除 MinIO 原文、解析文件、图片、chunk、向量索引和关键词索引。
- 清理失败记录 `cleanupStatus`、`cleanupRetryCount`、`cleanupFailureReason`。
- 补偿任务扫描清理失败或清理超时的文档。

## 13. 失败处理与重试

统一失败入口：

```java
DocumentService.recordProcessFailure(documentId, failureStage, failureReason, failureDetail)
```

失败阶段：

- `UPLOAD`。
- `PARSING`。
- `CHUNKING`。
- `INDEXING`。
- `CLEANUP`。

规则：

- Workflow 节点捕获异常后调用统一失败入口。
- 未达到 `maxRetryCount` 时重新进入 `QUEUED` 并重新入队。
- 达到 `maxRetryCount` 后进入 `FAILED`。
- 人工重试调用 `retryProcess(documentId)`，重置失败字段并重新入队。
- Redis 运行态重试次数只用于实时调度，最终以 MySQL `retryCount` 为准。

## 14. 补偿任务

补偿任务职责：

- 扫描 Redis running 中租约缺失的任务。
- 扫描 MySQL 中长时间停留在 `QUEUED/PARSING/CHUNKING/INDEXING` 的文档。
- 根据 `retryCount` 和 `maxRetryCount` 决定重新入队或标记失败。
- 扫描 `cleanupStatus=FAILED` 或清理租约过期的文档，重新投递清理任务。

补偿任务不直接执行解析、切分和索引，只恢复队列态或调用清理入口。

## 15. 数据结构增量建议

现有 `document` 表已经具备 Phase 2.5 大部分字段：

- `original_file_url`。
- `parsed_file_url`。
- `queue_stage`。
- `queue_time`。
- `process_start_time`。
- `process_end_time`。
- `process_config_json`。
- `failure_stage`。
- `failure_reason`。
- `failure_detail`。
- `retry_count`。
- `max_retry_count`。
- `cleanup_status`。
- `cleanup_retry_count`。
- `cleanup_failure_reason`。

建议增量字段：

- `content_hash VARCHAR(64)`：用于文件内容去重。
- `parser_type VARCHAR(32)`：记录实际解析器。
- `parsed_content_type VARCHAR(64)`：记录解析后内容类型。

现有 `document_chunk` 表已经具备：

- `chunk_id`。
- `parent_chunk_id`。
- `metadata_json`。
- `skip_index`。
- `vector_id`。
- `keyword_index_id`。

可满足父子切分和索引回写需求。

## 16. 验证策略

### 16.1 单元测试

- 上传 DTO 默认值合并测试。
- FileType 与解析器选择测试。
- MinIO objectName 生成测试。
- MinerU ZIP 解压路径穿越防护测试。
- Markdown 父子切分器测试。
- Excel/CSV 切分器测试。
- Redis 队列位置计算测试。
- Workflow Node 单元测试。
- 索引清理顺序测试。

### 16.2 模块测试

- `mvn -pl nexa-rag-infra -am test`。
- `mvn -pl nexa-rag-document -am test`。
- `mvn -pl nexa-rag-retrieval -am test`。
- `mvn -pl nexa-rag-workflow -am test`。

### 16.3 架构测试

必须继续验证：

- `infra` 不依赖业务模块。
- `document/retrieval/model/chat` 不依赖 `workflow`。
- Controller 不依赖 Mapper。
- `retrieval` 不依赖 `document.mapper` 和 `document.service.impl`。
- `workflow` 不依赖任何 mapper 包。

### 16.4 集成测试

默认测试不连接外部中间件。

集成测试显式开启：

- MinIO 上传、读取、删除冒烟。
- MinerU HTTP 解析冒烟。
- Tika 文本抽取冒烟。
- Redis 队列和租约冒烟。
- Elasticsearch/Milvus 索引写入冒烟。

## 17. 实施分解建议

Phase 2.5 建议拆为多个实施批次，每批都可独立验证：

1. 上传 DTO、默认配置、MinIO 保存、上传即入队返回。
2. Redis 队列态、本地 Worker、租约和排队状态查询。
3. Parser 适配：MinerU、Tika、Markdown 透传。
4. Splitter 适配：Markdown 父子、Markdown 同级、RegexText、Excel/CSV。
5. Retrieval 索引接口和 mock 索引实现。
6. DocumentIngestionWorkflow Graph 和节点测试。
7. 重处理清理、删除资源清理和补偿任务。
8. 真实中间件集成测试和架构边界测试增强。

## 18. 决策结论

Phase 2.5 采用端到端自动文档入库设计。

最终决策如下：

- 上传接口接收 MultipartFile 和嵌套 DTO 配置对象。
- 配置为空时由服务层补默认值。
- 上传成功后立即返回，不阻塞等待解析、切分和索引。
- 上传后自动入队并执行完整流水线。
- Redis 初版只按整条文档处理流水线排队。
- MySQL 文档表保存稳定状态，Redis 保存实时队列态和租约。
- Workflow 流程为 `PARSING -> CHUNKING -> INDEXING`。
- 对外产品语义为 `UPLOAD -> PARSING -> CHUNKING -> INDEXING -> INDEXED`。
- 解析能力在 `infra`，切分和 chunk 落库在 `document`，索引能力在 `retrieval`，流程组合在 `workflow`。
- `workflow` 只组合能力，不实现业务细节。