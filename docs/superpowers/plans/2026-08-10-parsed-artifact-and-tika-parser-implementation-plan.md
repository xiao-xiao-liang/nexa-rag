# ParsedArtifact 与框架 Tika Parser Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将解析阶段收敛为以对象存储快照为唯一正文交接物的 `ParsedArtifact`，并用 Spring AI Alibaba Tika Parser 替换项目直接使用的 Apache Tika API。

**Architecture:** 项目级解析器继续负责选择文件类型、保存标准化产物和写入工作流状态；框架解析器只负责 `InputStream` 到 `List<org.springframework.ai.document.Document>`。`ParsedArtifact` 只保留对象键、内容类型和元数据，`ParsingNode` 通过存储服务由对象键解析展示 URL。

**Tech Stack:** Java 21、Spring Boot、Spring AI 1.1.2、Spring AI Alibaba 1.1.2.0、Apache Tika 2.9.4、JUnit 5、AssertJ。

---

### Task 1: 收敛解析产物与对象 URL 解析（已完成）

**Files:**
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/model/ParsedArtifact.java`
- Delete: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/model/DocumentParseResult.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/DocumentParser.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/service/DocumentParseService.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/service/impl/DocumentParseServiceImpl.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MinerUDocumentParser.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/passthrough/PassthroughDocumentParser.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/storage/FileStorageStrategy.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/storage/service/FileStorageService.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/storage/service/FileStorageServiceImpl.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/storage/minio/MinioFileStorageStrategy.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/document/ParsingNode.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/ParsedArtifactTest.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/document/ParsingNodeTest.java`

- [ ] **Step 1: 写入失败测试，固定解析产物不再暴露正文或 URL，且节点从对象键解析 URL。**

```java
ParsedArtifact artifact = ParsedArtifact.builder()
        .objectKey("parsed/1/content.md")
        .contentType(ParsedContentTypes.TEXT_MARKDOWN)
        .metadata(Map.of("parser", "mineru"))
        .build();

when(fileStorageService.resolveUrl("parsed/1001/demo.md"))
        .thenReturn("http://127.0.0.1/parsed/1001/demo.md");
```

- [ ] **Step 2: 运行失败测试，确认失败原因是缺少 `ParsedArtifact`、`resolveUrl` 或新的构造参数。**

Run: `mvn -pl nexa-rag-workflow -am -Dtest=ParsedArtifactTest,ParsingNodeTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: 编译失败，提示缺少新解析产物或 URL 解析 API。

- [ ] **Step 3: 最小实现三字段解析产物及 URL 解析能力。**

```java
@Builder
public record ParsedArtifact(String objectKey, String contentType,
                             Map<String, Object> metadata) {
}

String resolveUrl(String objectName);
```

`FileStorageServiceImpl` 委派至当前策略；`MinioFileStorageStrategy` 复用既有 URL 组装逻辑。所有项目解析器仅返回 `ParsedArtifact`，正文仍只在解析器局部变量中存在。`ParsingNode` 注入 `FileStorageService`，以 `artifact.objectKey()` 回填对象名和 URL。

- [ ] **Step 4: 运行测试，确认产物契约和节点回写均通过。**

Run: `mvn -pl nexa-rag-workflow -am -Dtest=ParsedArtifactTest,ParsingNodeTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

### Task 2: 以 Spring AI Alibaba Tika Parser 生成文本快照（已完成）

**Files:**
- Modify: `nexa-rag-infra/pom.xml`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/tika/TikaDocumentParser.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/tika/TikaDocumentParserTest.java`

- [ ] **Step 1: 写入失败测试，固定框架返回多份 `Document` 时按顺序合并为单个文本快照。**

```java
com.alibaba.cloud.ai.document.DocumentParser parser = inputStream -> List.of(
        new org.springframework.ai.document.Document("第一页"),
        new org.springframework.ai.document.Document("第二页"));

assertThat(storageService.savedContent).isEqualTo("第一页\n\n第二页");
assertThat(artifact.metadata()).containsEntry("parsedDocumentCount", 2);
```

- [ ] **Step 2: 运行失败测试，确认当前直接 Tika 实现无法接受框架 Parser。**

Run: `mvn -pl nexa-rag-infra -Dtest=TikaDocumentParserTest test`

Expected: 编译失败，提示缺少框架 `DocumentParser` 构造参数或依赖。

- [ ] **Step 3: 添加同版本框架依赖并实现适配器。**

```xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter-document-parser-tika</artifactId>
    <version>${spring-ai-alibaba.version}</version>
</dependency>
```

项目 `TikaDocumentParser` 在适配器内创建默认框架 Tika Parser 并调用其 `parse(InputStream)`，按输入顺序以两个换行拼接非空 `Document#getText()`，空结果仍抛出现有业务异常；随后以 UTF-8 保存 `content.txt` 并返回 `ParsedArtifact`。移除项目对 `org.apache.tika.Tika` 的直接使用和冗余直接 Tika 依赖声明。

- [ ] **Step 4: 运行 Tika 单元测试，确认框架输出、快照保存和空文本校验通过。**

Run: `mvn -pl nexa-rag-infra -Dtest=TikaDocumentParserTest test`

Expected: PASS。

### Task 3: 回归测试和设计文档同步（已完成）

**Files:**
- Modify: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/DocumentParseServiceImplTest.java`
- Modify: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/MinerUDocumentParserTest.java`
- Modify: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/MinerUDocumentParserIntegrationTest.java`
- Modify: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/passthrough/PassthroughDocumentParserTest.java`
- Modify: `nexa-rag-infra/src/test/java/com/nexarag/infra/storage/FileStorageServiceImplTest.java`
- Modify: `docs/superpowers/specs/2026-08-10-unified-document-ingestion-design.md`

- [ ] **Step 1: 更新受影响测试，断言保存的对象内容而不是解析结果中的正文。**

```java
assertThat(storageService.savedObjects.get("parsed/1/content.md"))
        .contains("http://127.0.0.1:9000/nexa-rag/parsed/1/assets/");
assertThat(artifact.objectKey()).isEqualTo("parsed/1/content.md");
```

- [ ] **Step 2: 运行模块测试，确认类型迁移没有遗漏。**

Run: `mvn -pl nexa-rag-infra,nexa-rag-workflow -am test`

Expected: PASS；如因工作区既有无关编译错误无法执行，记录首个错误并运行可单独执行的最小测试。

- [ ] **Step 3: 将设计文档状态更新为“单默认知识库，索引版本/配置真实性延后”，并标注本轮解析产物和 Tika 适配已完成。**

- [ ] **Step 4: 执行最终静态检查。**

Run: `git diff --check`

Expected: PASS。
