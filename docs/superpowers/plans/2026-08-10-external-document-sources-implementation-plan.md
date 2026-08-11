# 外部文档来源接入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在统一文档受理入口中支持本地上传、单篇飞书 Docx URL 和单篇语雀 URL，并让两种远端内容在既有 Outbox、解析、切分和双索引流水线中可靠处理。

**Architecture:** `document` 保存来源身份和快照引用；HTTP 层只校验请求形态并创建排队任务。`ParsingNode` 对 `LOCAL` 继续调用文件解析服务，对外部来源委托 infra 的 Source Reader；Reader 把来源响应快照与规范化 Markdown 写入 MinIO，再返回 `ParsedArtifact`，下游无感复用现有 ChunkingNode 与 IndexingNode。

**Tech Stack:** Java 21、Spring Boot、MyBatis-Plus、Flyway、RocketMQ Outbox、MinIO、Spring AI 1.1.2、Spring AI Alibaba 1.1.2.0、`spring-ai-alibaba-starter-document-reader-yuque`、JUnit 5、Mockito、AssertJ。

---

## 文件结构与职责

| 路径 | 职责 |
| --- | --- |
| `nexa-rag-common/.../ExternalDocumentSourceType.java` | 跨 document/infra 使用的来源类型枚举。 |
| `nexa-rag-document/.../ExternalDocumentRequest.java` | 统一入口中外部来源的 URL、标题、描述与处理配置。 |
| `nexa-rag-document/.../DocumentSubmitRequest.java` | 统一受理请求：本地文件或外部 URL 二选一。 |
| `nexa-rag-document/.../DocumentSourceSubmitService.java` | 校验来源形态、创建文档并写 Outbox。 |
| `nexa-rag-infra/.../source/*` | 来源 URL 校验、Reader 路由、来源快照/Markdown 持久化。 |
| `nexa-rag-workflow/.../ParsingNode.java` | 按来源分流并回写来源身份、快照及 `ParsedArtifact`。 |
| `nexa-rag-boot/.../V18__add_document_external_source.sql` | 现有 `document` 表的兼容性迁移。 |

### Task 1: 持久化来源身份、快照引用并纳入删除清理

**Files:**
- Create: `nexa-rag-common/src/main/java/com/nexarag/common/document/ExternalDocumentSourceType.java`
- Create: `nexa-rag-boot/src/main/resources/db/migration/V18__add_document_external_source.sql`
- Modify: `nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/model/entity/Document.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/model/dto/CreateDocumentRequest.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentServiceImpl.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentDeleteTaskServiceImpl.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/messaging/document/model/DocumentStorageCleanupMessage.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/messaging/consumer/RocketMqDocumentStorageCleanupConsumer.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentServiceImplTest.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/messaging/consumer/RocketMqDocumentStorageCleanupConsumerTest.java`

- [ ] **Step 1: 写失败测试，固定外部来源没有原始 MinIO 文件，但必须保存可追溯字段与快照。**

```java
CreateDocumentRequest request = CreateDocumentRequest.external(
        "飞书测试文档", null, "feishu-docx-abc.md", ExternalDocumentSourceType.FEISHU_DOCX,
        "https://example.feishu.cn/docx/abc", "abc");

Document document = documentService.createDocument(request);

assertThat(document.getOriginalObjectName()).isNull();
assertThat(document.getSourceType()).isEqualTo(ExternalDocumentSourceType.FEISHU_DOCX);
assertThat(document.getSourceUrl()).isEqualTo("https://example.feishu.cn/docx/abc");
assertThat(document.getExternalDocumentId()).isEqualTo("abc");
```

- [ ] **Step 2: 运行测试，确认当前创建请求仍强制原始对象名且实体没有来源字段。**

Run: `mvn -pl nexa-rag-document -am -Dtest=DocumentServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，提示缺少 `ExternalDocumentSourceType`、外部创建工厂或来源字段。

- [ ] **Step 3: 最小实现来源字段及迁移。**

新增枚举值 `LOCAL`、`FEISHU_DOCX`、`YUQUE`。迁移和全量 schema 都新增以下可空字段及索引：

```sql
source_type VARCHAR(32) NOT NULL DEFAULT 'LOCAL' COMMENT '文档来源类型',
source_url VARCHAR(1024) NULL COMMENT '外部来源URL',
external_document_id VARCHAR(256) NULL COMMENT '外部文档ID',
external_revision_id VARCHAR(256) NULL COMMENT '外部修订版本ID',
source_snapshot_object_name VARCHAR(1024) NULL COMMENT '外部来源快照对象名',
source_metadata_json TEXT NULL COMMENT '来源追溯元数据',
KEY idx_document_source_external (source_type, external_document_id)
```

`CreateDocumentRequest` 提供显式的 `local(...)` 与 `external(...)` 工厂，只有本地来源要求 `originalObjectName`；`DocumentServiceImpl` 使用请求携带的 `fileType`，外部来源固定为 `MARKDOWN`，并保留 `originalObjectName=null`。存储清理消息和消费者把非空 `sourceSnapshotObjectName` 纳入去重后的删除集合。

- [ ] **Step 4: 运行迁移和单元测试，确认本地兼容与外部来源持久化均正确。**

Run: `mvn -pl nexa-rag-document -am -Dtest=DocumentServiceImplTest,RocketMqDocumentStorageCleanupConsumerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

### Task 2: 统一受理入口按来源类型创建并提交任务

**Files:**
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/model/dto/DocumentSubmitRequest.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentSourceSubmitService.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentSourceSubmitServiceImpl.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/controller/DocumentController.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentUploadServiceImpl.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/ProcessConfigDefaults.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentSourceSubmitServiceImplTest.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/controller/DocumentControllerTest.java`

- [ ] **Step 1: 写失败测试，固定同一受理接口只允许“本地文件”或“外部 URL”之一。**

```java
assertThatThrownBy(() -> submitService.submit(
        new DocumentSubmitRequest(ExternalDocumentSourceType.YUQUE, null, null, null,
                "https://www.yuque.com/team/doc", null, null, null), null))
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("外部文档URL不能为空");

assertThat(submitService.submit(new DocumentSubmitRequest(
        ExternalDocumentSourceType.FEISHU_DOCX, "标题", null, null,
        "https://tenant.feishu.cn/docx/abc", null, null, null), null).status())
        .isEqualTo(DocumentStatus.QUEUED);
```

- [ ] **Step 2: 运行测试，确认当前控制器只有文件上传和仅建档接口。**

Run: `mvn -pl nexa-rag-document -am -Dtest=DocumentSourceSubmitServiceImplTest,DocumentControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，提示缺少统一提交 DTO、服务或控制器映射。

- [ ] **Step 3: 最小实现统一入口。**

保留现有 `/api/documents/upload` 的兼容行为，但改为委托 `DocumentSourceSubmitService`。新增同路径的 JSON 受理方法 `POST /api/documents/submit`：

```java
public record DocumentSubmitRequest(
        @NotNull ExternalDocumentSourceType sourceType,
        @Size(max = 256) String title,
        @Size(max = 1024) String description,
        @Size(max = 1024) String sourceUrl,
        @Valid SplitConfigRequest splitConfig,
        @Valid ParseConfigRequest parseConfig,
        @Valid IndexConfigRequest indexConfig) { }
```

`LOCAL` 只允许 multipart 上传并沿用大小、文件类型校验；`FEISHU_DOCX` 与 `YUQUE` 只允许 JSON URL 请求。服务调用来源 URL 验证器提取外部 ID，使用 `ProcessConfigDefaults.merge(FileType.MARKDOWN, request)`，再调用既有 `DocumentPipelineSubmitService.createAndSubmit`。服务不得在请求线程调用飞书或语雀 API。

- [ ] **Step 4: 运行控制器和服务测试，确认统一受理均写入排队任务。**

Run: `mvn -pl nexa-rag-document -am -Dtest=DocumentSourceSubmitServiceImplTest,DocumentControllerTest,DocumentUploadServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

### Task 3: 定义来源 Reader 契约与快照/Markdown 制品持久化

**Files:**
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/source/model/SourceReadRequest.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/source/model/SourceReadResult.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/source/model/SourceArtifact.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/source/ExternalDocumentSourceReader.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/source/ExternalDocumentSourceService.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/source/impl/ExternalDocumentSourceServiceImpl.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/storage/ObjectNameResolver.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/source/ExternalDocumentSourceServiceImplTest.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/storage/ObjectNameResolverTest.java`

- [ ] **Step 1: 写失败测试，固定 Reader 只返回读取结果，服务统一保存快照及标准 Markdown。**

```java
when(reader.read(request)).thenReturn(new SourceReadResult(
        "{\"blocks\":[]}".getBytes(UTF_8), "application/json", "# 标题", "文档标题",
        "abc", "rev-1", Map.of("platform", "feishu")));

SourceArtifact artifact = service.readAndPersist(request);

assertThat(artifact.parsedArtifact().objectKey()).isEqualTo("parsed/1001/content.md");
assertThat(artifact.sourceSnapshotObjectName()).isEqualTo("source-snapshots/1001/source.json");
verify(fileStorageService).saveAs(eq("source-snapshots/1001/source.json"), any(), anyLong(), eq("application/json"));
```

- [ ] **Step 2: 运行测试，确认尚不存在来源路由与快照存储能力。**

Run: `mvn -pl nexa-rag-infra -Dtest=ExternalDocumentSourceServiceImplTest,ObjectNameResolverTest test`

Expected: 编译失败，提示缺少来源模型、Reader 接口或快照对象名 API。

- [ ] **Step 3: 最小实现来源契约和统一制品存储。**

```java
public interface ExternalDocumentSourceReader {
    boolean supports(ExternalDocumentSourceType sourceType);
    String validateAndExtractDocumentId(String sourceUrl);
    SourceReadResult read(SourceReadRequest request);
}

public interface ExternalDocumentSourceService {
    String validateAndExtractDocumentId(ExternalDocumentSourceType sourceType, String sourceUrl);
    SourceArtifact readAndPersist(SourceReadRequest request);
}
```

`ExternalDocumentSourceServiceImpl` 以 `sourceType` 唯一路由 Reader，拒绝 `LOCAL`；使用 `ObjectNameResolver.resolveSourceSnapshotObjectName(documentId, extension)` 保存源响应，使用既有 `resolveParsedObjectName(documentId, "source.md", ".md")` 保存 UTF-8 Markdown。`SourceArtifact` 包含 `ParsedArtifact`、标题、外部文档 ID、修订号、快照对象键及 metadata JSON；metadata 不得携带 Token、Cookie、Authorization 或 App Secret。

- [ ] **Step 4: 运行来源服务测试，确认空 Reader、重复 Reader 和无 Markdown 均失败。**

Run: `mvn -pl nexa-rag-infra -Dtest=ExternalDocumentSourceServiceImplTest,ObjectNameResolverTest test`

Expected: PASS。

### Task 4: 封装框架语雀单文档 Reader

**Files:**
- Modify: `nexa-rag-infra/pom.xml`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/source/yuque/YuqueSourceProperties.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/source/yuque/YuqueSourceReader.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/source/yuque/YuqueDocumentNormalizer.java`
- Modify: `nexa-rag-boot/src/main/resources/application.yml`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/source/yuque/YuqueSourceReaderTest.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/source/yuque/YuqueDocumentNormalizerTest.java`

- [ ] **Step 1: 写失败测试，固定仅接受单篇语雀 URL，并按框架 Document 顺序合并为 Markdown。**

```java
assertThat(reader.validateAndExtractDocumentId("https://www.yuque.com/team/doc-slug"))
        .isEqualTo("doc-slug");
assertThatThrownBy(() -> reader.validateAndExtractDocumentId("https://www.yuque.com/team"))
        .isInstanceOf(ClientException.class);

assertThat(normalizer.normalize(List.of(new Document("# 第一节"), new Document("第二节"))))
        .isEqualTo("# 第一节\n\n第二节");
```

- [ ] **Step 2: 运行测试，确认当前工程未引入框架语雀 Reader。**

Run: `mvn -pl nexa-rag-infra -Dtest=YuqueSourceReaderTest,YuqueDocumentNormalizerTest test`

Expected: 编译失败，提示缺少 `YuQueDocumentReader` 或项目封装类。

- [ ] **Step 3: 添加独立依赖并实现防腐封装。**

```xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter-document-reader-yuque</artifactId>
    <version>${spring-ai-alibaba.version}</version>
</dependency>
```

`YuqueSourceReader` 只支持 `YUQUE`，读取 server-side `nexa.source.yuque.token`，以经验证的单篇 URL 构造 `YuQueResource` 和 `YuQueDocumentReader`；框架返回的 `List<Document>` 交给 `YuqueDocumentNormalizer` 合并正文和允许的公开 metadata。快照使用 JSONL（每行含文本与脱敏 metadata），规范化产物使用 Markdown。Token 为空时在读取阶段抛出明确的不可配置错误，不写敏感信息到日志。

- [ ] **Step 4: 运行语雀单元测试，确认 URL、Markdown、快照和配置缺失行为。**

Run: `mvn -pl nexa-rag-infra -Dtest=YuqueSourceReaderTest,YuqueDocumentNormalizerTest test`

Expected: PASS。

### Task 5: 实现飞书 Docx 防腐 Reader

**Files:**
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/source/feishu/FeishuSourceProperties.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/source/feishu/FeishuDocxApiClient.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/source/feishu/FeishuDocxSourceReader.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/source/feishu/FeishuBlockMarkdownConverter.java`
- Modify: `nexa-rag-boot/src/main/resources/application.yml`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/source/feishu/FeishuDocxSourceReaderTest.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/source/feishu/FeishuBlockMarkdownConverterTest.java`

- [ ] **Step 1: 写失败测试，固定 Docx URL 校验、分页 block 汇集与未知块的可追溯占位符。**

```java
assertThat(reader.validateAndExtractDocumentId("https://tenant.feishu.cn/docx/abc123"))
        .isEqualTo("abc123");
assertThatThrownBy(() -> reader.validateAndExtractDocumentId("https://tenant.feishu.cn/wiki/abc123"))
        .isInstanceOf(ClientException.class);

assertThat(converter.convert(List.of(heading("标题"), unsupported("sheet"))))
        .isEqualTo("# 标题\n\n[未解析飞书块：sheet]");
```

- [ ] **Step 2: 运行测试，确认当前工程没有飞书 Docx Reader 与 Block 转换器。**

Run: `mvn -pl nexa-rag-infra -Dtest=FeishuDocxSourceReaderTest,FeishuBlockMarkdownConverterTest test`

Expected: 编译失败，提示缺少飞书 Reader、客户端或块转换器。

- [ ] **Step 3: 最小实现应用身份 API 客户端和 Markdown 转换。**

`FeishuDocxApiClient` 使用 Spring `RestClient`：以 `appId`、`appSecret` 请求 tenant access token；读取文档信息与固定 `revisionId`，再以该 revision 分页读取全部 Block。`FeishuDocxSourceReader` 只支持 `FEISHU_DOCX`，将完整响应序列化为 JSON 快照，并按 Block 类型生成 Markdown：标题、段落、无序/有序列表、代码块、引用、分割线和待办项保留文本结构；图片、附件、表格、嵌入对象与未知类型使用 `[未解析飞书块：{blockType}]` 占位。快照 metadata 记录 `externalDocumentId`、`externalRevisionId`、标题和 Block 数。

HTTP 401/403 转换为不可重试的 `ClientException`；429、5xx、连接超时和响应不完整转换为 `ServiceException`，由既有 MQ 消费失败策略重试。禁止记录 `appSecret`、tenant token 或 Authorization header。

- [ ] **Step 4: 运行飞书单元测试，确认 revision 一致性、分页、错误分类和块转换。**

Run: `mvn -pl nexa-rag-infra -Dtest=FeishuDocxSourceReaderTest,FeishuBlockMarkdownConverterTest test`

Expected: PASS。

### Task 6: 在 ParsingNode 分流外部来源并回写来源元数据

**Files:**
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/document/ParsingNode.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentService.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentServiceImpl.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/converter/DocumentConverter.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/model/vo/DocumentDetailVO.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/document/ParsingNodeTest.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentServiceImplTest.java`

- [ ] **Step 1: 写失败测试，固定外部来源只在 Parsing 阶段读取，且回写快照、修订号、标题和解析产物。**

```java
when(externalDocumentSourceService.readAndPersist(sourceRequest)).thenReturn(sourceArtifact);

node.apply(state);

verify(externalDocumentSourceService).readAndPersist(sourceRequest);
verify(documentService).markParsed(eq(documentId), argThat(document ->
        document.getSourceSnapshotObjectName().equals("source-snapshots/1001/source.json")
        && document.getExternalRevisionId().equals("rev-1")
        && document.getParsedObjectName().equals("parsed/1001/content.md")));
verifyNoInteractions(documentParseService);
```

- [ ] **Step 2: 运行测试，确认当前节点无来源分流且强制构建文件解析请求。**

Run: `mvn -pl nexa-rag-workflow -am -Dtest=ParsingNodeTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，提示缺少来源服务或 `markParsed` 的来源回写能力。

- [ ] **Step 3: 最小实现来源分流和原子回写。**

`ParsingNode` 仅当 `sourceType == LOCAL` 时创建 `DocumentParseRequest` 并调用 `DocumentParseService`；其他类型构造 `SourceReadRequest` 调用 `ExternalDocumentSourceService.readAndPersist`。新增 `DocumentService.markParsed(Document document, ParsedArtifact artifact, SourceArtifact sourceArtifact)`，以 `documentId + PARSING` 作为条件一次更新标题（仅请求未指定时）、外部修订号、快照对象名、来源 metadata、解析对象名、解析内容类型、状态和失败信息。详情 VO 返回来源类型、来源 URL、外部文档 ID、外部修订号和快照 URL（快照 URL 仅用于管理端，不用于检索）。

- [ ] **Step 4: 运行节点与 document 服务测试，确认外部来源完全复用后续节点。**

Run: `mvn -pl nexa-rag-workflow,nexa-rag-document -am -Dtest=ParsingNodeTest,DocumentServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

### Task 7: 回归验证、配置说明和设计同步

**Files:**
- Modify: `nexa-rag-boot/src/main/resources/application.yml`
- Modify: `docs/superpowers/specs/2026-08-10-unified-document-ingestion-design.md`
- Modify: `README.md`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/source/ExternalDocumentSourceServiceImplTest.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentSourceSubmitServiceImplTest.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/document/ParsingNodeTest.java`

- [ ] **Step 1: 写集成编排测试，固定两个外部来源在不调用 VectorStore 的前提下到达 `PARSED → CHUNKING` 路由。**

```java
assertThat(node.apply(feishuState)).containsEntry(ROUTE_TARGET, CHUNKING_NODE);
assertThat(node.apply(yuqueState)).containsEntry(ROUTE_TARGET, CHUNKING_NODE);
verifyNoInteractions(vectorStore);
```

- [ ] **Step 2: 运行受影响模块测试。**

Run: `mvn -pl nexa-rag-infra,nexa-rag-document,nexa-rag-workflow -am test`

Expected: PASS；飞书和语雀真实 API 测试必须默认禁用，单元测试使用 MockWebServer 或 `RestClient` mock。

- [ ] **Step 3: 补齐非敏感配置与文档。**

在 `application.yml` 提供 `nexa.source.feishu`（`base-url`、`app-id`、`app-secret`）和 `nexa.source.yuque`（`token`）的开发环境占位配置；README 说明仅支持飞书 Docx 与单篇语雀 URL，解释统一受理方式、异步解析与失败分类。设计文档将本计划对应能力标记为“已实现”仅限所有测试完成后。

- [ ] **Step 4: 执行最终检查。**

Run: `git diff --check; git status --short; mvn -pl nexa-rag-infra,nexa-rag-document,nexa-rag-workflow -am test`

Expected: `git diff --check` 无输出；状态仅包含本次来源接入相关文件；Maven 测试 PASS。

## 自检

- 统一入口、仅单篇飞书 Docx/语雀 URL、异步远端读取、快照与 Markdown 制品、来源追溯、删除清理、失败分类、框架语雀 Reader 和下游链路复用分别由 Task 1–7 覆盖。
- 本计划不引入 Wiki、语雀仓库批量导入、OAuth、Webhook、自动同步、document revision、知识库或索引版本。
- Reader 契约、来源类型和 `SourceArtifact` 的命名在 Task 1、3、4、5、6 中一致；所有新增代码均有对应的失败测试与通过命令。
