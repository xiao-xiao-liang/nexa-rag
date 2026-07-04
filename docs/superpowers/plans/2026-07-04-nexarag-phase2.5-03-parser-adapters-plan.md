# Phase 2.5-03 解析器适配 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `nexa-rag-infra` 中实现基于文件类型固定路由的真实文档解析能力，并在 `nexa-rag-document` 中完成 `QUEUED -> PARSING -> PARSED` 解析阶段闭环。

**Architecture:** Parser 能力沉淀在 `infra.parser`，采用统一 `DocumentParser` 抽象、按 `FileType` 分派的 `DocumentParseService`、以及 MinerU/Tika/Passthrough 三类 Adapter。MinerU 客户端预留本地部署和官方 API Key 服务两种模式；`document` 只负责状态推进、配置快照读取和 `parsedFileUrl` 回写，不感知 MinerU/Tika HTTP 细节；`workflow` 本批不参与。

**Tech Stack:** Java 21、Spring Boot 3.5.x、Maven 多模块、MinIO、Apache Tika、JUnit 5、AssertJ、Mockito、MyBatis-Plus。

---

## 1. Scope Check

本批包含：

- 重构 `infra.parser` 的 Parser 抽象、解析请求和解析结果。
- 去掉 `ParserType` 驱动，改为 `FileType` 固定选择解析方式。
- 新增 MinerU 解析器，支持 PDF、Word，并兼容本地服务和官方服务两种客户端模式。
- 新增 Tika 解析器，支持 PPT、TEXT。
- 新增 Passthrough 解析器，支持 Markdown、Excel 不解析但统一返回解析结果。
- 新增 ZIP 解压路径穿越防护、Markdown 图片地址重写、解析产物对象名生成。
- 在 `document` 模块补回 `PARSED`、`CHUNKED` 状态，并实现解析阶段执行器。
- 解析成功后回写 `document.parsed_file_url`，并把状态推进到 `PARSED`。
- 解析失败时记录 `failureStage=PARSE`，状态进入 `FAILED`。

本批不包含：

- Excel 解析。Excel 在本批走 Passthrough，后续 04 Splitter 使用 FastExcel 做结构化切分。
- 图片描述模型真实调用。本批只保留 `enableImageDescription` 配置和 metadata 位置。
- Workflow Graph 编排。本批仍由本地 Worker 调用 `DocumentPipelineExecutor`。
- 切分、chunk 落库、索引写入、重处理清理。
- 官方 MinerU 服务真实联调。若官方接口文档尚未落定，本批只实现可配置客户端边界、API Key 校验和禁日志泄露，真实冒烟测试在拿到官方接口后补。

## 2. 已确认设计决策

解析方式由文件类型固定决定：

```text
PDF / WORD        -> MinerUDocumentParser
PPT / TEXT        -> TikaDocumentParser
MARKDOWN / EXCEL  -> PassthroughDocumentParser
UNKNOWN           -> 解析失败
```

状态机使用：

```text
UPLOADED -> QUEUED -> PARSING -> PARSED -> CHUNKING -> CHUNKED -> INDEXING -> INDEXED -> FAILED
```

其他约束：

- 删除 `ParserType`，`ParseConfigRequest` 只保留 `enableOcr`、`enableImageDescription`。
- Excel 不用 Tika 拉平成普通文本，避免丢失 sheet、row、cell、header 等结构。
- MinIO、MinerU 默认在 `127.0.0.1`；其他中间件默认在 `192.168.0.134`。
- 默认单元测试不连接外部服务；真实 MinerU 冒烟测试必须显式开启。
- 所有新增类、关键方法、关键步骤注释和日志使用简体中文。

## 3. File Structure

修改文件：

- `nexa-rag-infra/pom.xml`：新增 Tika 依赖；如使用 Spring `RestClient`，新增 `org.springframework:spring-web`，否则使用 JDK `HttpClient`。
- `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/DocumentParser.java`：`supports(String fileType)` 改为 `supports(DocumentParseRequest request)`。
- `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/DocumentParseRequest.java`：删除 `InputStream` 主入口，改为 MinIO objectName + 配置上下文。
- `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/DocumentParseResult.java`：新增 `parsedObjectName`、`metadata`。
- `nexa-rag-infra/src/main/java/com/nexarag/infra/storage/ObjectNameResolver.java`：新增 parsed 主文件和 asset 对象名生成。
- `nexa-rag-document/src/main/java/com/nexarag/document/dto/ParseConfigRequest.java`：删除 `ParserType parserType`。
- `nexa-rag-document/src/main/java/com/nexarag/document/service/ProcessConfigDefaults.java`：删除 ParserType 默认值，只补齐 OCR/图片描述。
- `nexa-rag-document/src/main/java/com/nexarag/document/enums/DocumentStatus.java`：补齐 `PARSED`、`CHUNKED`。
- `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/NoopDocumentPipelineExecutor.java`：删除或替换为真实解析阶段执行器。
- `nexa-rag-boot/src/main/resources/application.yml`：编码阶段如需追加 `nexa.parser` 配置，必须先保护用户当前改动。

删除文件：

- `nexa-rag-infra/src/main/java/com/nexarag/infra/enums/ParserType.java`

新增 infra 文件：

- `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/DocumentParseService.java`
- `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/DocumentParseServiceImpl.java`
- `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/ParsedContentTypes.java`
- `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/ParserFileTypes.java`
- `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/passthrough/PassthroughDocumentParser.java`
- `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/tika/TikaDocumentParser.java`
- `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MinerUDocumentParser.java`
- `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MinerUClient.java`
- `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MinerUClientMode.java`
- `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MinerUParseCommand.java`
- `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MinerUParseResponse.java`
- `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/LocalMinerUClient.java`
- `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/OfficialMinerUClient.java`
- `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MinerUProperties.java`
- `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MinerUZipResultExtractor.java`
- `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MinerUExtractedResult.java`
- `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MinerUAssetFile.java`
- `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MarkdownImageUrlRewriter.java`

新增 document 文件：

- `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/LocalDocumentPipelineExecutor.java`

新增/修改测试：

- `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/DocumentParseServiceImplTest.java`
- `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/passthrough/PassthroughDocumentParserTest.java`
- `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/tika/TikaDocumentParserTest.java`
- `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/MinerUDocumentParserTest.java`
- `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/MinerUZipResultExtractorTest.java`
- `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/MarkdownImageUrlRewriterTest.java`
- `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/MinerUPropertiesTest.java`
- `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/MinerUClientConfigurationTest.java`
- `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/LocalDocumentPipelineExecutorTest.java`
- `nexa-rag-document/src/test/java/com/nexarag/document/service/ProcessConfigDefaultsTest.java`
- `nexa-rag-boot/src/test/java/com/nexarag/boot/NexaRagApplicationConfigurationTest.java`

## 4. 目标类签名

`DocumentParser`：

```java
/**
 * 文档解析器接口，定义不同文件类型解析适配器需要实现的统一能力。
 */
public interface DocumentParser {

    /**
     * 判断当前解析器是否支持本次解析请求。
     *
     * @param request 文档解析请求
     * @return true 表示支持，false 表示不支持
     */
    boolean supports(DocumentParseRequest request);

    /**
     * 解析文档并返回解析产物信息。
     *
     * @param request 文档解析请求
     * @return 文档解析结果
     */
    DocumentParseResult parse(DocumentParseRequest request);
}
```

`DocumentParseRequest`：

```java
/**
 * 文档解析请求，承载解析器选择和读取原始文件所需的上下文。
 */
@Builder
public record DocumentParseRequest(Long documentId,
                                   String originalFileName,
                                   String fileType,
                                   String originalObjectName,
                                   String originalFileUrl,
                                   Boolean enableOcr,
                                   Boolean enableImageDescription) {
}
```

`DocumentParseResult`：

```java
/**
 * 文档解析结果，描述解析后的标准产物及解析元数据。
 */
@Builder
public record DocumentParseResult(String contentType,
                                  String content,
                                  String parsedObjectName,
                                  String parsedFileUrl,
                                  Map<String, Object> metadata) {
}
```

`MinerUProperties`：

```java
/**
 * MinerU 解析器配置属性，支持本地部署和官方服务两种调用模式。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "nexa.parser.mineru")
public class MinerUProperties {

    /** 是否启用 MinerU 解析器。 */
    private boolean enabled = true;

    /** MinerU 客户端模式，LOCAL 表示本地服务，OFFICIAL 表示官方服务。 */
    private MinerUClientMode mode = MinerUClientMode.LOCAL;

    /** 本地 MinerU 服务地址。 */
    private String localEndpoint = "http://127.0.0.1:8000";

    /** 本地 MinerU 文件解析路径。 */
    private String localParsePath = "/file_parse";

    /** 官方 MinerU 服务地址。 */
    private String officialEndpoint = "";

    /** 官方 MinerU API Key，只允许通过环境变量或外部配置注入。 */
    private String apiKey = "";

    /** 连接超时时间。 */
    private Duration connectTimeout = Duration.ofSeconds(3);

    /** 读取超时时间。 */
    private Duration readTimeout = Duration.ofSeconds(120);

    /** 官方异步任务轮询间隔。 */
    private Duration pollInterval = Duration.ofSeconds(2);

    /** 官方异步任务最大轮询次数。 */
    private int maxPollCount = 60;
}
```

## 5. Task 1: 基线验证和用户改动保护

**Files:**

- Read: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/DocumentParser.java`
- Read: `nexa-rag-document/src/main/java/com/nexarag/document/enums/DocumentStatus.java`
- Read: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/NoopDocumentPipelineExecutor.java`

- [ ] **Step 1: 检查工作区**

Run:

```powershell
git status --short --branch
```

Expected: 只允许出现用户已确认的 `nexa-rag-boot/src/main/resources/application.yml` 修改，或本计划执行产生的相关文件。若出现未知文件，暂停确认。

- [ ] **Step 2: 运行现有相关模块测试**

Run:

```powershell
mvn -pl nexa-rag-infra,nexa-rag-document,nexa-rag-boot -am test
```

Expected: `BUILD SUCCESS`。失败时先定位现有失败，不进入实现。

- [ ] **Step 3: 搜索 ParserType 引用范围**

Run:

```powershell
rg -n "ParserType|parserType" nexa-rag-infra nexa-rag-document nexa-rag-boot
```

Expected: 只出现在 `ParserType.java`、`ParseConfigRequest.java`、`ProcessConfigDefaults.java` 和相关测试中。

## 6. Task 2: 重构 Parser 契约和解析产物对象名

**Files:**

- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/DocumentParser.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/DocumentParseRequest.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/DocumentParseResult.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/ParsedContentTypes.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/ParserFileTypes.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/storage/ObjectNameResolver.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/storage/ObjectNameResolverTest.java`

- [ ] **Step 1: 先写失败测试**

在 `ObjectNameResolverTest` 新增：

```java
@Test
void resolveParsedObjectNameShouldUseDocumentDirectoryAndSafeExtension() {
    ObjectNameResolver resolver = new ObjectNameResolver();

    String objectName = resolver.resolveParsedObjectName(1001L, "合同.pdf", ".md");

    assertThat(objectName).isEqualTo("parsed/1001/content.md");
}

@Test
void resolveParsedAssetObjectNameShouldRemoveUnsafePath() {
    ObjectNameResolver resolver = new ObjectNameResolver();

    String objectName = resolver.resolveParsedAssetObjectName(1001L, "..\\images/图 1.PNG");

    assertThat(objectName).startsWith("parsed/1001/assets/");
    assertThat(objectName).endsWith(".png");
    assertThat(objectName).doesNotContain("..", "\\", " ");
}
```

Run:

```powershell
mvn -pl nexa-rag-infra -am test -Dtest=ObjectNameResolverTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 因新增方法不存在而失败。

- [ ] **Step 2: 修改 Parser 契约**

按第 4 节签名更新 `DocumentParser`、`DocumentParseRequest`、`DocumentParseResult`。

Create `ParsedContentTypes.java`：

```java
/**
 * 解析产物内容类型常量，避免解析器之间重复硬编码 MIME 类型。
 */
public final class ParsedContentTypes {

    public static final String TEXT_MARKDOWN = "text/markdown";
    public static final String TEXT_PLAIN = "text/plain";
    public static final String EXCEL = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String OCTET_STREAM = "application/octet-stream";

    private ParsedContentTypes() {
    }
}
```

Create `ParserFileTypes.java`：

```java
/**
 * 解析器使用的文件类型常量，保持 infra 不依赖 document 模块的 FileType 枚举。
 */
public final class ParserFileTypes {

    public static final String PDF = "PDF";
    public static final String WORD = "WORD";
    public static final String EXCEL = "EXCEL";
    public static final String PPT = "PPT";
    public static final String MARKDOWN = "MARKDOWN";
    public static final String TEXT = "TEXT";
    public static final String UNKNOWN = "UNKNOWN";

    private ParserFileTypes() {
    }
}
```

- [ ] **Step 3: 实现解析产物对象名**

`ObjectNameResolver` 新增：

```java
public String resolveParsedObjectName(Long documentId, String originalFileName, String extension)
public String resolveParsedAssetObjectName(Long documentId, String assetFileName)
```

要求：

- `documentId` 为空时抛 `ServiceException("文档ID不能为空")`。
- 主解析产物路径固定为 `parsed/{documentId}/content.{ext}`。
- asset 路径固定为 `parsed/{documentId}/assets/{uuid}.{ext}`。
- 扩展名自动小写，只允许 `[a-z0-9.]`。
- 不把原文件名主体放进对象路径，避免中文、空格、路径穿越片段进入对象名。

- [ ] **Step 4: 验证契约测试**

Run:

```powershell
mvn -pl nexa-rag-infra -am test -Dtest=ObjectNameResolverTest,DocumentParseResultTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `BUILD SUCCESS`。

## 7. Task 3: 删除 ParserType 并调整默认解析配置

**Files:**

- Delete: `nexa-rag-infra/src/main/java/com/nexarag/infra/enums/ParserType.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/dto/ParseConfigRequest.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/ProcessConfigDefaults.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/service/ProcessConfigDefaultsTest.java`

- [ ] **Step 1: 写默认配置测试**

新增或更新测试：

```java
@Test
void mergeShouldEnableOcrForPdfAndWordOnly() {
    ProcessConfigDefaults defaults = new ProcessConfigDefaults();

    ProcessDocumentRequest pdfConfig = defaults.merge(FileType.PDF, emptyUploadRequest());
    ProcessDocumentRequest wordConfig = defaults.merge(FileType.WORD, emptyUploadRequest());
    ProcessDocumentRequest pptConfig = defaults.merge(FileType.PPT, emptyUploadRequest());

    assertThat(pdfConfig.parseConfig().enableOcr()).isTrue();
    assertThat(wordConfig.parseConfig().enableOcr()).isTrue();
    assertThat(pptConfig.parseConfig().enableOcr()).isFalse();
}

@Test
void mergeShouldKeepImageDescriptionDisabledByDefault() {
    ProcessConfigDefaults defaults = new ProcessConfigDefaults();

    ProcessDocumentRequest config = defaults.merge(FileType.PDF, emptyUploadRequest());

    assertThat(config.parseConfig().enableImageDescription()).isFalse();
}
```

Run:

```powershell
mvn -pl nexa-rag-document -am test -Dtest=ProcessConfigDefaultsTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 因 `ParseConfigRequest` 构造参数仍包含 `ParserType` 或测试尚未适配而失败。

- [ ] **Step 2: 修改 ParseConfigRequest**

目标签名：

```java
/**
 * 文档解析配置请求，描述本次文档处理的解析附加能力。
 *
 * @param enableOcr 是否启用 OCR
 * @param enableImageDescription 是否启用图片描述
 */
public record ParseConfigRequest(Boolean enableOcr,
                                 Boolean enableImageDescription) {
}
```

- [ ] **Step 3: 修改 ProcessConfigDefaults**

默认策略：

```java
private ParseConfigRequest defaultParseConfig(FileType fileType) {
    return switch (fileType) {
        case PDF, WORD -> new ParseConfigRequest(true, false);
        case EXCEL, PPT, MARKDOWN, TEXT, UNKNOWN -> new ParseConfigRequest(false, false);
    };
}
```

- [ ] **Step 4: 删除 ParserType 并确认无引用**

Run:

```powershell
git rm nexa-rag-infra/src/main/java/com/nexarag/infra/enums/ParserType.java
rg -n "ParserType|parserType" nexa-rag-infra nexa-rag-document nexa-rag-boot
```

Expected: `rg` 无输出。

- [ ] **Step 5: 验证默认配置测试**

Run:

```powershell
mvn -pl nexa-rag-document -am test -Dtest=ProcessConfigDefaultsTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `BUILD SUCCESS`。

## 8. Task 4: 实现 DocumentParseService 文件类型分派

**Files:**

- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/DocumentParseService.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/DocumentParseServiceImpl.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/DocumentParseServiceImplTest.java`

- [ ] **Step 1: 写失败测试**

测试点：

- 给定 `PDF` 请求，只选择支持 `PDF` 的 parser。
- 给定 `UNKNOWN` 请求，没有 parser 支持时抛 `ServiceException`，消息包含 `未找到可用文档解析器`。
- 测试断言解析结果的 `parsedFileUrl`，不要只 verify 方法调用。

Run:

```powershell
mvn -pl nexa-rag-infra -am test -Dtest=DocumentParseServiceImplTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 因 `DocumentParseServiceImpl` 不存在而失败。

- [ ] **Step 2: 实现 DocumentParseServiceImpl**

要求：

- 使用 `@Service`、`@RequiredArgsConstructor`、`@Slf4j`。
- 构造器注入 `List<DocumentParser>`。
- 校验 `request`、`documentId`、`fileType`、`originalObjectName`。
- 使用 `supports(request)` 找第一个 parser。
- 日志只记录 `documentId`、`fileType`、`parserClass`，不记录文件内容。

核心代码形态：

```java
@Override
public DocumentParseResult parse(DocumentParseRequest request) {
    // 1. 校验解析请求
    validateRequest(request);

    // 2. 根据文件类型选择解析器
    DocumentParser parser = documentParsers.stream()
            .filter(documentParser -> documentParser.supports(request))
            .findFirst()
            .orElseThrow(() -> new ServiceException("未找到可用文档解析器，documentId="
                    + request.documentId() + "，fileType=" + request.fileType()));

    // 3. 执行解析并返回产物信息
    log.info("开始执行文档解析，documentId={}，fileType={}，parserClass={}",
            request.documentId(), request.fileType(), parser.getClass().getSimpleName());
    return parser.parse(request);
}
```

- [ ] **Step 3: 验证分派测试**

Run:

```powershell
mvn -pl nexa-rag-infra -am test -Dtest=DocumentParseServiceImplTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `BUILD SUCCESS`。

## 9. Task 5: 实现 PassthroughDocumentParser

**Files:**

- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/passthrough/PassthroughDocumentParser.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/passthrough/PassthroughDocumentParserTest.java`

- [ ] **Step 1: 写失败测试**

测试点：

- `MARKDOWN` 和 `EXCEL` 返回 `supports=true`。
- `PDF` 返回 `supports=false`。
- Markdown 解析结果直接返回原始 `objectName` 和 `originalFileUrl`。
- Excel 解析结果直接返回原始 `objectName` 和 `originalFileUrl`，metadata 包含 `passthrough=true`。

Run:

```powershell
mvn -pl nexa-rag-infra -am test -Dtest=PassthroughDocumentParserTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 因 `PassthroughDocumentParser` 不存在而失败。

- [ ] **Step 2: 实现 PassthroughDocumentParser**

要求：

- 使用 `@Component`。
- 使用 `@ConditionalOnProperty(prefix = "nexa.parser.passthrough", name = "enabled", havingValue = "true", matchIfMissing = true)`。
- 不读取 MinIO，不复制文件。
- `MARKDOWN` 返回 `ParsedContentTypes.TEXT_MARKDOWN`。
- `EXCEL` 返回 `ParsedContentTypes.EXCEL`，后续 04 Splitter 再判断 xlsx/xls/csv。
- metadata 包含 `parser=passthrough`、`passthrough=true`、`originalFileName`。

- [ ] **Step 3: 验证透传测试**

Run:

```powershell
mvn -pl nexa-rag-infra -am test -Dtest=PassthroughDocumentParserTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `BUILD SUCCESS`。

## 10. Task 6: 实现 MinerU ZIP 解压和 Markdown 图片地址重写

**Files:**

- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MinerUZipResultExtractor.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MinerUExtractedResult.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MinerUAssetFile.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MarkdownImageUrlRewriter.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/MinerUZipResultExtractorTest.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/MarkdownImageUrlRewriterTest.java`

- [ ] **Step 1: 写 ZIP 解压失败测试和成功测试**

测试点：

- ZIP entry 为 `../evil.txt` 时抛 `ServiceException`，消息包含 `非法路径`。
- ZIP 不包含 `.md` 时抛 `ServiceException`，消息包含 `未找到 Markdown`。
- ZIP 包含 `content.md` 和 `images/a.png` 时，能读出 markdown 内容和 1 个 asset。
- 相对路径统一使用 `/`，测试 Windows 反斜杠 entry。

Run:

```powershell
mvn -pl nexa-rag-infra -am test -Dtest=MinerUZipResultExtractorTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 因类不存在而失败。

- [ ] **Step 2: 写 Markdown 图片重写测试**

测试点：

- `![图](images/a.png)` 替换为 MinIO URL。
- 未知图片路径保持原样。
- 外部 URL 如 `https://example.com/a.png` 保持原样。

Run:

```powershell
mvn -pl nexa-rag-infra -am test -Dtest=MarkdownImageUrlRewriterTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 因类不存在而失败。

- [ ] **Step 3: 实现 ZIP 解压和图片重写**

要求：

- `MinerUZipResultExtractor.extract(InputStream zipInputStream)` 使用 `ZipInputStream`。
- 每个 entry 替换 `\` 为 `/`。
- 拒绝包含 `..`、以 `/` 开头、包含 Windows 盘符的路径。
- Markdown 主文件选择第一个 `.md`；如果多个，优先文件名包含 `content`、`result`、`main` 的文件。
- 图片支持 `.png`、`.jpg`、`.jpeg`、`.webp`、`.gif`。
- 不把图片内容打印到日志。
- `MarkdownImageUrlRewriter` 只处理 Markdown 图片语法 `![alt](url)`，HTML `<img>` 后续扩展。

- [ ] **Step 4: 验证 ZIP 和重写测试**

Run:

```powershell
mvn -pl nexa-rag-infra -am test -Dtest=MinerUZipResultExtractorTest,MarkdownImageUrlRewriterTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `BUILD SUCCESS`。

## 11. Task 7: 实现 MinerU 客户端双模式边界

**Files:**

- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MinerUClient.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MinerUClientMode.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MinerUParseCommand.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MinerUParseResponse.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MinerUProperties.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/LocalMinerUClient.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/OfficialMinerUClient.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/MinerUPropertiesTest.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/MinerUClientConfigurationTest.java`

- [ ] **Step 1: 写配置默认值测试**

测试点：

- 默认 `enabled=true`。
- 默认 `mode=LOCAL`。
- 默认 `localEndpoint=http://127.0.0.1:8000`。
- 默认 `localParsePath=/file_parse`。
- 默认连接超时 3 秒，读取超时 120 秒。

Run:

```powershell
mvn -pl nexa-rag-infra -am test -Dtest=MinerUPropertiesTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 因配置类不存在而失败。

- [ ] **Step 2: 写官方模式 API Key 校验测试**

测试点：

- `mode=OFFICIAL` 且 `apiKey` 为空时，Spring Context 启动失败。
- 失败消息包含 `MinerU 官方服务 API Key 不能为空`。
- 测试中不要写真实 API Key。

Run:

```powershell
mvn -pl nexa-rag-infra -am test -Dtest=MinerUClientConfigurationTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 因类不存在而失败。

- [ ] **Step 3: 实现配置类和客户端接口**

要求：

- `MinerUClientMode` 只有 `LOCAL`、`OFFICIAL`。
- `MinerUClient` 暴露 `MinerUParseResponse parse(MinerUParseCommand command)`。
- `MinerUParseCommand` 字段：`documentId`、`fileName`、`InputStream inputStream`、`boolean enableOcr`。
- `MinerUParseResponse` 字段：`InputStream zipInputStream`、`Map<String, Object> metadata`。
- `LocalMinerUClient` 使用条件装配：`mode=LOCAL`，`matchIfMissing=true`。
- `OfficialMinerUClient` 使用条件装配：`mode=OFFICIAL`。
- `OfficialMinerUClient` 构造时校验 `apiKey`，日志不得输出 apiKey。
- 若官方 API 合同尚未明确，`OfficialMinerUClient.parse` 必须抛清晰异常 `MinerU 官方服务接口尚未配置完成`，不能假成功。
- `LocalMinerUClient` 调用 `${localEndpoint}${localParsePath}`。请求字段名如与真实 MinerU 不一致，先用集成测试校正。

- [ ] **Step 4: 验证 MinerU 配置测试**

Run:

```powershell
mvn -pl nexa-rag-infra -am test -Dtest=MinerUPropertiesTest,MinerUClientConfigurationTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `BUILD SUCCESS`。

## 12. Task 8: 实现 MinerUDocumentParser

**Files:**

- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MinerUDocumentParser.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/MinerUDocumentParserTest.java`

- [ ] **Step 1: 写失败测试**

测试点：

- `PDF`、`WORD` 返回 `supports=true`。
- `PPT`、`TEXT`、`EXCEL` 返回 `supports=false`。
- 给定 fake MinerU ZIP，能上传图片 asset 和最终 `content.md`。
- 返回 `contentType=text/markdown`、`parsedObjectName=parsed/{documentId}/content.md`。

Run:

```powershell
mvn -pl nexa-rag-infra -am test -Dtest=MinerUDocumentParserTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 因 `MinerUDocumentParser` 不存在而失败。

- [ ] **Step 2: 先解决 parsed 文件保存能力**

当前 `FileStorageService.save(fileName, inputStream, size)` 会生成 original 路径，不能保存到指定 parsed objectName。编码前必须选择并实现以下方案：

- 推荐方案：扩展 `FileStorageService` 和 `FileStorageStrategy`，新增 `saveAs(String objectName, InputStream inputStream, long size, String contentType)`。
- 不推荐方案：Parser 直接依赖 MinIO SDK 保存 parsed 文件，这会绕过 storage 抽象。

推荐方案需要补测试：

- `FileStorageServiceImplTest.saveAsShouldDelegateToConfiguredStrategy`
- `MinioFileStorageStrategyTest.saveAsShouldUseSpecifiedObjectName`，如果 MinIO 真实测试成本高，可用 mock MinioClient 或先覆盖委派层。

- [ ] **Step 3: 实现 MinerUDocumentParser**

关键步骤注释必须按如下语义组织：

```java
// 1. 校验解析请求
// 2. 从对象存储读取原始文件
// 3. 调用 MinerU 客户端生成 ZIP 解析产物
// 4. 解压 ZIP 并提取 Markdown 与图片资源
// 5. 保存图片资源并生成资源 URL 映射
// 6. 重写 Markdown 图片地址
// 7. 保存最终 Markdown 解析产物
// 8. 组装解析结果
```

metadata 至少包含：`parser=mineru`、`mode`、`assetCount`、`enableOcr`、`enableImageDescription`。

- [ ] **Step 4: 验证 MinerU parser 测试**

Run:

```powershell
mvn -pl nexa-rag-infra -am test -Dtest=MinerUDocumentParserTest,MinerUZipResultExtractorTest,MarkdownImageUrlRewriterTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `BUILD SUCCESS`。

## 13. Task 9: 实现 TikaDocumentParser

**Files:**

- Modify: `nexa-rag-infra/pom.xml`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/tika/TikaDocumentParser.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/tika/TikaDocumentParserTest.java`

- [ ] **Step 1: 增加 Tika 依赖**

在 `nexa-rag-infra/pom.xml` 添加：

```xml
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>${tika.version}</version>
</dependency>
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parsers-standard-package</artifactId>
    <version>${tika.version}</version>
</dependency>
```

不在本批添加 FastExcel；FastExcel 属于 04 Splitter。

- [ ] **Step 2: 写失败测试**

测试点：

- `PPT`、`TEXT` 返回 `supports=true`。
- `EXCEL` 返回 `supports=false`。
- TEXT 输入能抽取文本并保存 `parsed/{documentId}/content.txt`。
- Tika 抽取结果为空时抛 `ServiceException`，消息包含 `Tika解析结果为空`。

Run:

```powershell
mvn -pl nexa-rag-infra -am test -Dtest=TikaDocumentParserTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 因类不存在或依赖未引入而失败。

- [ ] **Step 3: 实现 TikaDocumentParser**

要求：

- 使用 `@Component` 和条件装配 `nexa.parser.tika.enabled=true`。
- 使用 Tika `AutoDetectParser` 或 `Tika` 抽取文本。
- 抽取结果 trim 后为空时失败，不生成空 parsed 文件。
- 保存解析产物为 `parsed/{documentId}/content.txt`。
- metadata 包含 `parser=tika`、`originalFileName`、`textLength`。
- 明确不支持 Excel。

- [ ] **Step 4: 验证 Tika 测试**

Run:

```powershell
mvn -pl nexa-rag-infra -am test -Dtest=TikaDocumentParserTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `BUILD SUCCESS`。

## 14. Task 10: 实现解析阶段 DocumentPipelineExecutor

**Files:**

- Modify/Delete: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/NoopDocumentPipelineExecutor.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/LocalDocumentPipelineExecutor.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/enums/DocumentStatus.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/LocalDocumentPipelineExecutorTest.java`

- [ ] **Step 1: 写解析成功失败测试**

测试点：

- `execute(documentId)` 查询到 `QUEUED` 文档后，先进入 `PARSING`。
- 调用 `DocumentParseService.parse`。
- 成功后回写 `parsedFileUrl`，状态进入 `PARSED`。
- `failureStage`、`failureReason` 清空或保持为空。

Run:

```powershell
mvn -pl nexa-rag-document -am test -Dtest=LocalDocumentPipelineExecutorTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 因 `LocalDocumentPipelineExecutor` 或 `PARSED` 不存在而失败。

- [ ] **Step 2: 写解析失败测试**

测试点：

- `DocumentParseService` 抛异常时，文档状态进入 `FAILED`。
- `failureStage=PARSE`。
- `failureReason` 包含解析失败原因。
- 原始异常需要保留到日志，但日志不能输出完整文档内容。

- [ ] **Step 3: 补齐 DocumentStatus**

`DocumentStatus.java` 必须包含：

```java
PARSED,
CHUNKED,
```

如已有状态流转测试，一并更新。

- [ ] **Step 4: 实现 LocalDocumentPipelineExecutor**

要求：

- 使用 `@Service`、`@RequiredArgsConstructor`、`@Slf4j`。
- 若保留 `NoopDocumentPipelineExecutor` 会产生多个 `DocumentPipelineExecutor` bean，必须删除 Noop 或用条件装配避免冲突。推荐删除 Noop。
- 关键步骤注释：

```java
// 1. 查询并校验文档记录
// 2. 标记文档进入解析中状态
// 3. 读取处理配置快照中的解析配置
// 4. 构造文档解析请求
// 5. 调用基础设施解析服务
// 6. 回写解析产物并标记解析完成
// 7. 解析失败时记录失败状态和失败原因
```

- 从 `processConfigJson` 读取 `parseConfig.enableOcr`、`parseConfig.enableImageDescription`。
- `originalObjectName` 必须来自稳定字段，不应通过 URL 字符串猜测。

Important checkpoint:

当前 `Document` 实体中只有 `originalFileUrl`，未看到 `originalObjectName` 字段。执行编码前必须确认是否新增数据库字段：

```text
original_object_name VARCHAR(512)
parsed_object_name VARCHAR(512)
parsed_content_type VARCHAR(64)
```

推荐新增这些字段，否则 Parser 读取 MinIO 只能依赖 URL 反解，后续换存储策略会出问题。若本批不做 DB migration，应暂停确认替代方案。

- [ ] **Step 5: 验证 executor 测试**

Run:

```powershell
mvn -pl nexa-rag-document -am test -Dtest=LocalDocumentPipelineExecutorTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `BUILD SUCCESS`。

## 15. Task 11: Boot 配置和条件装配测试

**Files:**

- Modify: `nexa-rag-boot/src/main/resources/application.yml`
- Modify: `nexa-rag-boot/src/test/java/com/nexarag/boot/NexaRagApplicationConfigurationTest.java`

Important:

当前用户正在修改 `application.yml`。执行本任务前必须重新运行：

```powershell
git status --short --branch
git diff -- nexa-rag-boot/src/main/resources/application.yml
```

如果 `application.yml` 仍有用户未提交改动，先让用户确认如何合并配置，不能直接覆盖。

- [ ] **Step 1: 写配置绑定测试**

测试点：

- `MinerUProperties` 默认绑定为 `LOCAL`。
- 默认本地 endpoint 为 `http://127.0.0.1:8000`。
- `tika.enabled`、`passthrough.enabled` 默认为 true。

Run:

```powershell
mvn -pl nexa-rag-boot -am test -Dtest=NexaRagApplicationConfigurationTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 因配置尚未补齐或属性类尚未装配而失败。

- [ ] **Step 2: 合并默认配置**

在不覆盖用户改动的前提下追加：

```yaml
nexa:
  parser:
    mineru:
      enabled: true
      mode: ${NEXA_MINERU_MODE:LOCAL}
      local-endpoint: ${NEXA_MINERU_LOCAL_ENDPOINT:http://127.0.0.1:8000}
      local-parse-path: ${NEXA_MINERU_LOCAL_PARSE_PATH:/file_parse}
      official-endpoint: ${NEXA_MINERU_OFFICIAL_ENDPOINT:}
      api-key: ${NEXA_MINERU_API_KEY:}
      connect-timeout: ${NEXA_MINERU_CONNECT_TIMEOUT:3s}
      read-timeout: ${NEXA_MINERU_READ_TIMEOUT:120s}
      poll-interval: ${NEXA_MINERU_POLL_INTERVAL:2s}
      max-poll-count: ${NEXA_MINERU_MAX_POLL_COUNT:60}
    tika:
      enabled: true
    passthrough:
      enabled: true
```

- [ ] **Step 3: 验证配置测试**

Run:

```powershell
mvn -pl nexa-rag-boot -am test -Dtest=NexaRagApplicationConfigurationTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `BUILD SUCCESS`。

## 16. Task 12: 默认跳过的 MinerU 集成测试

**Files:**

- Create: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/MinerUDocumentParserIntegrationTest.java`
- Create: `nexa-rag-infra/src/test/resources/parser/sample.pdf`

- [ ] **Step 1: 编写默认跳过的集成测试**

要求：

- 使用 `@EnabledIfSystemProperty(named = "nexa.parser.integration.enabled", matches = "true")`。
- 默认不连接 MinerU。
- 使用小 PDF 样本。
- 不需要官方 API Key。
- 成功时断言返回 `parsedFileUrl`、`contentType=text/markdown`。

- [ ] **Step 2: 验证默认跳过**

Run:

```powershell
mvn -pl nexa-rag-infra -am test -Dtest=MinerUDocumentParserIntegrationTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `BUILD SUCCESS`，测试 skipped。

- [ ] **Step 3: 本地 MinerU 已启动时运行真实冒烟**

Run:

```powershell
mvn -pl nexa-rag-infra -am test -Dtest=MinerUDocumentParserIntegrationTest "-Dsurefire.failIfNoSpecifiedTests=false" "-Dnexa.parser.integration.enabled=true" "-Dnexa.parser.mineru.local-endpoint=http://127.0.0.1:8000"
```

Expected: `BUILD SUCCESS`。如果 MinerU 未启动，记录为未执行，不可声称真实 MinerU 链路通过。

## 17. Task 13: 全量回归和提交

**Files:**

- All files modified by this plan.

- [ ] **Step 1: 运行最小相关回归**

Run:

```powershell
mvn -pl nexa-rag-infra,nexa-rag-document,nexa-rag-boot -am test
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 2: 运行架构边界测试**

Run:

```powershell
mvn -pl nexa-rag-boot -am test -Dtest=ModuleDependencyTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `BUILD SUCCESS`。确认 `infra` 未依赖 `document`，`document` 未依赖 `workflow`。

- [ ] **Step 3: 检查敏感信息和禁用方案残留**

Run:

```powershell
rg -n "NEXA_MINERU_API_KEY=.*[^}]|api-key: [^$]|ParserType|parserType|RPermit|Redisson" .
```

Expected: 不出现真实 API Key，不出现 `ParserType` 代码引用，不出现 Redisson/RPermit。

- [ ] **Step 4: 检查空白问题**

Run:

```powershell
git diff --check
```

Expected: no output，或仅有仓库既有 LF/CRLF 提示且无 whitespace error。

- [ ] **Step 5: 使用 git-commit-workflow 提交**

Commit message:

```text
feat(document): 接入文档解析器适配
```

提交前必须显式排除无关用户改动。若 `application.yml` 同时包含用户改动和本批 parser 配置，需要用户确认后再 stage 对应 hunk。

## 18. Self-Review Checklist

- [ ] `ParserType` 已删除，所有解析选择由文件类型决定。
- [ ] `PDF/WORD` 只走 MinerU。
- [ ] `PPT/TEXT` 只走 Tika。
- [ ] `MARKDOWN/EXCEL` 只走 Passthrough。
- [ ] Excel 没有被 Tika 解析。
- [ ] MinerU 设计兼容 `LOCAL` 和 `OFFICIAL` 两种模式。
- [ ] API Key 只通过环境变量或外部配置注入，代码和日志不输出真实密钥。
- [ ] ZIP 解压有路径穿越防护测试。
- [ ] Markdown 图片 URL 重写有测试。
- [ ] `PARSED` 和 `CHUNKED` 状态已补齐。
- [ ] 解析成功能进入 `PARSED`。
- [ ] 解析失败能进入 `FAILED` 并记录 `failureStage=PARSE`。
- [ ] `workflow` 模块没有参与本批实现。
- [ ] 所有新增类有简体中文 JavaDoc。
- [ ] 关键方法有简体中文 JavaDoc。
- [ ] 关键步骤注释使用 `1.`、`2.`、`3.` 形式。

## 19. 执行前需要用户确认的问题

以下问题会影响编码实现，执行计划前必须确认：

1. 当前 `document` 表是否允许新增 `original_object_name`、`parsed_object_name`、`parsed_content_type` 字段？推荐新增，否则 Parser 读取 MinIO 会缺少稳定 objectName。
2. MinerU 本地服务 `/file_parse` 的请求字段名和响应 ZIP 格式是否与计划假设一致？如果不一致，先用真实接口样例校正 `LocalMinerUClient`。
3. 官方 MinerU API 是否已有正式接口文档？如果没有，本批只保留官方客户端边界和 API Key 校验，不做假成功实现。
4. `application.yml` 当前有用户改动，编码阶段如需追加 `nexa.parser` 配置，需要先和用户合并确认。

## 20. 执行选项

Plan complete and saved to `docs/superpowers/plans/2026-07-04-nexarag-phase2.5-03-parser-adapters-plan.md`. Two execution options:

1. Subagent-Driven (recommended) - 每个任务派发 fresh subagent，任务间 review，适合本批多类、多测试拆分。
2. Inline Execution - 当前会话按计划执行，适合持续盯设计和实现细节。

Which approach?
