# Phase 2.5-04 文档切分器专项设计与实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

## 1. 目标

实现 `PARSING -> CHUNKING -> CHUNKED` 阶段的真实文档切分能力，为后续 `INDEXING` 阶段提供稳定的 `document_chunk` 数据。

本批次只做文档领域内的切分与 chunk 落库：

- Markdown 父子切分。
- Markdown 同级切分。
- 正则/分隔符/长度文本切分。
- Excel/CSV 结构化切分。
- `DocumentSplitContext` 上下文式切分接口。
- `ChunkDraft` 扩展为可落库的片段草稿。
- `DocumentChunkService` 批量保存 chunk。
- 本地流水线在解析完成后继续执行切分阶段。

## 2. 设计依据

### 2.1 NexaRAG 总体约定

Phase 2.5 总体流程仍保持：

```text
UPLOAD -> PARSING -> CHUNKING -> INDEXING -> INDEXED
```

MySQL 中文档稳定状态保持更细粒度：

```text
UPLOADED -> QUEUED -> PARSING -> PARSED -> CHUNKING -> CHUNKED -> INDEXING -> INDEXED
```

本批次承接 01、02、03 已完成能力：

- 01 已完成上传与 MinIO 原文保存。
- 02 已完成 Redis/Local 排队和本地 Worker 基础能力。
- 03 已完成 MinerU/Tika/Markdown 透传解析，文档可推进到 `PARSED`。
- 04 负责读取解析产物或原始文件，生成 chunk 并推进到 `CHUNKED`。

### 2.2 know-engine 参考点

参考项目：`E:\Code\Projects\Hollis\LLMentor\know-engine`。

借鉴但不照搬：

- `MarkdownHeaderParentTextSplitter` 的标题栈、代码块保护、父子片段、超长父片段 `skipEmbedding` 思路。
- `ExcelSplitter` 的 Excel/CSV 字节输入、CSV BOM 检测、键值对模式、HTML 表格模式、行级不拆分思路。
- `DocumentProcessServiceImpl.split` 的“下载解析产物 -> 按文件类型选择 splitter -> 转实体 -> 批量保存 -> 推进状态”链路。

不照搬：

- 不引入 LangChain4j `DocumentSplitter/TextSegment` 作为领域模型。
- 不把 MinIO URL 解析和下载逻辑塞进切分器。
- 不在 document 模块直接依赖 know-engine 的事件流、版本模型、权限模型。
- 不使用 `System.out.println`，统一使用中文日志。

## 3. 模块边界

### 3.1 `nexa-rag-document`

负责：

- 定义切分上下文、切分器接口、切分结果模型。
- 实现真实切分器。
- 编排切分阶段领域服务。
- 保存 `document_chunk`。
- 推进文档状态 `PARSED -> CHUNKING -> CHUNKED`。

允许依赖：

- `nexa-rag-infra` 暴露的 `FileStorageService` 抽象。
- Jackson 用于元数据 JSON 序列化。
- FastExcel 用于 Excel/CSV 读取，必要时预留 Commons CSV 作为 CSV fallback。

禁止：

- 直接使用 MinIO SDK。
- 直接调用 MinerU/Tika。
- 直接写 retrieval 索引。
- 在 workflow 模块外泄切分器内部细节。

### 3.2 `nexa-rag-infra`

本批原则上不新增能力，只复用：

- `FileStorageService.load(String objectName)`。

如实现时发现需要更友好的读取抽象，优先在 document 模块新增轻量适配类包装 `FileStorageService`，不要修改 infra 的存储策略接口。

### 3.3 `nexa-rag-workflow`

本批不实现 Graph Node。后续 06 只调用 document 模块提供的切分阶段服务。

## 4. 核心设计

### 4.1 切分阶段调用链

```text
LocalDocumentPipelineExecutor.execute(documentId)
  -> parse(document)
  -> markParsed(document, parseResult)
  -> chunk(documentId)
       -> DocumentChunkingService.chunk(documentId)
            -> DocumentService.getRequiredDocument(documentId)
            -> validate status == PARSED
            -> mark status = CHUNKING
            -> DocumentSplitContextBuilder.build(document)
            -> DocumentSplitterFactory.getRequired(splitStrategy)
            -> DocumentSplitter.split(context)
            -> DocumentChunkService.replaceDocumentChunks(documentId, drafts)
            -> mark status = CHUNKED
```

说明：

- 初版本地流水线完成解析后直接继续切分，保持整条文档流水线只入队一次。
- 后续 Workflow Graph 接入后，`CHUNKING` 节点只调用 `DocumentChunkingService.chunk(documentId)`。
- 如果切分失败，调用现有 `DocumentService.recordProcessFailure(documentId, "CHUNK", ...)`，由 Worker 重试策略决定是否回到 `QUEUED`。

### 4.2 输入来源规则

按文件类型选择内容来源：

| 文件类型 | 切分输入 | 说明 |
| --- | --- | --- |
| `PDF` | `parsedObjectName` Markdown 文本 | MinerU 解析后统一切 Markdown。 |
| `WORD` | `parsedObjectName` Markdown 文本 | MinerU 解析后统一切 Markdown。 |
| `MARKDOWN` | 优先 `parsedObjectName`，否则 `originalObjectName` | Markdown 透传解析后通常有 parsed 产物。 |
| `PPT` | `parsedObjectName` 纯文本 | Tika 解析成普通文本后走正则文本切分。 |
| `TEXT` | 优先 `parsedObjectName`，否则 `originalObjectName` | TXT 透传或 Tika 文本。 |
| `EXCEL` | `originalObjectName` 字节 | 保留表格结构，默认不使用 Tika 转普通文本。 |
| `UNKNOWN` | 优先 `parsedObjectName` 文本 | 有解析产物就走文本兜底，否则失败。 |

### 4.3 配置 DTO 设计

现有 `SplitConfigRequest` 只有三个字段，无法表达标题层级、正则、Excel 模式。本批将其升级为“基础字段 + 嵌套参数对象”的 DTO，保持上传 API 易管理。

```java
public record SplitConfigRequest(
        SplitStrategy splitStrategy,
        Integer chunkSize,
        Integer chunkOverlap,
        MarkdownSplitOptions markdown,
        RegexSplitOptions regex,
        ExcelSplitOptions excel
) {
}
```

新增：

```java
public record MarkdownSplitOptions(
        Integer titleLevel,
        Boolean stripHeaders,
        Boolean preserveCodeBlock,
        Boolean createParentForOversized
) {
}
```

```java
public record RegexSplitOptions(
        String separator,
        String regex,
        Boolean keepSeparator
) {
}
```

```java
public record ExcelSplitOptions(
        ExcelSplitMode mode,
        Boolean firstRowAsHeader,
        String charset,
        Integer maxRowsPerChunk
) {
}
```

新增枚举：

```java
public enum ExcelSplitMode {
    KEY_VALUE,
    HTML_TABLE
}
```

默认值：

| 配置项 | 默认值 |
| --- | --- |
| `chunkSize` | `1000` |
| `chunkOverlap` | `100` |
| `markdown.titleLevel` | `3` |
| `markdown.stripHeaders` | `false` |
| `markdown.preserveCodeBlock` | `true` |
| `markdown.createParentForOversized` | `true` |
| `regex.separator` | `\n\n` |
| `regex.regex` | `null` |
| `regex.keepSeparator` | `false` |
| `excel.mode` | `KEY_VALUE` |
| `excel.firstRowAsHeader` | `true` |
| `excel.charset` | `null`，自动识别 BOM，默认 UTF-8 |
| `excel.maxRowsPerChunk` | `null`，仅按 `chunkSize` 控制 |

默认策略仍由 `ProcessConfigDefaults` 统一补齐：

- `MARKDOWN`：`PARENT_MARKDOWN`。
- `PDF/WORD`：`PARENT_MARKDOWN`。
- `PPT/TEXT`：`REGEX_TEXT`。
- `EXCEL/CSV`：`EXCEL`。

兼容要求：

- 旧请求只传 `splitStrategy/chunkSize/chunkOverlap` 时仍可工作。
- 嵌套对象为空时由 `ProcessConfigDefaults` 补默认对象。
- `chunkOverlap >= chunkSize` 时应判为配置错误。

## 5. 类设计

### 5.1 `DocumentSplitContext`

包：`com.nexarag.document.splitter`

职责：承载一次切分所需的稳定上下文，避免切分器再反查数据库。

```java
public record DocumentSplitContext(
        Long documentId,
        String title,
        String originalFileName,
        FileType fileType,
        String originalObjectName,
        String originalFileUrl,
        String parsedObjectName,
        String parsedFileUrl,
        String parsedContentType,
        String content,
        byte[] fileBytes,
        SplitConfigRequest config
) {
}
```

字段规则：

- 文本型切分器使用 `content`。
- Excel/CSV 切分器使用 `fileBytes`。
- `content` 和 `fileBytes` 至少一个不为空。
- 切分器不得通过 `documentId` 再查询数据库。

### 5.2 `ChunkDraft`

包：`com.nexarag.document.splitter`

职责：表示切分器输出的待保存片段，不直接绑定 MyBatis 实体。

```java
public record ChunkDraft(
        String chunkId,
        String parentChunkId,
        String text,
        Integer tokenCount,
        Map<String, Object> metadata,
        boolean skipIndex
) {
}
```

字段规则：

- `chunkId` 由切分阶段生成，保存到 `document_chunk.chunk_id`。
- `parentChunkId` 仅子片段有值。
- `tokenCount` 初版可为空，后续由 model 模块精确统计。
- `metadata` 必须可被 Jackson 序列化为 JSON。
- `skipIndex=true` 的 chunk 保存为 `ChunkStatus.SKIP_INDEX`，不进入后续索引。

### 5.3 `DocumentSplitter`

包：`com.nexarag.document.splitter`

职责：切分器统一接口。

```java
public interface DocumentSplitter {

    SplitStrategy strategy();

    List<ChunkDraft> split(DocumentSplitContext context);
}
```

迁移要求：

- 删除旧接口 `split(String content, SplitConfigRequest config)`。
- 更新 `DocumentSplitterFactoryTest` 中的测试切分器。
- 真实切分器必须校验所需输入，不满足时抛出 `ServiceException`。

### 5.4 `DocumentSplitContextBuilder`

包：`com.nexarag.document.service`

职责：根据 `Document` 构造切分上下文，并集中处理对象存储读取。

依赖：

- `FileStorageService`。
- `ObjectMapper` 或现有配置读取工具。

核心方法：

```java
public DocumentSplitContext build(Document document);
```

设计规则：

- 读取 `document.processConfigJson` 中的 `splitConfig`。
- 根据文件类型决定读取文本还是字节。
- 文本读取统一使用 UTF-8。
- Excel/CSV 读取原始文件字节。
- 对象名为空、文件读取失败、内容为空时抛 `ServiceException`，错误码使用 `DOCUMENT_PROCESS_CONFIG_INVALID` 或新增更合适的文档处理错误码。
- 不暴露 MinIO SDK，也不解析 URL。

### 5.5 `DocumentChunkIdGenerator`

包：`com.nexarag.document.splitter`

职责：生成 chunk 业务 ID，避免各切分器散落 UUID 逻辑。

建议实现：

```java
public class DocumentChunkIdGenerator {

    public String nextChunkId(Long documentId) {
        return "chunk_" + documentId + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
```

说明：

- 当前项目尚无统一 Snowflake ID 组件，初版使用 UUID。
- 后续如引入统一 ID 服务，只替换此类。

### 5.6 `TextWindowSplitter`

包：`com.nexarag.document.splitter.support`

职责：纯字符窗口切分工具，被 Markdown 和 RegexText 复用。

核心方法：

```java
public List<String> split(String text, int chunkSize, int overlap);
```

规则：

- `chunkSize > 0`。
- `0 <= overlap < chunkSize`。
- 优先在段落边界、换行边界附近断开。
- 找不到自然边界时按字符截断。
- 每个输出片段 trim 后不能为空。

### 5.7 `MarkdownHeadingScanner`

包：`com.nexarag.document.splitter.markdown`

职责：扫描 Markdown 标题，维护标题栈，并保护代码块。

输出内部模型：

```java
record MarkdownSection(
        int level,
        String title,
        List<String> titlePath,
        int startLine,
        int endLine,
        String text
) {
}
```

规则：

- 支持 ATX 标题：`#` 到 `######`。
- 标题必须满足 `#` 后为空格或行尾。
- 代码块内的 `#` 不视为标题。
- 代码块围栏支持 ``` 和 `~~~`。
- `titleLevel` 控制切分到几级标题，超过层级的标题保留在当前 section 内容中。
- 文档开头无标题内容进入 `titlePath=[]` 的前置 section。

### 5.8 `MarkdownParentDocumentSplitter`

包：`com.nexarag.document.splitter.markdown`

策略：`SplitStrategy.PARENT_MARKDOWN`

职责：按 Markdown 标题生成父子片段，适合 PDF/Word/Markdown 的默认切分。

核心规则：

- 先用 `MarkdownHeadingScanner` 得到 section。
- section 文本长度 `<= chunkSize` 时直接生成普通 chunk。
- section 文本长度 `> chunkSize` 且 `createParentForOversized=true` 时：
  - 生成一个完整父 chunk，`skipIndex=true`。
  - 父 chunk 保存完整 section 文本。
  - 使用 `TextWindowSplitter` 切出多个子 chunk。
  - 子 chunk 写入 `parentChunkId`。
- section 文本长度 `> chunkSize` 且 `createParentForOversized=false` 时：
  - 只生成子 chunk，不保存父 chunk。
- 元数据写入：
  - `splitStrategy`。
  - `fileType`。
  - `title`。
  - `titleLevel`。
  - `titlePath`。
  - `startLine`。
  - `endLine`。
  - `parent`。
  - `childIndex`。

### 5.9 `MarkdownBrotherDocumentSplitter`

包：`com.nexarag.document.splitter.markdown`

策略：`SplitStrategy.BROTHER_MARKDOWN`

职责：按同级标题直接生成可索引 chunk，不生成父 chunk。

适用场景：

- 短 Markdown。
- FAQ。
- 不需要父子召回扩展的文档。

核心规则：

- 复用 `MarkdownHeadingScanner`。
- 每个 section 超长时使用 `TextWindowSplitter` 二次切分。
- 所有 chunk 默认 `skipIndex=false`。
- 可在元数据写入 `brotherGroup`，后续 retrieval 可据此做同级扩展。

### 5.10 `RegexTextDocumentSplitter`

包：`com.nexarag.document.splitter.text`

策略：`SplitStrategy.REGEX_TEXT`

职责：处理 TXT/PPT/Tika 解析后的普通文本。

切分优先级：

1. 如果 `regex.regex` 非空，按正则切分。
2. 否则如果 `regex.separator` 非空，按分隔符切分。
3. 否则按 `TextWindowSplitter` 纯长度切分。

规则：

- 多个小段可合并到接近 `chunkSize`。
- 单段超过 `chunkSize` 时使用 `TextWindowSplitter`。
- `keepSeparator=true` 时分隔符保留在上一个片段末尾。
- 元数据写入：`splitStrategy`、`separator`、`regex`、`partIndex`。

### 5.11 `ExcelDocumentSplitter`

包：`com.nexarag.document.splitter.excel`

策略：`SplitStrategy.EXCEL`

职责：处理 `.xlsx`、`.xls`、`.csv` 文件，保持表格结构。

输入：

- 只使用 `context.fileBytes()`。
- 文件类型优先从 `originalFileName` 后缀判断，必要时用魔数兜底。

建议依赖：`cn.idev.excel:fastexcel`。\n\n说明：\n\n- FastExcel 读取 `.xls/.xlsx/.csv`，对外仍转换为 `TableSheet/TableRow` 内部模型。\n- CSV 若遇到复杂兼容问题，允许在 `CsvTableReader` 内预留 Commons CSV fallback，但初版不强制引入。\n- 不让 FastExcel 类型泄漏到 `ExcelDocumentSplitter` 对外契约。

子类/辅助类：

- `ExcelWorkbookReader`：读取 xls/xlsx 为 `TableSheet`。
- `CsvTableReader`：读取 csv 为 `TableSheet`。
- `TableChunkRenderer`：把行渲染为 key-value 或 HTML。
- `TableChunkAccumulator`：按 `chunkSize/maxRowsPerChunk` 聚合行，保证同一行不被拆分。

内部模型：

```java
record TableSheet(String sheetName, List<String> headers, List<TableRow> rows) {
}
```

```java
record TableRow(int rowNumber, List<String> cells) {
}
```

模式：

- `KEY_VALUE`：每行渲染为 `表头：值; 表头：值`，多行合并成 chunk。
- `HTML_TABLE`：每个 chunk 渲染为完整 `<table>`，保留表头和若干行。

规则：

- CSV 支持 UTF-8 BOM、UTF-16 BOM，未指定 charset 时默认 UTF-8。
- 单元格需要清理非法控制字符。
- HTML 模式必须转义 `& < > " '`。
- 同一行不得被拆到两个 chunk。
- 如果单行超过 `chunkSize`，允许单行独立成 chunk。
- 空行可跳过。
- 元数据写入：
  - `splitStrategy`。
  - `mode`。
  - `sheetName`。
  - `startRow`。
  - `endRow`。
  - `headers`。
  - `rowCount`。

### 5.12 `DocumentChunkingService`

包：`com.nexarag.document.service`

职责：文档切分阶段领域服务，对外隐藏切分器选择和 chunk 落库细节。

接口：

```java
public interface DocumentChunkingService {

    int chunk(Long documentId);
}
```

实现：`DocumentChunkingServiceImpl`

依赖：

- `DocumentService`。
- `DocumentSplitContextBuilder`。
- `DocumentSplitterFactory`。
- `DocumentChunkService`。

流程：

1. 查询文档，不存在则失败。
2. 校验状态必须为 `PARSED`，如果已经是 `CHUNKED` 可直接返回现有 chunk 数量。
3. 推进状态为 `CHUNKING`。
4. 构造 `DocumentSplitContext`。
5. 选择切分器并执行切分。
6. 校验切分结果非空。
7. 调用 `DocumentChunkService.replaceDocumentChunks(documentId, drafts)`。
8. 清理失败字段，推进状态为 `CHUNKED`。
9. 返回保存的 chunk 数量。

失败处理：

- 捕获运行时异常。
- 调用 `DocumentService.recordProcessFailure(documentId, "CHUNK", "文档切分失败", exception.getMessage())`。
- 如果记录失败后状态回到 `QUEUED`，继续抛出异常交给 Worker 重试。
- 如果已经达到最大重试次数并变为 `FAILED`，记录错误日志但不吞掉关键信息。

### 5.13 `DocumentChunkService` 扩展

现有接口增加：

```java
List<DocumentChunk> replaceDocumentChunks(Long documentId, List<ChunkDraft> drafts);

long countByDocumentId(Long documentId);
```

规则：

- 初版重处理时可先逻辑删除当前文档旧 chunk，再保存新 chunk。
- 本批不清理向量索引和关键词索引，05/07 处理。
- `chunkOrder` 按 drafts 顺序从 0 开始。
- `skipIndex=true` 时：
  - `skipIndex=1`。
  - `status=SKIP_INDEX`。
- `skipIndex=false` 时：
  - `skipIndex=0`。
  - `status=PENDING_INDEX`。
- `metadata` 序列化到 `metadataJson`。
- `tokenCount` 直接使用 draft 值，初版通常为空。

## 6. 状态机影响

当前 `DocumentStatus` 已支持：

```text
PARSED -> CHUNKING -> CHUNKED
CHUNKING -> QUEUED / FAILED
```

本批不需要新增状态。

需要保证：

- `DocumentChunkingService` 不允许从 `QUEUED/PARSING/UPLOADED` 直接切分。
- `LocalDocumentPipelineExecutor` 内部完成 `PARSED` 后可以继续调用切分服务。
- 后续 Workflow Graph 接入时不需要改切分器，只替换编排入口。

## 7. 数据库影响

`document_chunk` 表已具备本批所需字段：

- `chunk_id`。
- `document_id`。
- `chunk_order`。
- `parent_chunk_id`。
- `text`。
- `metadata_json`。
- `token_count`。
- `status`。
- `skip_index`。
- `vector_id`。
- `keyword_index_id`。

本批不新增表字段。

注意：

- 01 之后如果 `document` 表已通过后续 migration 增加 `original_object_name/parsed_object_name`，本批直接使用。
- 如果当前 schema migration 仍缺对象名字段，实施前必须先补 migration，不能退回 URL 解析对象名。

## 8. 依赖调整

如实现 Excel/CSV 切分，建议新增依赖管理：

父 `pom.xml`：

```xml
<fastexcel.version>1.3.0</fastexcel.version>
```

`dependencyManagement`：

```xml
<dependency>
    <groupId>cn.idev.excel</groupId>
    <artifactId>fastexcel</artifactId>
    <version>${fastexcel.version}</version>
</dependency>
```

`nexa-rag-document/pom.xml`：

```xml
<dependency>
    <groupId>cn.idev.excel</groupId>
    <artifactId>fastexcel</artifactId>
</dependency>
```

CSV fallback 说明：如 FastExcel 的 CSV 读取在引号、换行字段或编码上遇到兼容问题，可在 `CsvTableReader` 内部追加 `org.apache.commons:commons-csv`，但本批优先不额外引入。

单元测试不访问外部服务，也不访问真实 MinIO。

## 9. 测试设计

### 9.1 单元测试

新增或更新：

- `DocumentSplitterFactoryTest`：更新为上下文式接口。
- `MarkdownHeadingScannerTest`：标题栈、代码块内标题保护、无标题前置内容。
- `MarkdownParentDocumentSplitterTest`：普通 section、超长父子片段、`parentChunkId`、`skipIndex`、标题路径元数据。
- `MarkdownBrotherDocumentSplitterTest`：同级切分、不生成父 chunk、超长二次切分。
- `RegexTextDocumentSplitterTest`：separator、regex、纯长度切分、overlap 校验。
- `ExcelDocumentSplitterTest`：CSV key-value、CSV BOM、HTML 转义、行不拆分。
- `DocumentSplitContextBuilderTest`：不同文件类型读取 `parsedObjectName/originalObjectName` 的选择。
- `DocumentChunkServiceImplTest`：draft 转实体、metadata JSON、skipIndex 状态、旧 chunk 替换。
- `DocumentChunkingServiceImplTest`：状态推进、重复 `CHUNKED` 幂等、失败记录。
- `LocalDocumentPipelineExecutorTest`：解析后继续切分并推进到 `CHUNKED`。

### 9.2 回归测试

命令：

```powershell
mvn -pl nexa-rag-document -am test
```

架构边界：

```powershell
mvn -pl nexa-rag-boot -am test -Dtest=ModuleDependencyTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

空白检查：

```powershell
git diff --check
```

### 9.3 不做集成测试的内容

本批单元测试不连接：

- MinIO。
- Redis。
- MySQL。
- MinerU。
- Elasticsearch。
- Milvus。

真实服务冒烟放到 08 集成架构批次。

## 10. 实施计划

### Task 1: 基线验证

- [ ] 检查工作区，只允许存在用户已确认的 `application.yml` 改动和本批改动。

```powershell
git status --short --branch
```

- [ ] 运行 document 模块测试。

```powershell
mvn -pl nexa-rag-document -am test
```

### Task 2: DTO 与核心契约

- [ ] 扩展 `SplitConfigRequest`，新增 `MarkdownSplitOptions`、`RegexSplitOptions`、`ExcelSplitOptions`、`ExcelSplitMode`。
- [ ] 更新 `ProcessConfigDefaults`，补齐嵌套默认值。
- [ ] 新增 `DocumentSplitContext`。
- [ ] 扩展 `ChunkDraft`。
- [ ] 修改 `DocumentSplitter` 为上下文式接口。
- [ ] 更新 `DocumentSplitterFactoryTest`。

### Task 3: 支撑工具

- [ ] 新增 `DocumentChunkIdGenerator`。
- [ ] 新增 `TextWindowSplitter`。
- [ ] 新增 `MarkdownHeadingScanner` 和内部 section 模型。
- [ ] 新增 Excel/CSV 表格内部模型与 reader/renderer/accumulator。

### Task 4: 真实切分器

- [ ] 实现 `MarkdownParentDocumentSplitter`。
- [ ] 实现 `MarkdownBrotherDocumentSplitter`。
- [ ] 实现 `RegexTextDocumentSplitter`。
- [ ] 实现 `ExcelDocumentSplitter`。

### Task 5: chunk 落库服务

- [ ] 扩展 `DocumentChunkService`。
- [ ] 实现 `replaceDocumentChunks`。
- [ ] 保证 metadata 序列化失败时抛出业务异常。
- [ ] 保证 skipIndex/status 映射正确。

### Task 6: 切分阶段编排

- [ ] 新增 `DocumentSplitContextBuilder`。
- [ ] 新增 `DocumentChunkingService` 与实现。
- [ ] 更新 `LocalDocumentPipelineExecutor`，解析成功后继续执行切分。
- [ ] 补齐失败阶段 `CHUNK` 的记录与重试抛出逻辑。

### Task 7: 验证

- [ ] 运行指定单元测试。

```powershell
mvn -pl nexa-rag-document -am test -Dtest=MarkdownHeadingScannerTest,MarkdownParentDocumentSplitterTest,MarkdownBrotherDocumentSplitterTest,RegexTextDocumentSplitterTest,ExcelDocumentSplitterTest,DocumentSplitContextBuilderTest,DocumentChunkServiceImplTest,DocumentChunkingServiceImplTest,LocalDocumentPipelineExecutorTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

- [ ] 运行 document 模块全量测试。

```powershell
mvn -pl nexa-rag-document -am test
```

- [ ] 运行架构边界测试。

```powershell
mvn -pl nexa-rag-boot -am test -Dtest=ModuleDependencyTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

- [ ] 检查空白问题。

```powershell
git diff --check
```

## 11. 编码规范

实施时必须遵守：

- 新增类必须有简体中文 JavaDoc。
- 关键 public 方法必须有简体中文 JavaDoc。
- 关键步骤使用编号注释。
- 日志使用简体中文。
- 日志不得输出完整文档内容、文件字节、敏感配置。
- 单元测试优先验证真实业务结果，不只验证方法调用。
- 不修改当前批次无关文件。
- 不回滚用户已有改动，尤其是 `application.yml` 中的 Redis 密码。

## 12. 非目标

本批暂不实现：

- 精确 Token 统计。
- 文档权限元数据。
- 文档版本模型。
- 向量索引写入。
- 关键词索引写入。
- 旧向量索引/关键词索引清理。
- Workflow Graph 节点。
- Redis 阶段级队列。
- 前端 chunk 预览页。

上述内容已在后续 05、06、07、08 或 TODO 中预留。

## 13. 验收标准

- PDF/Word/Markdown 解析产物可切为 Markdown chunk。
- TXT/PPT 解析文本可按正则/分隔符/长度切为 chunk。
- CSV 可切为 key-value chunk。
- Excel 可切为结构化 chunk。
- 超长 Markdown section 可保存父 chunk，并让子 chunk 引用 `parentChunkId`。
- `skipIndex=true` 的父 chunk 状态为 `SKIP_INDEX`。
- 普通 chunk 状态为 `PENDING_INDEX`。
- 文档成功后状态推进到 `CHUNKED`。
- 切分失败可记录失败阶段 `CHUNK`，并复用现有重试机制。
- 单元测试通过，架构边界测试通过，`git diff --check` 无输出。
