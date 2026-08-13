# 文件化文档解析制品管线 Implementation Plan

> For agentic workers: REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

Goal: 用文件化工作区和流式对象存储发布替代全文内存解析；新 DOCX 经 Pandoc，PDF 继续经 MinerU，飞书官方 DOCX 导出复用同一转换管线。

Architecture: 删除 DocumentArtifactParser。DocumentArtifactService 是应用门面；Handler 将格式路由为透传或转换；唯一注册的 DocumentConverter 只产生工作区文件；ArtifactPublisher 统一发布、改写和补偿。

Tech Stack: Java 21、Spring Boot 3.5、MinIO Java SDK、Apache Commons IO、Pandoc 3.x、MinerU、JUnit 5、AssertJ、Mockito。

执行约束：工作区已有用户未提交变更。执行前重新运行 git status --short；只叠加本计划相关变更，不重置、不暂存、不提交。仓库未授权 commit，以下任务均以测试与 diff 检查收尾。

---

## 文件结构

- 新建 parser/model/DocumentFormat.java、DocumentArtifactRequest.java、StagedDocument.java、ExtractedAsset.java、ExtractedDocument.java：格式、请求和文件化转换结果。
- 新建 parser/workspace/ArtifactWorkspace.java、ArtifactWorkspaceFactory.java、BoundedFileTransfer.java：受管临时目录和按实际字节限制的落盘。
- 新建 parser/converter/DocumentConverter.java、DocumentConverterRegistry.java：格式转换 SPI 与唯一性校验。
- 新建 parser/handler/DocumentArtifactHandler.java、DocumentArtifactHandlerRegistry.java、DirectReferenceArtifactHandler.java、ConvertAndPublishArtifactHandler.java：透传与转换策略。
- 新建 parser/service/DocumentArtifactService.java、DocumentArtifactServiceImpl.java：工作流调用的门面。
- 新建 parser/publish/ArtifactPublisher.java、MarkdownAssetFileRewriter.java：流式资源发布与文件到文件的 URL 改写。
- 新建 parser/pandoc/PandocProperties.java、PandocProcessRunner.java、PandocDocxConverter.java：DOCX 转换。
- 新建 parser/mineru/MinerUPdfConverter.java、parser/mineru/extract/MinerUZipArtifactExtractor.java、parser/tika/TikaDocumentConverter.java。
- 新建 parser/ArtifactProcessingProperties.java、source/model/FileBackedSourceDocument.java、source/feishu/FeishuExportTaskClient.java 及其 task/result DTO。
- 删除旧 DocumentArtifactParser、DocumentParseService、MinerUArtifactParser、MinerUZipResultExtractor、MarkdownImageUrlRewriter、TikaArtifactParser、PassthroughArtifactParser、空壳 FeishuParser 和 FeishuCliDocumentExporter。

## Task 1: 工作区、格式模型与有界传输

Files:
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/model/DocumentFormat.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/ArtifactProcessingProperties.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/workspace/ArtifactWorkspace.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/workspace/ArtifactWorkspaceFactory.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/workspace/BoundedFileTransfer.java
- Test: nexa-rag-infra/src/test/java/com/nexarag/infra/parser/workspace/ArtifactWorkspaceTest.java
- Test: nexa-rag-infra/src/test/java/com/nexarag/infra/parser/workspace/BoundedFileTransferTest.java
- Modify: nexa-rag-boot/src/main/resources/application.yml

- [ ] Step 1: 写失败测试。

~~~java
assertThatThrownBy(() -> transfer.copy(new ByteArrayInputStream(new byte[11]), target, 10L))
        .isInstanceOf(DocumentPipelineNonRetryableException.class);
assertThat(Files.exists(target)).isFalse();
assertThatThrownBy(() -> workspace.resolve("../escape.md"))
        .isInstanceOf(ServiceException.class);
~~~

- [ ] Step 2: 运行失败测试。

Run: mvn -pl nexa-rag-infra -am -Dtest=ArtifactWorkspaceTest,BoundedFileTransferTest -Dsurefire.failIfNoSpecifiedTests=false test

Expected: FAIL，缺少工作区和传输类。

- [ ] Step 3: 实现最小基础设施。

~~~java
public enum DocumentFormat { PDF, WORD, EXCEL, PPT, MARKDOWN, TEXT, UNKNOWN }

public long copy(InputStream source, Path target, long maxBytes) throws IOException {
    // 使用 8 KiB 缓冲逐段复制；每段累加后检查 maxBytes；异常时删除 target。
}
~~~

ArtifactWorkspace 只暴露 root()、受限 resolve(String) 和 close()；删除前验证真实路径仍位于配置的 tempRoot 内。配置绑定 tempRoot、maxWorkspaceBytes、maxConcurrent，不写入本机路径和凭据。

- [ ] Step 4: 运行通过测试。

Run: mvn -pl nexa-rag-infra -am -Dtest=ArtifactWorkspaceTest,BoundedFileTransferTest -Dsurefire.failIfNoSpecifiedTests=false test

Expected: PASS；路径穿越和真实字节超限都被拒绝，工作区关闭后被删除。

## Task 2: Converter SPI、Handler 路由和门面 API

Files:
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/model/DocumentArtifactRequest.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/model/StagedDocument.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/model/ExtractedAsset.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/model/ExtractedDocument.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/converter/DocumentConverter.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/converter/DocumentConverterRegistry.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/handler/DocumentArtifactHandler.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/handler/DocumentArtifactHandlerRegistry.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/service/DocumentArtifactService.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/service/DocumentArtifactServiceImpl.java
- Test: nexa-rag-infra/src/test/java/com/nexarag/infra/parser/converter/DocumentConverterRegistryTest.java
- Test: nexa-rag-infra/src/test/java/com/nexarag/infra/parser/handler/DocumentArtifactHandlerRegistryTest.java
- Test: nexa-rag-infra/src/test/java/com/nexarag/infra/parser/service/DocumentArtifactServiceImplTest.java

- [ ] Step 1: 写失败测试，固定唯一注册与门面路由。

~~~java
assertThatThrownBy(() -> new DocumentConverterRegistry(List.of(
        converter(DocumentFormat.WORD), converter(DocumentFormat.WORD))))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("重复注册文档转换器");

service.process(wordRequest());
verify(convertHandler).handle(wordRequest());
verifyNoInteractions(directHandler);
~~~

- [ ] Step 2: 运行失败测试。

Run: mvn -pl nexa-rag-infra -am -Dtest=DocumentConverterRegistryTest,DocumentArtifactHandlerRegistryTest,DocumentArtifactServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test

Expected: FAIL，缺少 SPI、注册表和门面。

- [ ] Step 3: 实现命名 SPI。

~~~java
public interface DocumentConverter {
    Set<DocumentFormat> supportedFormats();
    ExtractedDocument convert(DocumentArtifactRequest request, Path stagedSource,
                              ArtifactWorkspace workspace);
}

public interface DocumentArtifactHandler {
    Set<DocumentFormat> supportedFormats();
    ParsedArtifact handle(DocumentArtifactRequest request);

    default ParsedArtifact handleStaged(DocumentArtifactRequest request, StagedDocument source) {
        return handle(request);
    }
}
~~~

两个 Registry 使用不可变 EnumMap 并在构造期拒绝重复 format；DocumentArtifactService.process 委派 requiredHandler(request.format()).handle(request)，processStaged(DocumentArtifactRequest, StagedDocument) 委派同一 Handler 的 handleStaged。ConvertAndPublishArtifactHandler 覆盖 handleStaged，用于已在受管工作区中的飞书 DOCX，避免再次从对象存储下载；DirectReferenceArtifactHandler 使用默认实现。StagedDocument 只持有工作区内的常规文件路径与真实大小，不能在工作区关闭后使用。不把核心扩展点做成裸 Function；仅在 ArtifactWorkspace 内部保留私有函数式回调以保证资源关闭。

- [ ] Step 4: 运行通过测试。

Run: mvn -pl nexa-rag-infra -am -Dtest=DocumentConverterRegistryTest,DocumentArtifactHandlerRegistryTest,DocumentArtifactServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test

Expected: PASS；重复注册、未知格式和错误 Handler 路由均失败明确。

## Task 3: 流式发布、资源 URL 重写与安全前缀清理

Files:
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/publish/ArtifactPublisher.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/publish/MarkdownAssetFileRewriter.java
- Modify: nexa-rag-infra/src/main/java/com/nexarag/infra/storage/ObjectNameResolver.java
- Modify: nexa-rag-infra/src/main/java/com/nexarag/infra/storage/FileStorageStrategy.java
- Modify: nexa-rag-infra/src/main/java/com/nexarag/infra/storage/service/FileStorageService.java
- Modify: nexa-rag-infra/src/main/java/com/nexarag/infra/storage/service/FileStorageServiceImpl.java
- Modify: nexa-rag-infra/src/main/java/com/nexarag/infra/storage/minio/MinioFileStorageStrategy.java
- Test: nexa-rag-infra/src/test/java/com/nexarag/infra/parser/publish/MarkdownAssetFileRewriterTest.java
- Test: nexa-rag-infra/src/test/java/com/nexarag/infra/parser/publish/ArtifactPublisherTest.java
- Test: nexa-rag-infra/src/test/java/com/nexarag/infra/storage/FileStorageServiceImplTest.java

- [ ] Step 1: 写失败测试。

~~~java
new MarkdownAssetFileRewriter().rewrite(input, output,
        Map.of("assets/a.png", "https://storage/parsed/1/assets/a.png"));
assertThat(Files.readString(output)).contains("![](https://storage/parsed/1/assets/a.png)");

assertThatThrownBy(() -> publisher.publish(request, extractedDocument)).isInstanceOf(ServiceException.class);
verify(storageService).deleteByPrefix("parsed/1/");
~~~

- [ ] Step 2: 运行失败测试。

Run: mvn -pl nexa-rag-infra -am -Dtest=MarkdownAssetFileRewriterTest,ArtifactPublisherTest,FileStorageServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test

Expected: FAIL，缺少发布器、文件重写器和前缀 API。

- [ ] Step 3: 实现发布和删除。

ArtifactPublisher 先以 Files.size / Files.newInputStream 上传资源，再把相对路径映射为 StoredFile.url()，用文件级重写器生成第二个 Markdown 文件，最后上传 parsed/{documentId}/content.md。任一步失败，调用 deleteByPrefix(resolveParsedPrefix(documentId)) 后重新抛出。禁止 Files.readString、readAllBytes 和整文正则。

~~~java
String resolveParsedPrefix(Long documentId) { return "parsed/" + documentId + "/"; }
void deleteByPrefix(String objectPrefix);
~~~

MinIO 使用 ListObjectsArgs.prefix(...).recursive(true) 枚举后逐项删除；禁止桶级删除和未校验前缀。

- [ ] Step 4: 运行通过测试。

Run: mvn -pl nexa-rag-infra -am -Dtest=MarkdownAssetFileRewriterTest,ArtifactPublisherTest,FileStorageServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test

Expected: PASS；普通链接不变，图片链接重写，上传失败清理当前文档专属前缀。

## Task 4: 实现 Pandoc DOCX Converter

Files:
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/pandoc/PandocProperties.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/pandoc/PandocProcessRunner.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/pandoc/PandocDocxConverter.java
- Test: nexa-rag-infra/src/test/java/com/nexarag/infra/parser/pandoc/PandocProcessRunnerTest.java
- Test: nexa-rag-infra/src/test/java/com/nexarag/infra/parser/pandoc/PandocDocxConverterTest.java
- Modify: nexa-rag-boot/src/main/resources/application.yml

- [ ] Step 1: 写失败测试。

~~~java
assertThat(converter.supportedFormats()).containsExactly(DocumentFormat.WORD);
assertThat(converter.convert(wordRequest(), sourceDocx, workspace).markdownPath()).isRegularFile();
assertThatThrownBy(() -> runner.run(List.of(fakePandoc, "--sleep"), workspace.root()))
        .isInstanceOf(DocumentPipelineNonRetryableException.class)
        .hasMessageContaining("Pandoc执行超时");
~~~

- [ ] Step 2: 运行失败测试。

Run: mvn -pl nexa-rag-infra -am -Dtest=PandocProcessRunnerTest,PandocDocxConverterTest -Dsurefire.failIfNoSpecifiedTests=false test

Expected: FAIL，缺少 Pandoc 类。

- [ ] Step 3: 实现受限进程和 Converter。

~~~java
List<String> command = List.of(properties.getExecutable(), source.toString(),
        "--from=docx", "--to=markdown", "--wrap=none",
        "--extract-media=" + assetsDirectory, "--output=" + markdownFile);
~~~

进程不经 shell 拼接；stdout 丢弃，stderr 用虚拟线程读取并限制字节数；超时强制终止。转换后校验主文件和资源是工作区内常规文件，受最大 Markdown、资源数量、单资源和总资源大小限制。配置 enabled=false 时不注册 WORD Converter。没有 Word Heading 样式时不得启发式伪造 Markdown 标题。

- [ ] Step 4: 运行通过测试。

Run: mvn -pl nexa-rag-infra -am -Dtest=PandocProcessRunnerTest,PandocDocxConverterTest -Dsurefire.failIfNoSpecifiedTests=false test

Expected: PASS；假进程覆盖超时和输出校验，不依赖本机 Pandoc。

## Task 5: 迁移 MinerU、Tika、透传 Handler 与工作流

Files:
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MinerUPdfConverter.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/extract/MinerUZipArtifactExtractor.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/tika/TikaDocumentConverter.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/handler/DirectReferenceArtifactHandler.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/handler/ConvertAndPublishArtifactHandler.java
- Delete: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/DocumentArtifactParser.java
- Delete: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/service/DocumentParseService.java
- Delete: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/service/impl/DocumentParseServiceImpl.java
- Delete: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/MinerUArtifactParser.java
- Delete: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/extract/MinerUZipResultExtractor.java
- Delete: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/extract/MarkdownImageUrlRewriter.java
- Delete: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/tika/TikaArtifactParser.java
- Delete: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/passthrough/PassthroughArtifactParser.java
- Delete: nexa-rag-infra/src/main/java/com/nexarag/infra/parser/cloud/FeishuParser.java
- Modify: nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/document/ParsingNode.java
- Test: nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/MinerUPdfConverterTest.java
- Test: nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/extract/MinerUZipArtifactExtractorTest.java
- Test: nexa-rag-infra/src/test/java/com/nexarag/infra/parser/handler/DirectReferenceArtifactHandlerTest.java
- Test: nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/document/ParsingNodeTest.java

- [ ] Step 1: 写失败测试。

~~~java
ExtractedDocument result = extractor.extract(zipInputStream, workspace);
assertThat(result.markdownPath()).isRegularFile();
assertThat(result.assets()).singleElement().extracting(ExtractedAsset::relativePath)
        .isEqualTo("images/a.png");
assertThatThrownBy(() -> extractor.extract(zipOf("../escape.md", "bad"), workspace))
        .isInstanceOf(DocumentPipelineNonRetryableException.class);
~~~

- [ ] Step 2: 运行失败测试。

Run: mvn -pl nexa-rag-infra,nexa-rag-workflow -am -Dtest=MinerUPdfConverterTest,MinerUZipArtifactExtractorTest,DirectReferenceArtifactHandlerTest,ParsingNodeTest -Dsurefire.failIfNoSpecifiedTests=false test

Expected: FAIL，新 Converter/Handler 不存在。

- [ ] Step 3: 完成迁移。

MinerUPdfConverter 只支持 PDF，保留 MinerUParseLimiter 和 OCR；ZIP extractor 逐条目以 BoundedFileTransfer 写入工作区，拒绝 Zip Slip、符号链接、重复和超限条目。TikaDocumentConverter 将抽取文本写入工作区而非 ByteArrayInputStream。DirectReferenceArtifactHandler 只支持 MARKDOWN/EXCEL。ConvertAndPublishArtifactHandler 负责阶段化、Converter 选择、Publisher 调用和工作区关闭。

ParsingNode 改为注入 DocumentArtifactService，将 document 模块 FileType 显式映射为 DocumentFormat；状态机、幂等和异常语义不改变。

- [ ] Step 4: 运行通过测试。

Run: mvn -pl nexa-rag-infra,nexa-rag-workflow -am -Dtest=MinerUPdfConverterTest,MinerUZipArtifactExtractorTest,DirectReferenceArtifactHandlerTest,ParsingNodeTest -Dsurefire.failIfNoSpecifiedTests=false test

Expected: PASS；PDF 保留 MinerU，DOCX 有 Pandoc 路由，Markdown/Excel 不复制制品。

## Task 6: 飞书官方导出接入与来源文件化

Files:
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/source/model/FileBackedSourceDocument.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/source/feishu/FeishuExportTaskClient.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/source/feishu/model/FeishuExportTask.java
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/source/feishu/model/FeishuExportedDocument.java
- Modify: nexa-rag-infra/src/main/java/com/nexarag/infra/config/CloudDocumentProperties.java
- Modify: nexa-rag-infra/src/main/java/com/nexarag/infra/source/ExternalDocumentSourceReader.java
- Modify: nexa-rag-infra/src/main/java/com/nexarag/infra/source/ExternalDocumentSourceService.java
- Modify: nexa-rag-infra/src/main/java/com/nexarag/infra/source/ExternalDocumentSourceServiceImpl.java
- Modify: nexa-rag-infra/src/main/java/com/nexarag/infra/source/feishu/FeishuDocxSourceReader.java
- Modify: nexa-rag-infra/src/main/java/com/nexarag/infra/source/yuque/YuqueSourceReader.java
- Delete: nexa-rag-infra/src/main/java/com/nexarag/infra/source/feishu/FeishuCliDocumentExporter.java
- Delete: nexa-rag-infra/src/main/java/com/nexarag/infra/source/model/SourceReadResultBO.java
- Test: nexa-rag-infra/src/test/java/com/nexarag/infra/source/feishu/FeishuExportTaskClientTest.java
- Test: nexa-rag-infra/src/test/java/com/nexarag/infra/source/ExternalDocumentSourceServiceImplTest.java
- Test: nexa-rag-infra/src/test/java/com/nexarag/infra/source/yuque/YuqueSourceReaderTest.java

- [ ] Step 1: 写失败测试。

~~~java
FeishuExportedDocument exported = client.export(request, workspace);
assertThat(exported.docxFile()).isRegularFile().hasSize(12L);
assertThatThrownBy(() -> client.export(overLimitRequest, workspace))
        .isInstanceOf(DocumentPipelineNonRetryableException.class)
        .hasMessageContaining("飞书导出文件超过大小限制");
~~~

- [ ] Step 2: 运行失败测试。

Run: mvn -pl nexa-rag-infra -am -Dtest=FeishuExportTaskClientTest,ExternalDocumentSourceServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test

Expected: FAIL，缺少导出任务客户端和文件化来源模型。

- [ ] Step 3: 实现飞书任务与统一交接。

客户端依次创建 DOCX 导出任务、以受限间隔轮询、下载结果；先检查 Content-Length，再以 BoundedFileTransfer 的实际计数写 source.docx。权限、超限、无效文档抛不可重试异常；网络和 5xx 保留可重试异常。

FileBackedSourceDocument 实现 AutoCloseable，持有工作区、DOCX、标题和小型元数据。来源服务在 try-with-resources 中上传 source-snapshots/{documentId}/source.docx，再以 StagedDocument 调用 DocumentArtifactService.processStaged，禁止重新下载、CLI JSON、Block API 全文降级。Yuque Reader 同步迁移为文件化返回：在同一上限约束下将其现有正文写入工作区，保持来源类型、标题和既有授权语义；不得成为飞书失败时的降级路径。删除 CLI 配置，增加 export 轮询、超时和 DOCX 限额配置。

- [ ] Step 4: 运行通过测试。

Run: mvn -pl nexa-rag-infra -am -Dtest=FeishuExportTaskClientTest,ExternalDocumentSourceServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test

Expected: PASS；飞书下载超限时不会产生快照或解析制品。

## Task 7: 清理消息、文档、全量验证和真实 Pandoc 验收

Files:
- Modify: nexa-rag-infra/src/main/java/com/nexarag/infra/messaging/document/task/DocumentStorageCleanupMessage.java
- Modify: nexa-rag-document/src/main/java/com/nexarag/document/messaging/consumer/RocketMqDocumentStorageCleanupConsumer.java
- Modify: nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentPipelineOutboxServiceImpl.java
- Test: nexa-rag-document/src/test/java/com/nexarag/document/messaging/RocketMqDocumentStorageCleanupConsumerTest.java
- Test: nexa-rag-document/src/test/java/com/nexarag/document/outbox/DocumentPipelineSchemaContractTest.java
- Modify: docs/operations/feishu-cli-document-import.md
- Modify: docs/superpowers/specs/2026-08-11-file-backed-document-artifact-pipeline-design.md

- [ ] Step 1: 写失败测试，兼容清理消息版本。

~~~java
consumer.onMessage(message(schemaVersion2("original/a.docx", "parsed/9/", "source-snapshots/9/")));
verify(storageService).delete("original/a.docx");
verify(storageService).deleteByPrefix("parsed/9/");
verify(storageService).deleteByPrefix("source-snapshots/9/");

consumer.onMessage(message(schemaVersion1("original/a.pdf", "parsed/9/content.md")));
verify(storageService, never()).deleteByPrefix(anyString());
~~~

- [ ] Step 2: 运行失败测试。

Run: mvn -pl nexa-rag-document,nexa-rag-infra -am -Dtest=RocketMqDocumentStorageCleanupConsumerTest,DocumentPipelineSchemaContractTest -Dsurefire.failIfNoSpecifiedTests=false test

Expected: FAIL，schema v2 字段和消费者逻辑缺失。

- [ ] Step 3: 实现版本兼容并同步文档。

schema v2 写入解析和来源快照的精确前缀；消费者根据 documentId 重新计算并验证前缀，v1 只删除原对象和主解析对象。运维文档改为飞书 DOCX 导出/Pandoc 导入，写明权限、Pandoc 部署、上限、临时磁盘、失败语义和“历史 Word 不自动重跑”。

- [ ] Step 4: 运行回归、真实转换和仓库检查。

Run: mvn -pl nexa-rag-infra,nexa-rag-document,nexa-rag-workflow -am test

Expected: PASS。

Run:

~~~powershell
& 'D:\Software\笔记&协助\pandoc-3.1.13\pandoc.exe' 'D:\下载\飞书\架构演进.docx' --from=docx --to=markdown --wrap=none --extract-media=assets --output=content.md
git status --short
git diff --check
~~~

Expected: Pandoc 生成 Markdown 和媒体目录；该样本没有 Word Heading 样式，不应伪造 # 标题；git diff --check 无错误。真实飞书环境验收需要部署账号具备导出权限，若未执行必须在交付说明中如实标注。
