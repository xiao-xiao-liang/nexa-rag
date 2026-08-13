# 文档结构恢复与层级切分（阶段一）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 保留飞书 DOCX 和 MinerU PDF 的结构证据，保守恢复标题层级，并让父子 Markdown 切分在局部异常时保留可信章节树。

**Architecture:** 解析模块发布受控 MinerU 中间 JSON 并在文档记录中持久化对象键。切分模块从原始 DOCX、解析 Markdown 与中间 JSON 提取标题证据，合并为带来源和置信度的结构结果，交给现有章节树和父子块逻辑。阶段一不调用 LLM，不替换 Pandoc/MinerU，不批量重跑历史文档。

**Tech Stack:** Java 21、Spring Boot、Jackson Streaming API、Apache POI XWPF、MyBatis-Plus、JUnit 5、AssertJ、Mockito。

---

## 文件结构与提交边界

| 范围 | 主要文件 | 职责 |
| --- | --- | --- |
| 持久化契约 | `V19__add_document_parsed_metadata.sql`、`Document`、`ParsingNode` | 保存结构制品定位元数据 |
| 解析制品 | `ExtractedDocumentBO`、`ExtractedStructureArtifactBO`、`MinerUZipFileExtractor`、`ArtifactPublisher` | 提取、发布、清理中间 JSON |
| 结构恢复 | `document/splitter/structure/*` | 标题证据、层级融合、行号定位 |
| 切分接入 | `DocumentSplitContext*`、`MarkdownHeadingScanner`、`MarkdownSectionStructureBuilder` | 消费恢复后的标题、局部降级 |

提交顺序为“解析制品与持久化契约”再到“结构恢复与切分接入”。第二个提交依赖第一个提交新增的元数据字段和对象键，两个提交内部不再拆分以保证可构建。

### Task 1: 保存解析附属制品元数据

**Files:**
- Create: `nexa-rag-boot/src/main/resources/db/migration/V19__add_document_parsed_metadata.sql`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/model/entity/Document.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/document/ParsingNode.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/document/ParsingNodeTest.java`

- [ ] 写失败测试：mock `DocumentParseService` 返回 `structureArtifacts` metadata；断言 update 的文档 JSON 中 `/structureArtifacts/0/objectKey` 为 `parsed/42/structure/mineru-middle.json`。
- [ ] 运行 `mvn -pl nexa-rag-workflow -am -Dtest=ParsingNodeTest -Dsurefire.failIfNoSpecifiedTests=false test`，预期 FAIL：`Document` 没有 `parsedMetadataJson` 或 Node 未写入。
- [ ] 新增迁移 `ALTER TABLE document ADD COLUMN parsed_metadata_json JSON NULL COMMENT '解析附属制品与结构元数据';`，在 `Document` 增加 `String parsedMetadataJson`，并在 `ParsingNode.markParsed` 用已有 `ObjectMapper` 序列化 `ParsedArtifact.metadata()`。序列化失败必须抛出 `DOCUMENT_PROCESS_CONFIG_INVALID`，不能将文档标为 `PARSED`。
- [ ] 再次运行上述测试，预期 PASS。
- [ ] 提交：`git add nexa-rag-boot/src/main/resources/db/migration/V19__add_document_parsed_metadata.sql nexa-rag-document/src/main/java/com/nexarag/document/model/entity/Document.java nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/document/ParsingNode.java nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/document/ParsingNodeTest.java; git commit -m "feat(document): 保存解析结构制品元数据"`。

### Task 2: 提取和发布 MinerU 中间 JSON

**Files:**
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/model/ExtractedStructureArtifactBO.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/model/ExtractedDocumentBO.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/client/LocalMinerUClient.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/extract/MinerUZipFileExtractor.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/publish/ArtifactPublisher.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/storage/ObjectNameResolver.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/extract/MinerUZipFileExtractorTest.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/publish/ArtifactPublisherTest.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/LocalMinerUClientTest.java`

- [ ] 写失败测试：ZIP 包含 `content.md`、图片、`middle.json`、`content_list.json` 时，结果的结构文件相对路径严格为 `mineru-middle.json`、`mineru-content-list.json`。覆盖 `../middle.json` 被拒绝，且 JSON 计入总解压字节上限。
- [ ] 运行 `mvn -pl nexa-rag-infra -am -Dtest=MinerUZipFileExtractorTest -Dsurefire.failIfNoSpecifiedTests=false test`，预期 FAIL：结果模型没有 `structureArtifacts()`。
- [ ] 创建 `record ExtractedStructureArtifactBO(Path file, String relativePath, String contentType)`；扩展 `ExtractedDocumentBO` 为 Markdown、资源、结构制品、metadata，并保留当前三参数兼容构造器，避免 Pandoc/Tika 行为变化。
- [ ] 将 `LocalMinerUClient` 的 `return_middle_json` 改为 `true`。Extractor 只接受文件名恰为 `middle.json`、`content_list.json` 的 JSON，复制到 `structure/` 并固定重命名；其余 JSON 不发布，继续使用 Zip Slip 和总字节限制。
- [ ] 写发布测试：结构文件写入 `parsed/{documentId}/structure/{name}`；返回 metadata 仅包含 `type`、`objectKey`、`contentType`、`size`；失败调用 `deleteByPrefix("parsed/{documentId}/")`。实现 `ObjectNameResolver.resolveParsedStructureObjectName(Long, String)`，只能使用安全简单文件名。
- [ ] 运行 `mvn -pl nexa-rag-infra -am -Dtest=MinerUZipFileExtractorTest,ArtifactPublisherTest,LocalMinerUClientTest -Dsurefire.failIfNoSpecifiedTests=false test`，预期 PASS。
- [ ] 提交：`git add nexa-rag-infra/src/main/java/com/nexarag/infra/parser/model nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/client/LocalMinerUClient.java nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/extract/MinerUZipFileExtractor.java nexa-rag-infra/src/main/java/com/nexarag/infra/parser/publish/ArtifactPublisher.java nexa-rag-infra/src/main/java/com/nexarag/infra/storage/ObjectNameResolver.java nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru nexa-rag-infra/src/test/java/com/nexarag/infra/parser/publish; git commit -m "feat(parser): 保留 MinerU 结构中间制品"`。

### Task 3: 定义标题证据和保守层级融合

**Files:**
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/structure/HeadingEvidenceSource.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/structure/HeadingEvidenceBO.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/structure/ResolvedHeadingBO.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/structure/DocumentStructureBO.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/structure/HeadingHierarchyResolver.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/splitter/structure/HeadingHierarchyResolverTest.java`

- [ ] 写失败测试：同一位置冲突的候选按 `MARKDOWN > WORD_STYLE > NUMBERING > HEURISTIC > MINERU` 取值；低于 `0.80` 的启发式候选不成为标题；最终层级限于 1–6，向下跳跃最多增加一级。
- [ ] 运行 `mvn -pl nexa-rag-document -am -Dtest=HeadingHierarchyResolverTest -Dsurefire.failIfNoSpecifiedTests=false test`，预期 FAIL。
- [ ] 定义不可变 `HeadingEvidenceBO`（标题、声明层级、顺序、来源、置信度、页码）、`ResolvedHeadingBO`（最终层级、Markdown 行号）和 `DocumentStructureBO`（标题、诊断）。Resolver 只做去重、来源优先级、阈值过滤和层级夹紧；不得访问存储、调用 LLM 或把单个加粗文本直接升为标题。
- [ ] 再次运行上述测试，预期 PASS。

### Task 4: 从 Markdown、DOCX 和 MinerU JSON 获取标题证据

**Files:**
- Modify: `nexa-rag-document/pom.xml`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/structure/MarkdownHeadingEvidenceExtractor.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/structure/WordHeadingEvidenceExtractor.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/structure/MinerUHeadingEvidenceExtractor.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/structure/HeadingNumberingParser.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/splitter/structure/MarkdownHeadingEvidenceExtractorTest.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/splitter/structure/WordHeadingEvidenceExtractorTest.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/splitter/structure/MinerUHeadingEvidenceExtractorTest.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/splitter/structure/HeadingNumberingParserTest.java`

- [ ] 在 document 模块直接依赖 `org.apache.poi:poi-ooxml`（版本由父 POM 管理）。写失败测试：POI 生成包含 `Heading 1/2`、普通段落、短 `1.1` 整段加粗、长加粗正文的 DOCX；真实样式必须输出 `WORD_STYLE`，伪标题仅在编号、整段加粗、去编号后不超过 80 字符、非句末标点同时满足时输出 `HEURISTIC`，长加粗正文被拒绝。
- [ ] 增加 Markdown、编号和 MinerU fixture 测试：Markdown 保护代码围栏并支持 1–6 级；编号支持 `1`、`1.2`、`1.2.3`、`一、`、`（一）`、`第十二章`，拒绝版本号/IP；MinerU 仅提取 title、页码、阅读顺序，忽略表格/图片/页眉页脚，并通过禁止 `ObjectMapper.readTree` 的流验证 Jackson `JsonParser` 流式读取。
- [ ] 运行 `mvn -pl nexa-rag-document -am -Dtest=MarkdownHeadingEvidenceExtractorTest,WordHeadingEvidenceExtractorTest,MinerUHeadingEvidenceExtractorTest,HeadingNumberingParserTest -Dsurefire.failIfNoSpecifiedTests=false test`，预期初次 FAIL、实现后 PASS。
- [ ] 实现四个 Extractor；它们只返回原始顺序候选，最终层级由 Task 3 Resolver 决定。

### Task 5: 编排结构解析并定位 Markdown 行号

**Files:**
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/DocumentSplitContext.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentSplitContextBuilder.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/structure/StructureArtifactReferenceBO.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/structure/HeadingLineLocator.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/structure/DocumentStructureResolver.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/service/DocumentSplitContextBuilderTest.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/splitter/structure/HeadingLineLocatorTest.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/splitter/structure/DocumentStructureResolverTest.java`

- [ ] 写失败测试：`parsedMetadataJson` 中的 `MINERU_MIDDLE_JSON` 仅作为对象键、类型、内容类型进入上下文，不能加载正文；未知类型返回空列表。行定位器用前向游标将重复标题定位到第 3、20 行，不能定位的候选只进入诊断。
- [ ] 运行 `mvn -pl nexa-rag-document -am -Dtest=DocumentSplitContextBuilderTest,HeadingLineLocatorTest,DocumentStructureResolverTest -Dsurefire.failIfNoSpecifiedTests=false test`，预期初次 FAIL、实现后 PASS。
- [ ] 在 `DocumentSplitContext` 加 `List<StructureArtifactReferenceBO>` 与兼容构造器；构建器白名单解析小型 metadata JSON。Resolver 通过 `FileStorageService.load` 读取原 DOCX（仅 WORD）和 JSON（仅 PDF），合并 Markdown、Word、MinerU 证据后调用 Resolver 和定位器。单一辅助文件损坏只记录 documentId 与计数，保留其余证据。

### Task 6: 接入章节树并由全篇降级改为局部修复

**Files:**
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/markdown/MarkdownHeadingScanner.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/markdown/MarkdownSectionStructureBuilder.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/model/dto/MarkdownSplitOptions.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/splitter/markdown/MarkdownHeadingScannerTest.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/splitter/markdown/MarkdownSectionStructureBuilderTest.java`

- [ ] 写失败测试：封面/摘要在首标题前时后续章节仍结构化；`# 第一章` 后的 `### 1.1.1` 修为 `##`；无法定位的 MinerU 标题降为正文，其余标题仍写入 `DocumentSectionDraft` 和完整 `titlePath`，并且不出现 `structuredFallback=true`。
- [ ] 运行 `mvn -pl nexa-rag-document -am -Dtest=MarkdownHeadingScannerTest,MarkdownSectionStructureBuilderTest,MarkdownParentDocumentSplitterTest,MarkdownBrotherDocumentSplitterTest -Dsurefire.failIfNoSpecifiedTests=false test`，预期初次 FAIL、实现后 PASS。
- [ ] 为 Scanner 增加接收 `DocumentStructureBO` 的重载，优先使用行号和层级，无结构时保持现有 Markdown-only 路径；默认最大标题层级为 6。Builder 注入 `DocumentStructureResolver`，将前言归属隐式根正文、跳级夹紧为上一层级加一，只有全文没有可用标题才调用 `fallbackToUnstructuredChunks`。chunk metadata 增加来源、置信度、诊断摘要，不保存正文或 JSON。
- [ ] 提交：`git add nexa-rag-document/pom.xml nexa-rag-document/src/main/java/com/nexarag/document/splitter nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentSplitContextBuilder.java nexa-rag-document/src/test/java/com/nexarag/document/splitter nexa-rag-document/src/test/java/com/nexarag/document/service/DocumentSplitContextBuilderTest.java; git commit -m "feat(splitter): 恢复文档标题层级切分"`。

### Task 7: 配置、端到端验收与运维文档

**Files:**
- Modify: `nexa-rag-boot/src/main/resources/application.yml`
- Modify: `docs/operations/structured-section-rebuild.md`
- Create: `nexa-rag-boot/src/test/java/com/nexarag/boot/integration/DocumentStructureResolutionIntegrationTest.java`

- [ ] 写端到端失败测试：DOCX fixture 含真实标题与加粗编号候选；PDF fixture 含已发布 Markdown 和 `middle.json`。断言章节非空、正文 `indexContent` 带完整标题路径、纯文本仍是非结构化窗口块。
- [ ] 在 `nexa.parser.artifact.structure` 增加 `heuristic-heading-enabled`、`heuristic-heading-min-confidence: 0.80`、`max-diagnostics: 100`，绑定 `DocumentStructureProperties`。日志只包含 documentId、文件类型、候选数、来源统计、未定位数和耗时，禁止正文、标题全文、JSON、预签名 URL 或凭据。
- [ ] 运行 `mvn -pl nexa-rag-boot -am -Dtest=DocumentStructureResolutionIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`，预期初次 FAIL、实现后 PASS；再运行 `mvn -pl nexa-rag-document,nexa-rag-infra,nexa-rag-workflow,nexa-rag-boot -am test; git diff --check`，预期全部 PASS 且格式检查无输出。
- [ ] 在每份文档得到独立重处理授权后，按 `structured-section-rebuild.md` 验收飞书伪标题 DOCX、真实 Word 标题 DOCX 和 `D:\下载\飞书\MySQL.pdf`；记录章节数、带标题路径正文数和来源分布，不记录完整正文。

## 自检

- 覆盖阶段一全部范围：元数据、中间制品、DOCX/PDF/Markdown 证据、保守融合、局部降级、配置和验收。
- 未混入阶段二的 PDF 书签/字体聚类，或阶段三的 LLM 与自动 Recall@K 阈值。
- 每项代码变更都有失败测试、通过测试与明确命令；新增类型均在首次使用前定义。
