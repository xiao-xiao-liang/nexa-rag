# 飞书新版文档单篇导入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 支持以应用身份导入用户提交的单篇飞书新版文档 URL，将其 Block 树转换为可追溯的 Markdown 快照，并复用现有文档切分、索引和失败重试链路。

**Architecture:** 新增 `FEISHU_DOCX` 文档来源，而不新增第二套工作流。导入 API 只创建携带来源标识的 `MARKDOWN` 文档并复用现有 Outbox 投递；既有 `ParsingNode` 根据来源选择飞书解析器，飞书解析器在对象存储落原始 Block JSON 与 Markdown 快照，随后原样进入 `ChunkingNode`、Markdown 父子切分和 `IndexingNode`。

**Tech Stack:** Java 21、Spring Boot 3、Spring AI `DocumentReader`/`Document` 概念契约、飞书开放平台 Docx API、Flyway、MyBatis-Plus、MinIO、RocketMQ、JUnit 5、Mockito。

---

## 已确认边界

- 仅接收新版飞书 `/docx/{token}` URL；Wiki、旧版 `doc`、云盘遍历、OAuth、定时同步、Webhook 不属于本期。
- 使用应用 `tenant_access_token` 读取一篇指定文档；应用仍须具备该文档的实际阅读权限。
- 不直接引入 Spring AI Alibaba 的 `FeiShuDocumentReader`。仅与 Spring AI 的 Reader/Document 概念对齐；飞书 URL、访问令牌、Block 树、版本与快照属于项目的防腐层。
- 首期支持标题、正文、无序/有序列表、待办、引用、代码块、分割线；图片、附件、嵌入表格、同步块、公式等保留明确的占位标记和 Block 元数据，不做 OCR、下载或递归展开。
- 文档更新不自动同步。用户再次提交相同 URL 时创建新的文档处理记录；保留 `revisionId` 只作可追溯信息，后续再决定“同 URL 原地重建”的产品语义。

## 文件结构与职责

| 路径 | 职责 |
| --- | --- |
| `nexa-rag-boot/src/main/resources/db/migration/V18__add_document_source_fields.sql` | 为 `document` 增加来源类型、来源 URL、外部 ID、外部版本字段及来源查询索引。实施时先检查最大 Flyway 版本；若 `V18` 已被占用，使用下一个唯一版本。 |
| `nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql` | 新环境 `document` 表的最终来源字段契约。 |
| `nexa-rag-document/.../enums/DocumentSourceType.java` | 领域来源枚举：`UPLOAD`、`FEISHU_DOCX`。 |
| `nexa-rag-document/.../dto/FeishuDocumentImportRequest.java` | 单篇 URL 导入 REST 请求与处理配置。 |
| `nexa-rag-document/.../service/DocumentFeishuImportService.java` | 创建来源记录、生成初始对象名、提交既有流水线。 |
| `nexa-rag-infra/.../parser/feishu/*` | URL 解析、飞书 API 客户端、Block DTO、Markdown 渲染器及 `DocumentParser` 适配器。 |
| `nexa-rag-infra/.../config/FeishuDocumentProperties.java` | 应用身份和请求超时/重试配置；不得在代码、日志、文档或测试中写入真实凭据。 |
| `nexa-rag-workflow/.../node/document/ParsingNode.java` | 将来源信息传给解析器，同时保持普通上传文件现有路径不变。 |
| `nexa-rag-document/.../controller/DocumentController.java` | 增加飞书单篇导入 API；前端由后续独立计划接入。 |

## Task 1: 固化来源领域与数据库兼容契约

**Files:**

- Create: `nexa-rag-boot/src/main/resources/db/migration/V18__add_document_source_fields.sql`
- Modify: `nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql:88-130`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/enums/DocumentSourceType.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/model/entity/Document.java`
- Create: `nexa-rag-document/src/test/java/com/nexarag/document/DocumentSourceSchemaContractTest.java`

- [ ] **Step 1: 写失败的 SQL 契约测试。**

  ```java
  @Test
  void migrationShouldAddTraceableExternalSourceFields() throws IOException {
      String migration = readMigration("V18__add_document_source_fields.sql");

      assertThat(migration).contains("ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'UPLOAD'");
      assertThat(migration).contains("ADD COLUMN source_url VARCHAR(1024) NULL");
      assertThat(migration).contains("ADD COLUMN external_document_id VARCHAR(128) NULL");
      assertThat(migration).contains("ADD COLUMN external_revision_id VARCHAR(128) NULL");
      assertThat(migration).contains("idx_document_source_external");
  }
  ```

- [ ] **Step 2: 运行测试并确认其因迁移不存在失败。**

  Run: `mvn -pl nexa-rag-document -am -Dtest=DocumentSourceSchemaContractTest test`

  Expected: FAIL，提示迁移文件或预期字段不存在。

- [ ] **Step 3: 添加前滚迁移、schema 与枚举。**

  ```sql
  ALTER TABLE document
      ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'UPLOAD' COMMENT '文档来源类型' AFTER description,
      ADD COLUMN source_url VARCHAR(1024) NULL COMMENT '外部来源URL' AFTER original_file_url,
      ADD COLUMN external_document_id VARCHAR(128) NULL COMMENT '外部文档唯一标识' AFTER source_url,
      ADD COLUMN external_revision_id VARCHAR(128) NULL COMMENT '外部文档版本标识' AFTER external_document_id,
      ADD KEY idx_document_source_external (source_type, external_document_id, del_flag);
  ```

  ```java
  public enum DocumentSourceType {
      UPLOAD,
      FEISHU_DOCX
  }
  ```

  `Document` 新增四个字段；既有上传创建路径显式写入 `UPLOAD`，历史记录依赖数据库默认值。不得改动 `FileType`，飞书解析产物固定为既有 `MARKDOWN`。

- [ ] **Step 4: 运行来源契约与文档服务回归。**

  Run: `mvn -pl nexa-rag-document -am -Dtest=DocumentSourceSchemaContractTest,DocumentServiceImplTest test`

  Expected: PASS；普通上传仍创建 `sourceType=UPLOAD`。

## Task 2: 建立飞书 URL 和应用身份读取防腐层

**Files:**

- Modify: `nexa-rag-infra/pom.xml`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/config/FeishuDocumentProperties.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/feishu/FeishuDocxUrl.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/feishu/FeishuDocxUrlParser.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/feishu/FeishuDocumentClient.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/feishu/FeishuBlock.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/feishu/FeishuDocumentSnapshot.java`
- Create: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/feishu/FeishuDocxUrlParserTest.java`
- Create: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/feishu/FeishuDocumentClientTest.java`

- [ ] **Step 1: 先写 URL 边界测试。**

  ```java
  @ParameterizedTest
  @ValueSource(strings = {
          "https://example.feishu.cn/docx/doxcnAJ9VRRJqVMYZ1MyKnavXWe",
          "https://example.larksuite.com/docx/doxcnAJ9VRRJqVMYZ1MyKnavXWe"
  })
  void parseShouldExtractDocxToken(String url) {
      assertThat(parser.parse(url).documentId()).isEqualTo("doxcnAJ9VRRJqVMYZ1MyKnavXWe");
  }

  @Test
  void parseShouldRejectWikiAndNonFeishuHosts() {
      assertThatThrownBy(() -> parser.parse("https://example.feishu.cn/wiki/wikcnKQ"))
              .hasMessageContaining("仅支持飞书新版文档");
  }
  ```

- [ ] **Step 2: 运行 URL 测试，确认类不存在。**

  Run: `mvn -pl nexa-rag-infra -am -Dtest=FeishuDocxUrlParserTest test`

  Expected: FAIL，提示 `FeishuDocxUrlParser` 不存在。

- [ ] **Step 3: 引入官方 Java SDK 并实现客户端接口。**

  在 `nexa-rag-infra/pom.xml` 加入飞书官方 `com.larksuite.oapi:oapi-sdk`，版本固定为与验证通过的 Spring AI Alibaba 扩展一致的 `2.3.7`。`FeishuDocumentClient` 只暴露一个读取方法：

  ```java
  public interface FeishuDocumentClient {
      FeishuDocumentSnapshot readDocx(String documentId);
  }

  public record FeishuBlock(String blockId, String parentId, String blockType,
                            List<String> children, String text, String language) { }

  public record FeishuDocumentSnapshot(String documentId, String revisionId, String title,
                                       List<FeishuBlock> blocks) { }
  ```

  实现必须使用应用身份，按顺序调用“获取文档基本信息”和分页“获取文档所有块”；`revisionId` 固定传给块读取请求，防止一次导入混入不同版本。将 401/403 映射为不可重试的 `ServiceException`，429/5xx/网络超时抛出可被 RocketMQ 重试的 `ServiceException`；不打印 token、请求头、Block 全文或 SDK 原始响应。

- [ ] **Step 4: 用 SDK mock 覆盖 API 读取语义。**

  ```java
  @Test
  void readDocxShouldUseSameRevisionForEveryBlockPage() {
      when(api.getDocument("doxcnAJ9VRRJqVMYZ1MyKnavXWe"))
              .thenReturn(document("标题", "42"));
      when(api.listBlocks("doxcnAJ9VRRJqVMYZ1MyKnavXWe", "42", null)).thenReturn(firstPage());
      when(api.listBlocks("doxcnAJ9VRRJqVMYZ1MyKnavXWe", "42", "next")).thenReturn(lastPage());

      FeishuDocumentSnapshot snapshot = client.readDocx("doxcnAJ9VRRJqVMYZ1MyKnavXWe");

      assertThat(snapshot.revisionId()).isEqualTo("42");
      assertThat(snapshot.blocks()).hasSize(3);
      verify(api).listBlocks("doxcnAJ9VRRJqVMYZ1MyKnavXWe", "42", "next");
  }
  ```

- [ ] **Step 5: 运行 infra 测试。**

  Run: `mvn -pl nexa-rag-infra -am -Dtest=FeishuDocxUrlParserTest,FeishuDocumentClientTest test`

  Expected: PASS；Wiki URL 被拒绝，多页 Block 使用同一 revision，权限错误不被吞掉。

## Task 3: 将 Block 树规范化为可供现有 Markdown 切分器消费的快照

**Files:**

- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/feishu/FeishuMarkdownRenderer.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/feishu/FeishuDocumentParser.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/model/DocumentParseRequest.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/passthrough/PassthroughDocumentParser.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/storage/ObjectNameResolver.java`
- Create: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/feishu/FeishuMarkdownRendererTest.java`
- Create: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/feishu/FeishuDocumentParserTest.java`

- [ ] **Step 1: 写标题层级、Block 追溯和未知类型降级测试。**

  ```java
  @Test
  void renderShouldProduceMarkdownAndBlockTraceMetadata() {
      FeishuMarkdownDocument rendered = renderer.render(snapshotWithHeadingAndCode());

      assertThat(rendered.markdown()).contains("# 总览", "正文", "```java", "System.out.println(1);");
      assertThat(rendered.metadata()).containsEntry("feishuDocumentId", "doxcnAJ9VRRJqVMYZ1MyKnavXWe")
              .containsEntry("feishuRevisionId", "42");
  }

  @Test
  void renderShouldKeepUnsupportedBlockAsTraceablePlaceholder() {
      assertThat(renderer.render(snapshotWithImage()).markdown())
              .contains("[飞书未解析块: image]");
  }
  ```

- [ ] **Step 2: 运行渲染测试，确认类型尚不存在。**

  Run: `mvn -pl nexa-rag-infra -am -Dtest=FeishuMarkdownRendererTest,FeishuDocumentParserTest test`

  Expected: FAIL，提示 `FeishuMarkdownRenderer` 或 `FeishuDocumentParser` 不存在。

- [ ] **Step 3: 为解析请求增加来源上下文并实现 Parser。**

  `DocumentParseRequest` 增加不依赖 document 模块的字段 `sourceType`、`sourceUrl`、`externalDocumentId`。`FeishuDocumentParser.supports` 只接受 `sourceType="FEISHU_DOCX"`；`PassthroughDocumentParser.supports` 必须额外要求 `sourceType` 为 `null` 或 `UPLOAD`，避免 Spring Bean 注入顺序让飞书来源被错误透传。

  解析步骤固定如下：读取快照、将完整 Block JSON 写入 `source/{documentId}/feishu-blocks.json`、将 Markdown 写入 `parsed/{documentId}/content.md`，返回：

  ```java
  String parsedObjectName = objectNameResolver.resolveParsedObjectName(request.documentId(),
          request.originalFileName(), ".md");
  StoredFile parsedFile = storageService.saveAs(parsedObjectName, markdownInputStream,
          markdownBytes.length, ParsedContentTypes.TEXT_MARKDOWN);
  return DocumentParseResult.builder()
          .contentType(ParsedContentTypes.TEXT_MARKDOWN)
          .parsedObjectName(parsedFile.objectName())
          .parsedFileUrl(parsedFile.url())
          .metadata(Map.of("parser", "feishu", "documentId", snapshot.documentId(),
                  "revisionId", snapshot.revisionId(), "sourceUrl", request.sourceUrl()))
          .build();
  ```

  通过 `ObjectNameResolver.resolveExternalSourceObjectName(documentId, "feishu-blocks", ".json")` 生成原始快照路径；不得覆写用户上传文件路径。对象存储成功写入 Block 快照后才写 Markdown；任一步失败都抛出异常，交由既有消息重试。

- [ ] **Step 4: 运行解析器测试。**

  Run: `mvn -pl nexa-rag-infra -am -Dtest=FeishuMarkdownRendererTest,FeishuDocumentParserTest,DocumentParseServiceImplTest test`

  Expected: PASS；飞书文档生成 `.md` 产物，普通 Markdown 透传行为不变。

## Task 4: 通过既有 ParsingNode 接入来源解析，而不增加第二条 ETL 状态机

**Files:**

- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/document/ParsingNode.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentService.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentServiceImpl.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentPipelineSubmitService.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentPipelineSubmitServiceImpl.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/model/command/CreateExternalDocumentCommand.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/converter/DocumentConverter.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/model/vo/DocumentDetailVO.java`
- Modify: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/document/ParsingNodeTest.java`
- Modify: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentServiceImplTest.java`
- Modify: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentPipelineSubmitServiceImplTest.java`

- [ ] **Step 1: 写飞书来源解析节点测试。**

  ```java
  @Test
  void applyShouldPassFeishuSourceContextToParserAndKeepExistingRoute() {
      Document document = feishuQueuedDocument();
      when(documentService.getRequiredDocument(1001L)).thenReturn(document);
      when(documentParseService.parse(argThat(request ->
              "FEISHU_DOCX".equals(request.sourceType())
                      && request.sourceUrl().equals(document.getSourceUrl()))))
              .thenReturn(markdownResult());

      Map<String, Object> result = node.apply(stateFor(1001L));

      assertThat(result).containsEntry(ROUTE_TARGET, CHUNKING_NODE);
      assertThat(document.getStatus()).isEqualTo(DocumentStatus.PARSED);
  }
  ```

- [ ] **Step 2: 运行工作流测试，确认请求未携带来源字段。**

  Run: `mvn -pl nexa-rag-workflow -am -Dtest=ParsingNodeTest test`

  Expected: FAIL，提示 `sourceType`/`sourceUrl` 不存在或断言不满足。

- [ ] **Step 3: 更新来源字段与解析请求构造。**

  `DocumentServiceImpl.createDocument` 的既有上传分支设置 `UPLOAD`；`DocumentService.createExternalDocument` 只接受显式来源命令，不复用上传 DTO 校验，并固定创建：

  ```java
  public record CreateExternalDocumentCommand(String title, String description, String sourceUrl,
                                              String externalDocumentId, FileType fileType,
                                              DocumentSourceType sourceType) { }

  Document.builder()
          .documentId(IdWorker.getId())
          .title(command.title())
          .originalFileName("feishu-" + command.documentId() + ".md")
          .fileType(FileType.MARKDOWN)
          .sourceType(DocumentSourceType.FEISHU_DOCX)
          .sourceUrl(command.sourceUrl())
          .externalDocumentId(command.documentId())
          .originalObjectName(objectNameResolver.resolveExternalSourceObjectName(
                  documentId, "feishu-blocks", ".json"))
          .status(DocumentStatus.UPLOADED)
          .build();
  ```

  `DocumentPipelineSubmitService` 新增 `createExternalAndSubmit(CreateExternalDocumentCommand, ProcessDocumentRequest)`，在既有 `@Transactional` 事务内执行“创建来源文档 → 进入 QUEUED → 写入 PROCESS_DOCUMENT Outbox”。`ParsingNode.buildParseRequest` 传递 `document.getSourceType().name()`、`getSourceUrl()`、`getExternalDocumentId()`；`markParsed` 仅当 `parseResult.metadata().get("revisionId")` 为非空文本时更新 `externalRevisionId`。节点状态、路由和失败语义保持原样。`DocumentDetailVO` 返回来源字段供后续前端显示，但不返回任何飞书应用凭据。

- [ ] **Step 4: 运行模块回归。**

  Run: `mvn -pl nexa-rag-document,nexa-rag-workflow -am -Dtest=DocumentServiceImplTest,ParsingNodeTest,DocumentChunkingServiceImplTest test`

  Expected: PASS；上传文档与飞书文档都从 `PARSING` 进入既有 `CHUNKING_NODE`。

## Task 5: 提供单篇 URL 导入 API 并将默认切分策略固定为 Markdown 父子切分

**Files:**

- Create: `nexa-rag-document/src/main/java/com/nexarag/document/model/dto/FeishuDocumentImportRequest.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentFeishuImportService.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentFeishuImportServiceImpl.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/controller/DocumentController.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/ProcessConfigDefaults.java`
- Create: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentFeishuImportServiceImplTest.java`
- Modify: `nexa-rag-document/src/test/java/com/nexarag/document/controller/DocumentControllerTest.java`

- [ ] **Step 1: 写导入服务测试。**

  ```java
  @Test
  void importShouldCreateFeishuMarkdownDocumentAndSubmitExistingPipeline() {
      FeishuDocumentImportRequest request = new FeishuDocumentImportRequest(
              "https://example.feishu.cn/docx/doxcnAJ9VRRJqVMYZ1MyKnavXWe", "产品说明", null, null);
      when(urlParser.parse(request.url())).thenReturn(new FeishuDocxUrl(request.url(), "doxcnAJ9VRRJqVMYZ1MyKnavXWe"));

      service.importDocument(request);

      verify(documentPipelineSubmitService).createExternalAndSubmit(argThat(command ->
              command.sourceType() == DocumentSourceType.FEISHU_DOCX
                      && command.fileType() == FileType.MARKDOWN));
      verify(documentPipelineSubmitService).createExternalAndSubmit(any(), argThat(process ->
              process.splitConfig().splitStrategy() == SplitStrategy.PARENT_MARKDOWN));
  }
  ```

- [ ] **Step 2: 运行测试并确认 API 与服务不存在。**

  Run: `mvn -pl nexa-rag-document -am -Dtest=DocumentFeishuImportServiceImplTest,DocumentControllerTest test`

  Expected: FAIL，提示导入请求、服务或 REST 端点不存在。

- [ ] **Step 3: 实现请求校验和 REST 接口。**

  ```java
  public record FeishuDocumentImportRequest(
          @NotBlank @Size(max = 1024) String url,
          @Size(max = 256) String title,
          @Size(max = 1024) String description,
          @Valid SplitConfigRequest splitConfig) { }
  ```

  ```java
  @PostMapping("/imports/feishu")
  public Result<DocumentDetailVO> importFeishuDocument(
          @Valid @RequestBody FeishuDocumentImportRequest request) {
      return Results.success(DocumentConverter.toDetailVO(documentFeishuImportService.importDocument(request)));
  }
  ```

  服务只做 URL 本地校验、创建记录、合并 `ProcessConfigDefaults` 并调用既有 `DocumentPipelineSubmitService`；不得在 HTTP 线程调用飞书 API。未传 `title` 时使用 `feishu-{documentId}.md` 作为初始标题，远程标题仅记录在解析产物元数据中。调用方未提供 `splitConfig` 时固定使用 `PARENT_MARKDOWN`，并继承既有 chunk 大小和 overlap 默认值。

- [ ] **Step 4: 运行接口与默认配置测试。**

  Run: `mvn -pl nexa-rag-document -am -Dtest=DocumentFeishuImportServiceImplTest,DocumentControllerTest,ProcessConfigDefaultsTest test`

  Expected: PASS；非法 URL 在提交前被拒绝，合法 URL 只入队不阻塞等待飞书响应，默认策略为 `PARENT_MARKDOWN`。

## Task 6: 配置、端到端验证与运行文档

**Files:**

- Modify: `nexa-rag-boot/src/main/resources/application.yml`
- Modify: `README.md`
- Create: `docs/operations/feishu-docx-import.md`
- Create: `nexa-rag-boot/src/test/java/com/nexarag/boot/integration/FeishuDocumentIngestionIntegrationTest.java`

- [ ] **Step 1: 添加无真实凭据的配置契约。**

  ```yaml
  nexa:
    document:
      feishu:
        enabled: false
        app-id: ${NEXA_FEISHU_APP_ID:}
        app-secret: ${NEXA_FEISHU_APP_SECRET:}
        connect-timeout-ms: 5000
        read-timeout-ms: 15000
  ```

  `enabled=false` 时，导入 API 返回明确的业务错误；启用时必须校验 appId/appSecret 非空。不要将配置接入模型治理表，也不要将飞书访问令牌放入 `document`、日志、消息体或对象存储。

- [ ] **Step 2: 写集成测试，验证只有既有节点被复用。**

  ```java
  @Test
  void feishuImportShouldReachExistingChunkAndIndexStages() {
      Document document = importThroughApi("https://example.feishu.cn/docx/doxcnAJ9VRRJqVMYZ1MyKnavXWe");
      stubFeishuSnapshotWithHeadings();

      workflowRunner.run(document.getDocumentId());

      assertThat(documentService.getRequiredDocument(document.getDocumentId()).getStatus())
              .isEqualTo(DocumentStatus.INDEXED);
      assertThat(documentChunkService.listByDocumentId(document.getDocumentId()))
              .allSatisfy(chunk -> assertThat(chunk.getMetadataJson()).contains("titlePath"));
  }
  ```

- [ ] **Step 3: 运行分层验证。**

  Run: `mvn -pl nexa-rag-infra,nexa-rag-document,nexa-rag-workflow -am test`

  Run: `mvn -pl nexa-rag-boot -am -Dtest=FeishuDocumentIngestionIntegrationTest test`

  Run: `git diff --check`

  Expected: 所有单元测试通过；集成测试验证飞书 Markdown 进入既有父子切分与索引；`git diff --check` 无空白错误。

- [ ] **Step 4: 写运行文档。**

  `docs/operations/feishu-docx-import.md` 必须说明：支持的 URL 类型、飞书应用需要的文档读取权限、用户需将文档授予应用的前置条件、导入 API、对象存储快照含义、403/429/版本读取失败的处理、重试入口与本期不支持的 Wiki/自动同步。不得写入任何开发或生产凭据。

## 计划自检

| 已确认要求 | 对应任务 |
| --- | --- |
| 应用身份导入用户提交的单篇 URL | Task 2、Task 5 |
| 仅 `/docx/`，不支持 Wiki/OAuth/遍历 | Task 2、Task 5、Task 6 |
| 精确保留 Block 层级并转 Markdown | Task 2、Task 3 |
| 复用 Parsing→Chunking→Indexing 与 MQ 重试 | Task 4、Task 6 |
| 保留原始快照、来源、外部 ID 与版本 | Task 1、Task 3、Task 4 |
| 不直接复用框架 FeiShuDocumentReader 实现 | Task 2 |

未列入本计划：Wiki token 解析、旧版文档、附件/图片下载与 OCR、嵌入表格递归解析、远程文档更新检测、Webhook、同 URL 去重/原地重建，以及前端 URL 导入交互。
