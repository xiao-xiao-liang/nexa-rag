# 结构化章节检索 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让有标题层级的 Word、PDF、Markdown 将标题作为章节定位而非回答证据，并在证据偏弱时仅从命中章节的后代正文中补充 2–3 个相关片段；无充分证据时保守拒答。

**Architecture:** 文档模块把“标题树”和“正文窗口块”建模为两套关系：`DocumentSectionDO` 保存章节父子关系，`DocumentChunk` 保存正文及超长窗口关系。内容索引写入“文档标题 + 标题路径 + 原文”，章节导航索引只定位范围；工作流先常规检索，再在必要时对范围内正文二次召回、Rerank 和预算截断。

**Tech Stack:** Java 21、Spring Boot、MyBatis-Plus、Flyway、Milvus、Elasticsearch、JUnit 5、Mockito、AssertJ。

---

## 实施前约束

- 只对 Word、PDF、Markdown 的**可用标题层级**创建章节树；Excel 与纯文本保持原切分语义。
- `parentChunkId` 仍只表示超长正文的窗口父子关系，绝不能作为章节父子关系。
- 不建立 RAGAS、标注集、指标面板或参数自动调优；本期只留下结构化日志与可配置参数。
- 不在代码中执行全量删除或重建。用户已授权未来删除旧索引，但实际破坏性重建必须另行确认并按本文 Task 10 的运行手册执行。
- 当前工作区已有用户改动 `nexa-rag-boot/src/main/resources/application.yml` 和 `nexa-rag-front/src/shared/api/client.ts`；实施时不得覆盖或混入提交。
- 未取得明确提交授权时，不执行 `git commit`、`push`、`reset` 或清理工作区。

## 文件结构与职责

| 文件 | 变更 | 职责 |
| --- | --- | --- |
| `nexa-rag-boot/src/main/resources/db/migration/V16__add_document_section_structure.sql` | 新建 | 创建沿用 `del_flag` 的 `document_section`，并给 `document_chunk` 加 `section_id`、迁移期可空的 `index_content`。 |
| `nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql` | 修改 | 同步全新安装时的完整表结构。 |
| `nexa-rag-document/.../entity/DocumentSectionDO.java` | 新建 | 章节树持久化对象。 |
| `nexa-rag-document/.../mapper/DocumentSectionMapper.java` | 新建 | 章节树的按文档替换、后代查询。 |
| `nexa-rag-document/.../splitter/DocumentSplitResult.java` | 新建 | 一次切分的章节草稿和正文块草稿。 |
| `nexa-rag-document/.../splitter/DocumentSectionDraft.java` | 新建 | 尚未持久化的章节节点。 |
| `nexa-rag-document/.../splitter/ChunkDraft.java` | 修改 | 承载 `sectionId` 与 `indexContent`。 |
| `nexa-rag-document/.../splitter/markdown/MarkdownHeadingScanner.java` | 修改 | 用标题栈输出树关系与正文边界，不产出纯标题正文块。 |
| `nexa-rag-document/.../splitter/{DocumentSplitter,DocumentSplitterFactory}.java` | 修改 | 所有切分器返回统一的 `DocumentSplitResult`；非结构化切分器返回空章节集合。 |
| `nexa-rag-document/.../splitter/markdown/{MarkdownParentDocumentSplitter,MarkdownBrotherDocumentSplitter}.java` | 修改 | 对有效标题层级生成章节草稿及关联正文块，异常时退回窗口切分。 |
| `nexa-rag-document/.../service/impl/{DocumentChunkingServiceImpl,DocumentChunkPersistenceService,DocumentChunkServiceImpl}.java` | 修改 | 在同一事务中替换章节与正文块。 |
| `nexa-rag-document/.../entity/DocumentChunk.java`、`.../mapper/DocumentChunkMapper.java` | 修改 | 保存/查询 `sectionId` 与 `indexContent`。 |
| `nexa-rag-retrieval/.../model/{IndexableChunk,SectionNavigationDocument,RetrievalChunk}.java` | 修改/新建 | 明确正文证据、章节导航候选和上下文组装所需字段。 |
| `nexa-rag-retrieval/.../index/{vector,keyword}/*` | 修改/新建 | 内容索引与独立章节导航索引的写入、删除、查询。 |
| `nexa-rag-retrieval/.../config/RetrievalProperties.java` | 修改 | 提供候选、RRF、扩召、Token 预算、接受阈值等运行配置。 |
| `nexa-rag-retrieval/.../retriever/*` | 修改/新建 | 宽松候选、章节导航与受章节范围限制的正文检索。 |
| `nexa-rag-workflow/.../dispatcher/chat/RetrievalFusionDispatcher.java` | 修改 | 根据“证据质量偏弱”而非仅空结果触发扩召。 |
| `nexa-rag-workflow/.../node/chat/{RerankNode,EvidenceQualityNode,AnswerGenerationNode}.java` | 修改/新建 | Rerank 截断、证据质量判定、Token 预算与拒答。 |
| `nexa-rag-boot/src/main/resources/application.yml` | 修改 | 仅追加本功能配置；先人工合并用户已有改动。 |
| `docs/operations/structured-section-rebuild.md` | 新建 | 经独立批准后执行的预检、删除、重处理和验收手册。 |

### Task 1: 建立持久化模型与迁移契约

**Files:**

- Create: `nexa-rag-boot/src/main/resources/db/migration/V16__add_document_section_structure.sql`
- Modify: `nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/entity/DocumentSectionDO.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/mapper/DocumentSectionMapper.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/entity/DocumentChunk.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/mapper/DocumentChunkMapper.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/DocumentSectionSchemaContractTest.java`

- [ ] **Step 1: 写失败的数据库契约测试。**

```java
@Test
void schemaShouldSeparateSectionTreeFromChunkWindowRelation() throws IOException {
    String sql = Files.readString(Path.of("../nexa-rag-boot/src/main/resources/db/migration/"
            + "V16__add_document_section_structure.sql"));

    assertThat(sql).contains("CREATE TABLE document_section");
    assertThat(sql).contains("parent_section_id BIGINT NULL");
    assertThat(sql).contains("section_id BIGINT NULL");
    assertThat(sql).contains("index_content MEDIUMTEXT NULL");
    assertThat(sql).contains("idx_document_section_parent");
}
```

- [ ] **Step 2: 运行测试，确认迁移尚不存在。**

Run: `mvn -pl nexa-rag-document -Dtest=DocumentSectionSchemaContractTest test`

Expected: FAIL，提示找不到 `V16__add_document_section_structure.sql`。

- [ ] **Step 3: 实现迁移、全量 schema 与 DO/Mapper。**

```sql
CREATE TABLE document_section (
    section_id BIGINT NOT NULL COMMENT '章节ID',
    document_id BIGINT NOT NULL COMMENT '文档ID',
    parent_section_id BIGINT NULL COMMENT '父章节ID',
    title VARCHAR(512) NOT NULL COMMENT '当前标题',
    heading_path_json JSON NOT NULL COMMENT '从根到当前的标题路径',
    heading_level INT NOT NULL COMMENT '标题级别',
    start_line INT NOT NULL COMMENT '章节起始行',
    end_line INT NOT NULL COMMENT '章节结束行',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    del_flag TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (section_id),
    KEY idx_document_section_document (document_id),
    KEY idx_document_section_parent (document_id, parent_section_id)
) COMMENT='文档标题章节树';

ALTER TABLE document_chunk
    ADD COLUMN section_id BIGINT NULL COMMENT '所属章节ID' AFTER parent_chunk_id,
    ADD COLUMN index_content MEDIUMTEXT NULL COMMENT '用于检索的标题路径增强正文' AFTER text,
    ADD KEY idx_document_chunk_section (document_id, section_id);
```

```java
@Data
@TableName("document_section")
public class DocumentSectionDO {
    @TableId(type = IdType.INPUT)
    private Long sectionId;
    private Long documentId;
    private Long parentSectionId;
    private String title;
    private String headingPathJson;
    private Integer headingLevel;
    private Integer startLine;
    private Integer endLine;
}
```

`DocumentChunk` 新增 `Long sectionId`、`String indexContent`；迁移期 `index_content` 允许为 NULL，Task 4 的新切分链路必须始终为新正文块赋值，索引端必须拒绝空索引文本。`DocumentSectionMapper` 必须声明 `deleteByDocumentId(Long)`、`selectDescendantSectionIds(Long documentId, Long sectionId)`，使用按层批量查询或等价数据库查询返回后代，且不得包含根节点自身。

- [ ] **Step 4: 运行契约测试与模块编译。**

Run: `mvn -pl nexa-rag-document -Dtest=DocumentSectionSchemaContractTest test`

Expected: PASS。

Run: `mvn -pl nexa-rag-document -am -DskipTests compile`

Expected: BUILD SUCCESS。

### Task 2: 统一切分结果并保留非结构化兼容性

**Files:**

- Create: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/DocumentSectionDraft.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/DocumentSplitResult.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/ChunkDraft.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/DocumentSplitter.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/text/RegexTextDocumentSplitter.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/excel/ExcelDocumentSplitter.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/splitter/DocumentSplitterFactoryTest.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/splitter/text/RegexTextDocumentSplitterTest.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/splitter/excel/ExcelDocumentSplitterTest.java`

- [ ] **Step 1: 为非结构化输入补充失败回归测试。**

```java
assertThat(result.sections()).isEmpty();
assertThat(result.structured()).isFalse();
assertThat(result.chunks()).allSatisfy(chunk -> {
    assertThat(chunk.sectionId()).isNull();
    assertThat(chunk.indexContent()).isEqualTo(chunk.text());
});
```

- [ ] **Step 2: 运行三个切分器测试，确认旧接口导致编译失败。**

Run: `mvn -pl nexa-rag-document -Dtest=DocumentSplitterFactoryTest,RegexTextDocumentSplitterTest,ExcelDocumentSplitterTest test`

Expected: FAIL，测试尚无 `DocumentSplitResult`。

- [ ] **Step 3: 用不可变草稿类型替换 `List<ChunkDraft>` 返回值。**

```java
public record DocumentSectionDraft(
        Long sectionId, Long parentSectionId, String title, List<String> headingPath,
        int headingLevel, int startLine, int endLine) { }

public record DocumentSplitResult(
        List<DocumentSectionDraft> sections, List<ChunkDraft> chunks, boolean structured) {
    public static DocumentSplitResult unstructured(List<ChunkDraft> chunks) {
        return new DocumentSplitResult(List.of(), chunks, false);
    }
}

public record ChunkDraft(
        Long chunkId, Long parentChunkId, Long sectionId, String text,
        String indexContent, Integer tokenCount, Map<String, Object> metadata, boolean skipIndex) { }

public interface DocumentSplitter {
    DocumentSplitResult split(DocumentSplitContext context);
}
```

对 `RegexTextDocumentSplitter`、`ExcelDocumentSplitter` 及其调用方，把原有块列表包为 `DocumentSplitResult.unstructured(chunks)`。非结构化块的 `indexContent` 固定等于其原始 `text`，使现有检索语义不变。

- [ ] **Step 4: 运行三个回归测试。**

Run: `mvn -pl nexa-rag-document -Dtest=DocumentSplitterFactoryTest,RegexTextDocumentSplitterTest,ExcelDocumentSplitterTest test`

Expected: PASS；Excel 与纯文本没有任何 `sectionId`。

### Task 3: 用标题栈生成章节树，杜绝纯标题正文块

**Files:**

- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/markdown/MarkdownHeadingScanner.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/markdown/MarkdownSectionStructureBuilder.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/markdown/MarkdownParentDocumentSplitter.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/markdown/MarkdownBrotherDocumentSplitter.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/DocumentSectionIdGenerator.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/splitter/markdown/MarkdownHeadingScannerTest.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/splitter/markdown/MarkdownParentDocumentSplitterTest.java`

- [ ] **Step 1: 写标题树与目标回归的失败测试。**

```java
@Test
void shouldKeepTitleOnlySectionAsNavigationButNotEvidenceChunk() {
    DocumentSplitResult result = splitter.split(context("""
            # 3. 环境与目录规范：先让环境稳定
            ## 3.1 建议的项目目录
            /home/liang/swift/
            ├── models/
            """));

    assertThat(result.structured()).isTrue();
    assertThat(result.sections()).extracting(DocumentSectionDraft::title)
            .containsExactly("3. 环境与目录规范：先让环境稳定", "3.1 建议的项目目录");
    assertThat(result.chunks()).singleElement().satisfies(chunk -> {
        assertThat(chunk.sectionId()).isEqualTo(result.sections().get(1).sectionId());
        assertThat(chunk.text()).contains("/home/liang/swift/");
        assertThat(chunk.indexContent()).contains("3. 环境与目录规范：先让环境稳定")
                .contains("3.1 建议的项目目录");
    });
}
```

再添加断言：H1 后直接 H3、循环或空标题、无任何标题三种输入均 `structured == false`，且回退块的 `sectionId == null`。

- [ ] **Step 2: 运行 Markdown 测试，确认新断言失败。**

Run: `mvn -pl nexa-rag-document -Dtest=MarkdownHeadingScannerTest,MarkdownParentDocumentSplitterTest test`

Expected: FAIL；当前扫描器会把一级纯标题输出为普通块。

- [ ] **Step 3: 实现结构构建和两个 Markdown 策略。**

```java
private void acceptHeading(int level, String title, int lineNumber) {
    while (!headingStack.isEmpty() && headingStack.peek().level() >= level) {
        closeAt(lineNumber - 1);
    }
    if (level > headingStack.size() + 1) {
        throw new InvalidHeadingHierarchyException("标题级别跳跃");
    }
    Long parentSectionId = headingStack.isEmpty() ? null : headingStack.peek().sectionId();
    MarkdownSectionNode node = new MarkdownSectionNode(
            sectionIdGenerator.nextId(), parentSectionId, title,
            paths(headingStack, title), level, lineNumber);
    headingStack.push(node);
}
```

`MarkdownSectionStructureBuilder` 必须：

1. 仅从标题行创建 `DocumentSectionDraft`；
2. 仅为去掉空白后仍有正文的 section 创建 `ChunkDraft`；
3. 把文档标题、`String.join(" > ", headingPath)`、正文用换行拼为 `indexContent`；
4. 通过既有窗口切分逻辑处理超长正文，并只在该场景维护 `parentChunkId`；
5. 捕获 `InvalidHeadingHierarchyException` 后记录 `structureFallbackReason`，并调用原窗口切分逻辑生成 `DocumentSplitResult.unstructured(...)`。

两个 Markdown splitter 共享结构构建器，分别复用原有父文档/兄弟窗口策略；不要复制标题扫描代码。

- [ ] **Step 4: 运行 Markdown 与原窗口父子关系回归。**

Run: `mvn -pl nexa-rag-document -Dtest=MarkdownHeadingScannerTest,MarkdownParentDocumentSplitterTest test`

Expected: PASS；纯标题仅存在于 `sections`，超长正文测试仍证明 `parentChunkId` 独立可用。

### Task 4: 原子替换章节和正文块，并把索引文本贯穿到读取端

**Files:**

- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentChunkingServiceImpl.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentChunkPersistenceService.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentChunkServiceImpl.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentChunkService.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/model/IndexableChunk.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/dto/res/DocumentChunkIndexResult.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentChunkingServiceImplTest.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentChunkTransactionServiceTest.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentChunkServiceImplTest.java`

- [ ] **Step 1: 写失败的事务与索引读取测试。**

```java
verify(sectionMapper).deleteByDocumentId(2075566770998038530L);
verify(sectionMapper).insertBatch(List.of(parentSection, childSection));
verify(chunkMapper).insertBatch(argThat(chunks -> chunks.stream()
        .allMatch(chunk -> chunk.getSectionId() != null
                && chunk.getIndexContent().contains("环境与目录规范"))));

assertThat(indexableChunk.text()).contains("/home/liang/swift/");
assertThat(indexableChunk.indexContent()).contains("3.1 建议的项目目录");
```

- [ ] **Step 2: 运行持久化测试，确认新章节协作尚未实现。**

Run: `mvn -pl nexa-rag-document -Dtest=DocumentChunkingServiceImplTest,DocumentChunkTransactionServiceTest,DocumentChunkServiceImplTest test`

Expected: FAIL，缺少章节替换和 `indexContent` 映射。

- [ ] **Step 3: 在一个事务内持久化，并在索引读取模型中显式区分原文和索引文。**

```java
@Transactional(rollbackFor = Exception.class)
public void replaceDocumentStructure(Long documentId, DocumentSplitResult result) {
    documentSectionMapper.deleteByDocumentId(documentId);
    documentChunkMapper.deleteByDocumentId(documentId);
    if (!result.sections().isEmpty()) {
        documentSectionMapper.insertBatch(toSectionDOs(documentId, result.sections()));
    }
    documentChunkMapper.insertBatch(toChunkEntities(documentId, result.chunks()));
}

public record IndexableChunk(
        Long chunkId, Long documentId, Integer chunkOrder, Long parentChunkId,
        Long sectionId, String text, String indexContent, String metadataJson, Integer tokenCount) { }
```

删除顺序固定为：旧章节 → 旧正文 → 新章节 → 新正文，事务失败必须整体回滚。`DocumentChunkingServiceImpl` 只调用一次 `replaceDocumentStructure`；不得先写正文、后写章节以产生孤立 `sectionId`。

- [ ] **Step 4: 运行文档模块所有切分和持久化测试。**

Run: `mvn -pl nexa-rag-document test`

Expected: PASS。

### Task 5: 将内容索引与章节导航索引物理分离

**Files:**

- Create: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/model/SectionNavigationDocument.java`
- Create: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/model/SectionNavigationHit.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/index/vector/MilvusVectorIndexClient.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/index/keyword/ElasticsearchKeywordIndexClient.java`
- Create: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/index/SectionNavigationIndexRepository.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/config/RetrievalProperties.java`
- Test: `nexa-rag-retrieval/src/test/java/com/nexarag/retrieval/index/vector/MilvusVectorIndexClientTest.java`
- Test: `nexa-rag-retrieval/src/test/java/com/nexarag/retrieval/index/keyword/ElasticsearchKeywordIndexClientTest.java`

- [ ] **Step 1: 写失败测试，证明纯标题不会写入内容索引、但会写入导航索引。**

```java
assertThat(contentUpsertRequest.getData()).noneMatch(row ->
        row.get("chunk_id").equals("section-3"));
assertThat(sectionNavigationUpsertRequest.getData()).anyMatch(row ->
        row.get("section_id").equals("section-3")
                && row.get("heading_path").contains("环境与目录规范"));
assertThat(contentUpsertRequest.getData()).allMatch(row ->
        row.containsKey("section_id") && row.containsKey("index_content"));
```

- [ ] **Step 2: 运行两个索引客户端测试。**

Run: `mvn -pl nexa-rag-retrieval -Dtest=MilvusVectorIndexClientTest,ElasticsearchKeywordIndexClientTest test`

Expected: FAIL，新字段与导航集合尚未存在。

- [ ] **Step 3: 定义两个索引文档契约并扩展外部模式。**

```java
public record SectionNavigationDocument(
        Long sectionId, Long documentId, Long parentSectionId,
        String title, String headingPath, Integer headingLevel) {
    public String indexContent() {
        return title + "\n" + headingPath;
    }
}

public record SectionNavigationHit(
        Long sectionId, Long documentId, double score, String channel) { }
```

Milvus 内容 collection 增加 `section_id` 和 `index_content` 标量字段，向量仍由 `indexContent` 生成，返回命中必须带 `sectionId` 和原始 `text`。新增独立 section-navigation collection，其向量由 `SectionNavigationDocument.indexContent()` 生成。Elasticsearch 同样建立独立导航 index，内容 `text` 字段使用项目已配置的中文 analyzer；若当前集群无该 analyzer，启动时必须失败并给出明确配置错误，不能静默退化为错误分词。

- [ ] **Step 4: 把导航写入/删除接入文档索引生命周期。**

```java
public interface SectionNavigationIndexRepository {
    void upsert(Collection<SectionNavigationDocument> sections);
    void deleteByDocumentId(Long documentId);
    List<SectionNavigationHit> search(String query, int limit);
}
```

内容重建前先删除同一 `documentId` 的内容和导航索引；成功持久化章节后写导航索引，成功持久化正文后写内容索引。任何单通道写入失败沿用现有文档索引失败状态与重试机制。

- [ ] **Step 5: 运行检索模块索引测试。**

Run: `mvn -pl nexa-rag-retrieval test`

Expected: PASS，测试断言内容索引不含仅标题节点。

### Task 6: 引入可版本化候选配置和宽松粗过滤

**Files:**

- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/config/RetrievalProperties.java`
- Modify: `nexa-rag-boot/src/main/resources/application.yml`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/retriever/vector/MilvusConversationRetriever.java`
- Modify: 现有 BM25 conversation retriever（用 `rg -n "class .*ConversationRetriever" nexa-rag-retrieval` 定位）
- Test: `nexa-rag-retrieval/src/test/java/com/nexarag/retrieval/retriever/vector/MilvusConversationRetrieverTest.java`
- Test: 对应 BM25 retriever test；没有该文件时新建同包测试

- [ ] **Step 1: 写失败回归，覆盖原始查询的第五名正文块不被粗过滤丢弃。**

```java
RetrievalProperties.Candidate candidate = new RetrievalProperties.Candidate();
candidate.setVectorCandidateLimit(20);
candidate.setCoarseScoreFloor(0.0D);

List<RetrievalChunk> chunks = retriever.retrieve(request("微调的目录有什么规范"));
assertThat(chunks).extracting(RetrievalChunk::chunkId).contains(13L);
assertThat(chunks).noneMatch(RetrievalChunk::navigationOnly);
```

- [ ] **Step 2: 运行 retriever 测试，确认当前 `0.5` 过滤会丢失候选。**

Run: `mvn -pl nexa-rag-retrieval -Dtest=MilvusConversationRetrieverTest test`

Expected: FAIL，测试桩中分数为 `0.48699233` 的正文块被过滤。

- [ ] **Step 3: 定义一组集中配置，禁止针对个例散落常量。**

```java
@Data
public static class Candidate {
    private int vectorCandidateLimit = 20;
    private int keywordCandidateLimit = 20;
    private double coarseScoreFloor = 0.0D;
    private int rrfCandidateLimit = 20;
    private int rerankCandidateLimit = 12;
    private int expansionCandidateLimit = 8;
    private int expansionEvidenceLimit = 3;
    private int evidenceTokenBudget = 1800;
    private double acceptedRerankScore = 0.0D;
}
```

YAML 以 `nexa.retrieval.candidate` 配置这些项，并注明“初始保召回参数，后续由 RAGAS 评测校准”。`MilvusConversationRetriever` 和 BM25 retriever 先取得上限候选，再以 `coarseScoreFloor` 做宽松过滤；`RerankNode` 移除 `FINAL_TOP_K`，改从该配置读取。

- [ ] **Step 4: 运行两类 retriever 测试。**

Run: `mvn -pl nexa-rag-retrieval -Dtest=MilvusConversationRetrieverTest test`

Expected: PASS；分数低于旧阈值但高于新粗过滤线的正文仍能进入候选。

### Task 7: 限定在后代正文范围内的二次检索与 Rerank

**Files:**

- Create: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/retriever/section/SectionExpansionRetriever.java`
- Create: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/retriever/section/SectionDescendantChunkRepository.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/model/RetrievalChunk.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/dispatcher/chat/RetrievalFusionDispatcher.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/RerankNode.java`
- Test: `nexa-rag-retrieval/src/test/java/com/nexarag/retrieval/retriever/section/SectionExpansionRetrieverTest.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/dispatcher/chat/RetrievalFusionDispatcherTest.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/chat/RerankNodeTest.java`

- [ ] **Step 1: 写失败测试，保证扩召不按 `chunkOrder` 相邻追加。**

```java
when(sectionNavigationIndex.search("微调的目录有什么规范", 3))
        .thenReturn(List.of(new SectionNavigationHit(300L, 2075566770998038530L, 0.81D, "VECTOR")));
when(descendantChunkRepository.searchDescendants(2075566770998038530L, 300L,
        "微调的目录有什么规范", 8))
        .thenReturn(List.of(directoryChunk, unrelatedSiblingChunk, guideChunk));

assertThat(expanded).extracting(RetrievalChunk::chunkId)
        .containsExactly(directoryChunk.chunkId(), guideChunk.chunkId());
assertThat(expanded).noneMatch(chunk -> chunk.chunkOrder() == directoryChunk.chunkOrder() + 1);
```

测试还需构造“常规结果非空但均为 `navigationOnly` 或正文过短”的场景，断言 dispatcher 会调用扩召；而高质量正文命中时扩召不会发生。

- [ ] **Step 2: 运行扩召与 workflow 测试，确认仅空结果时扩召的现状不符合契约。**

Run: `mvn -pl nexa-rag-retrieval,nexa-rag-workflow -am -Dtest=SectionExpansionRetrieverTest,RetrievalFusionDispatcherTest,RerankNodeTest test`

Expected: FAIL，当前 dispatcher 只在 `results.isEmpty()` 时扩召。

- [ ] **Step 3: 实现范围查询、二次候选和类型标识。**

```java
public record RetrievalChunk(
        Long chunkId, Long documentId, Integer chunkOrder, Long parentChunkId, Long sectionId,
        String title, String source, String content, double score, String channel, int rank,
        boolean navigationOnly) { }

public interface SectionDescendantChunkRepository {
    List<RetrievalChunk> searchDescendants(
            Long documentId, Long rootSectionId, String query, int limit);
}

public List<RetrievalChunk> expand(String query) {
    return sectionNavigationIndex.search(query, navigationLimit).stream()
            .flatMap(hit -> descendantChunks.searchDescendants(
                    hit.documentId(), hit.sectionId(), query, candidateLimit).stream())
            .filter(chunk -> !chunk.navigationOnly() && StringUtils.hasText(chunk.content()))
            .collect(toDistinctChunks());
}
```

后代范围由 `document_section` 递归查询得到 section IDs，再作为 `section_id IN (...)` 过滤条件交给内容向量/关键词检索；不要把完整章节正文加载到内存后做字符串过滤。把普通候选和扩召候选合并、去重后统一交给同一 Rerank 模型，最多接受 `expansionEvidenceLimit` 个新增正文块。

- [ ] **Step 4: 改成由证据质量触发扩召，并记录原因。**

```java
boolean needsExpansion = quality.empty()
        || quality.onlyNavigation()
        || quality.bodyTooShort()
        || quality.lowConfidence()
        || quality.incompleteCoverage();
log.info("章节扩召判定，conversationId={}, needsExpansion={}, reason={}, regularCount={}",
        conversationId, needsExpansion, quality.reason(), regularResults.size());
```

dispatcher 使用 `needsExpansion` 判断；常规路径不为空并不意味着足以回答。日志只记录 ID、计数、分数区间、原因与章节范围，禁止写入完整用户问题或提示词。

- [ ] **Step 5: 运行限定范围扩召测试。**

Run: `mvn -pl nexa-rag-retrieval,nexa-rag-workflow -am -Dtest=SectionExpansionRetrieverTest,RetrievalFusionDispatcherTest,RerankNodeTest test`

Expected: PASS；扩召只返回后代正文，不返回标题节点或固定相邻块。

### Task 8: 证据质量、Token 预算与保守拒答

**Files:**

- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/EvidenceQualityNode.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/EvidenceQuality.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/AnswerGenerationNode.java`
- Modify: 聊天 workflow 图装配文件（用 `rg -n "new RerankNode|AnswerGenerationNode" nexa-rag-workflow` 定位后修改唯一装配点）
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/chat/EvidenceQualityNodeTest.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/chat/AnswerGenerationNodeTest.java`

- [ ] **Step 1: 写失败测试，规定低置信或不完整证据必须拒答。**

```java
EvidenceQuality quality = node.evaluate(List.of(titleOnly, shortBody), request);
assertThat(quality.accepted()).isFalse();
assertThat(quality.reason()).isEqualTo("INSUFFICIENT_BODY_EVIDENCE");

String answer = answerGenerationNode.generate(refusedContext("LOW_CONFIDENCE"));
assertThat(answer).contains("现有资料不足");
assertThat(answer).doesNotContain("我猜");
```

另加预算测试：四个候选合计超过 1800 tokens 时，仅保留 rerank 最高、总 token 不超过预算的至多三个正文块。

- [ ] **Step 2: 运行质量与回答节点测试。**

Run: `mvn -pl nexa-rag-workflow -Dtest=EvidenceQualityNodeTest,AnswerGenerationNodeTest test`

Expected: FAIL，当前没有独立质量判定或预算组装。

- [ ] **Step 3: 实现明确、可测试的质量对象和上下文选择。**

```java
public record EvidenceQuality(boolean accepted, String reason, int bodyCount, int tokenCount) {
    public static EvidenceQuality rejected(String reason, int bodyCount, int tokenCount) {
        return new EvidenceQuality(false, reason, bodyCount, tokenCount);
    }
}

private List<RetrievalChunk> selectEvidence(List<RetrievalChunk> reranked) {
    int remaining = properties.getCandidate().getEvidenceTokenBudget();
    return reranked.stream()
            .filter(chunk -> !chunk.navigationOnly() && StringUtils.hasText(chunk.content()))
            .filter(chunk -> chunk.score() >= properties.getCandidate().getAcceptedRerankScore())
            .takeWhile(chunk -> tokenCount(chunk.content()) <= remaining)
            .limit(properties.getCandidate().getExpansionEvidenceLimit())
            .toList();
}
```

实际实现不能使用会遗漏后续更短高分片段的 `takeWhile`：按 rerank 降序逐个检查，能放入剩余预算才加入。`AnswerGenerationNode` 只序列化已接受的正文 `content`；质量拒绝时直接走现有“资料不足”响应路径，不调用回答模型生成猜测。

- [ ] **Step 4: 在图中把质量判定置于常规 Rerank 后、扩召前和扩召后。**

```text
常规 Rerank → EvidenceQualityNode → accepted ? AnswerGenerationNode
                                  └→ SectionExpansionRetriever → RerankNode
                                      → EvidenceQualityNode → accepted ? AnswerGenerationNode : 拒答
```

第二次质量判定失败必须设置固定拒答原因，且记录最终证据 `chunkId`、token 总数、Rerank 分数区间；不记录正文。

- [ ] **Step 5: 运行 workflow 节点测试。**

Run: `mvn -pl nexa-rag-workflow -Dtest=EvidenceQualityNodeTest,AnswerGenerationNodeTest,RerankNodeTest test`

Expected: PASS；低置信路径不会调用回答模型。

### Task 9: 端到端回归、故障降级与运行日志

**Files:**

- Create: `nexa-rag-boot/src/test/java/com/nexarag/boot/integration/StructuredSectionRetrievalIntegrationTest.java`
- Modify: 现有检索通道异常处理类（用 `rg -n "vector.*fail|keyword.*fail|catch.*Retriev" nexa-rag-retrieval nexa-rag-workflow` 定位）
- Modify: `nexa-rag-boot/src/main/resources/application.yml`

- [ ] **Step 1: 写失败的原始问题端到端回归。**

```java
@Test
void directoryConventionQuestionShouldUseDescendantBodyInsteadOfTitleOnlyChunk() {
    ChatResult result = ask("微调的目录有什么规范");

    assertThat(result.answer()).contains("models").contains("datasets").contains("output");
    assertThat(result.trace().finalEvidenceChunkIds()).contains(13L);
    assertThat(result.trace().finalEvidenceChunkIds()).doesNotContain(12L);
    assertThat(result.trace().expansionReason()).isNotBlank();
}
```

测试数据必须包含标题节点 12、目录正文 13 和至少三个无关高相似候选，模拟原始 0.569/0.519/0.512/0.499/0.487 的排序，证明宽松候选 + Rerank/扩召能选到正文而非标题。

- [ ] **Step 2: 运行测试，确认当前链路返回资料不足或未选正文。**

Run: `mvn -pl nexa-rag-boot -am -Dtest=StructuredSectionRetrievalIntegrationTest test`

Expected: FAIL，当前最终上下文中无 `chunkId=13`。

- [ ] **Step 3: 覆盖降级和日志字段。**

```java
assertThat(trace.channelFailures()).contains("SECTION_NAVIGATION");
assertThat(trace.finalDecision()).isEqualTo("REFUSE");
assertThat(trace.refusalReason()).isEqualTo("INSUFFICIENT_BODY_EVIDENCE");
```

对“导航索引故障、向量单通道故障、无标题文档、层级异常文档、扩召超过预算”分别测试：其他通道继续；最终证据不足则拒答。日志/trace 只允许含候选计数、剔除原因、章节 ID、最终 chunk ID、分数区间、token 数和拒答原因。

- [ ] **Step 4: 运行端到端与完整 Maven 验证。**

Run: `mvn -pl nexa-rag-boot -am -Dtest=StructuredSectionRetrievalIntegrationTest test`

Expected: PASS。

Run: `mvn test`

Expected: BUILD SUCCESS。

### Task 10: 编写经批准后才能执行的全量重建手册

**Files:**

- Create: `docs/operations/structured-section-rebuild.md`
- Modify: `docs/superpowers/specs/2026-08-06-structured-section-retrieval-design.md`（仅追加手册链接）

- [ ] **Step 1: 写运行手册的只读预检。**

```sql
SELECT document_id, file_path, parse_status
FROM document
WHERE del_flag = 0
  AND (file_path IS NULL OR file_path = '' OR parse_status IS NULL);
```

```bash
curl -fsS http://localhost:8080/api/documents/{documentId}/process-status
```

手册明确：上述 SQL 非空时停止，先修复原始文件或解析状态；没有二次明确批准不得进入删除步骤。

- [ ] **Step 2: 写可回滚的操作顺序和验收查询。**

```text
1. 导出 document 与 document_chunk、document_section 的计数和待处理 documentId 清单。
2. 获得本次窗口的书面批准，停止对外知识库检索流量。
3. 使用各环境既有 Milvus/Elasticsearch 运维工具，按 documentId 删除内容索引与 section-navigation 索引；不得 drop 整个共享 collection/index。
4. 调用 POST /api/documents/{documentId}/retry 逐份重处理，等待 process-status 为完成。
5. 对每份文档核对 section 数、正文 chunk 数、Milvus 内容/导航写入数、Elasticsearch 内容/导航写入数。
6. 执行 Task 9 的回归测试和人工抽检；任一失败文档用原文件单独 retry。
```

验收 SQL：

```sql
SELECT d.document_id,
       COUNT(DISTINCT s.section_id) AS section_count,
       COUNT(DISTINCT c.chunk_id) AS body_chunk_count,
       SUM(c.section_id IS NOT NULL) AS structured_body_chunk_count
FROM document d
LEFT JOIN document_section s ON s.document_id = d.document_id AND s.del_flag = 0
LEFT JOIN document_chunk c ON c.document_id = d.document_id AND c.del_flag = 0
WHERE d.del_flag = 0
GROUP BY d.document_id;
```

- [ ] **Step 3: 链接手册并做文档检查。**

Run: `rg -n "RAGAS|指标看板|全量重建|独立批准" docs/operations/structured-section-rebuild.md docs/superpowers/specs/2026-08-06-structured-section-retrieval-design.md`

Expected: 运行手册明确需要独立批准；设计说明明确 RAGAS/评测平台不在本期范围。

### Task 11: 最终变更核验与交接

**Files:**

- Modify: 本计划列出的文件，仅限实际实现产生的变更

- [ ] **Step 1: 执行最小相关模块测试。**

Run: `mvn -pl nexa-rag-document,nexa-rag-retrieval,nexa-rag-workflow,nexa-rag-boot -am test`

Expected: BUILD SUCCESS。

- [ ] **Step 2: 运行静态与差异检查。**

Run: `git diff --check`

Expected: 无输出，退出码 0。

Run: `git status --short`

Expected: 只包含本计划相关文件，以及实施前已存在的 `application.yml`、`client.ts` 用户改动；不得篡改后两者的无关内容。

- [ ] **Step 3: 人工检查四个不可破坏契约。**

```text
□ 纯标题没有进入内容向量或内容关键词索引。
□ section_id 与 parent_chunk_id 在模型、SQL 和测试中分别表达不同关系。
□ Excel/纯文本的 chunks 没有 section_id，index_content 等于 text。
□ 低置信/不完整证据在扩召后仍会拒答，而不是调用回答模型猜测。
```

- [ ] **Step 4: 交接时报告。**

报告实际执行的测试命令与输出、运行配置初值、尚未建设的 RAGAS 评测系统，以及全量索引重建尚待独立批准。未经用户明确要求，保持未提交状态。

## 计划自检

- 设计覆盖：章节树（Task 1–4）、索引分离（Task 5）、宽松候选和可配置参数（Task 6）、有限后代扩召（Task 7）、Token 预算与拒答（Task 8）、原始故障回归/可观测性（Task 9）、全量重建独立审批手册（Task 10）均有实现和测试步骤。
- 延后事项：计划没有创建评测集、RAGAS 调用、质量指标看板或自动参数优化；仅保留日志字段和回归测试。
- 类型一致性：`DocumentSectionDraft.sectionId` → `DocumentChunk.sectionId` → `IndexableChunk.sectionId` → `RetrievalChunk.sectionId`；`ChunkDraft.indexContent` → `DocumentChunk.indexContent` → 索引 embedding/BM25 输入，所有路径名称一致。
- 实施前再次运行占位语句扫描（匹配 `T` + `ODO`、`T` + `BD`、英文“稍后实现”和同类表述），确认计划中没有占位语句。
