# 双索引替换短期修正 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让文档重处理时 Milvus、正文 Elasticsearch 与导航 Elasticsearch 的替换和开关语义一致，避免旧正文 chunk 被 BM25 继续召回。

**Architecture:** `KeywordIndexClient` 新增面向业务的 `replaceDocument` 默认契约，先按 documentId 删除指定正文索引再批量 upsert。`DocumentIndexServiceImpl` 对正常与无可索引 chunk 两条路径都按 vector/keyword 开关执行清理，章节导航只在关键词索引启用时写入。保持既有“先删后写”的短期语义，不实现构建批次和可见性时间。

**Tech Stack:** Java 21、Spring Boot、Spring Data Elasticsearch、JUnit 5、Mockito、AssertJ。

---

### Task 1: 为关键词索引定义按文档替换契约

**Files:**

- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/index/keyword/KeywordIndexClient.java`
- Modify: `nexa-rag-retrieval/src/test/java/com/nexarag/retrieval/service/impl/DocumentIndexServiceImplTest.java`

- [x] **Step 1: 写失败测试，要求正常索引先删除正文 ES 再写入。**

  在测试 fixture 中保留 `StubKeywordIndexClient` 引用，并让它记录 `deleteByDocumentId(Long, String)` 与 `upsert` 调用顺序。新增测试：

  ```java
  @Test
  void indexDocumentShouldReplaceKeywordIndexBeforeWritingNewChunks() {
      Fixture fixture = new Fixture(DocumentStatus.CHUNKED, null, chunks());

      fixture.service.indexDocument(1L);

      assertThat(fixture.keywordIndexClient.operations())
              .containsExactly("delete:1:nexa_document_chunk", "upsert:1");
  }
  ```

- [x] **Step 2: 运行失败测试。**

  Run: `mvn -pl nexa-rag-retrieval -Dtest=DocumentIndexServiceImplTest#indexDocumentShouldReplaceKeywordIndexBeforeWritingNewChunks test`

  Expected: FAIL，正文关键词索引尚未在 `upsert` 前按 documentId 删除。

- [x] **Step 3: 在 KeywordIndexClient 添加默认替换方法。**

  ```java
  default List<KeywordIndexWriteResult> replaceDocument(KeywordIndexWriteRequest request) {
      if (request == null || request.documentId() == null) {
          throw new IllegalArgumentException("关键词索引替换请求或文档ID不能为空");
      }
      deleteByDocumentId(request.documentId(), request.indexName());
      return upsert(request);
  }
  ```

  为方法补充中文 Javadoc。`DocumentIndexServiceImpl.writeKeywordIndex` 改为调用 `keywordIndexClient.replaceDocument(request)`。

- [x] **Step 4: 再次运行测试。**

  Run: `mvn -pl nexa-rag-retrieval -Dtest=DocumentIndexServiceImplTest#indexDocumentShouldReplaceKeywordIndexBeforeWritingNewChunks test`

  Expected: PASS。

### Task 2: 补齐空分块与关键词关闭的索引语义

**Files:**

- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/service/impl/DocumentIndexServiceImpl.java`
- Modify: `nexa-rag-retrieval/src/test/java/com/nexarag/retrieval/service/impl/DocumentIndexServiceImplTest.java`

- [x] **Step 1: 写失败测试，要求空分块清理正文 ES。**

  ```java
  @Test
  void indexDocumentShouldClearKeywordIndexWhenNoIndexableChunkRemains() {
      Fixture fixture = new Fixture(DocumentStatus.CHUNKED, null, List.of());

      fixture.service.indexDocument(1L);

      assertThat(fixture.keywordIndexClient.operations())
              .containsExactly("delete:1:nexa_document_chunk");
  }
  ```

- [x] **Step 2: 运行失败测试。**

  Run: `mvn -pl nexa-rag-retrieval -Dtest=DocumentIndexServiceImplTest#indexDocumentShouldClearKeywordIndexWhenNoIndexableChunkRemains test`

  Expected: FAIL，空分块分支只清理了向量索引。

- [x] **Step 3: 实现空分块正文关键词清理。**

  在 `chunks.isEmpty()` 分支中，当 `keywordEnabled` 为 `true` 时调用：

  ```java
  keywordIndexClient.deleteByDocumentId(documentId, config.keywordIndexName());
  ```

- [x] **Step 4: 写失败测试，要求关闭关键词时不写章节导航。**

  ```java
  @Test
  void indexDocumentShouldNotWriteNavigationWhenKeywordIndexDisabled() throws Exception {
      ProcessDocumentRequest request = new ProcessDocumentRequest(null, null,
              new IndexConfigRequest(true, true, false));
      Fixture fixture = new Fixture(DocumentStatus.CHUNKED,
              objectMapper.writeValueAsString(request), chunks());

      fixture.service.indexDocument(1L);

      verify(fixture.navigationIndexRepository, never()).upsert(1L);
  }
  ```

- [x] **Step 5: 运行失败测试。**

  Run: `mvn -pl nexa-rag-retrieval -Dtest=DocumentIndexServiceImplTest#indexDocumentShouldNotWriteNavigationWhenKeywordIndexDisabled test`

  Expected: FAIL，`indexNavigation` 只判断了总开关。

- [x] **Step 6: 收紧导航开关。**

  将 `indexNavigation` 判断改为：

  ```java
  if (config.enabled() && config.keywordEnabled()) {
      sectionNavigationIndexRepository.upsert(documentId);
  }
  ```

- [x] **Step 7: 运行该测试类。**

  Run: `mvn -pl nexa-rag-retrieval -Dtest=DocumentIndexServiceImplTest test`

  Expected: PASS。

### Task 3: 回归验证与文档状态

**Files:**

- Modify: `docs/superpowers/plans/2026-08-10-dual-index-replace-short-term-implementation-plan.md`
- Modify: `docs/superpowers/specs/2026-08-10-unified-document-ingestion-design.md`

- [x] **Step 1: 执行模块编译。**

  Run: `mvn -pl nexa-rag-retrieval -am compile`

  Expected: PASS。

- [x] **Step 2: 执行差异检查。**

  Run: `git diff --check`

  Expected: 无空白错误。

- [x] **Step 3: 勾选已完成计划步骤，并把设计文档的短期修正标注为已实现。**

  仅更新本计划的复选框和设计文档中的实施状态，不修改其他设计决策。

## 执行记录

- `mvn -pl nexa-rag-retrieval -am compile` 已通过。
- 新增测试先以旧实现运行，确认 3 个断言分别因“正文 ES 未清理”“空分块未清理正文 ES”“关键词关闭仍写导航”失败。
- `DocumentIndexServiceImplTest` 通过单独编译测试类并执行 `mvn -pl nexa-rag-retrieval -Dtest=DocumentIndexServiceImplTest surefire:test` 验证，结果为 7/7 通过。
- 标准 `mvn -pl nexa-rag-retrieval -Dtest=DocumentIndexServiceImplTest test` 仍在 test-compile 阶段被无关的 `DocumentDeletedEventListenerTest` 与 `DocumentIndexCleanupDeadLetterConsumerTest` 缺失类型阻断；本计划未修改这些无关模块。
