# Document Delete Chunk Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在文档删除事务中删除 document_chunk，并仅在数据库事务提交后通过现有 Outbox 清理外部索引。

**Architecture:** `DocumentServiceImpl` 继续作为事务编排边界，先逻辑删除文档，再调用 `DocumentChunkService` 完成片段逻辑删除，最后落库 `CLEAN_DOCUMENT_INDEX` Outbox。Outbox 消费者保持异步清理 Milvus、Elasticsearch 和章节导航索引，不在事务内直接访问外部基础设施。

**Tech Stack:** Spring Boot、Spring 事务、MyBatis-Plus、RocketMQ Outbox、JUnit 5、Mockito。

---

### Task 1: 补充文档删除事务的回归测试

**Files:**
- Modify: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentServiceImplTest.java`

- [x] **Step 1: 写入失败测试**

在 `deleteDocumentShouldCreateCleanupTaskAfterSuccessfulDelete` 前新增测试，令 `DocumentChunkService.deleteByDocumentId(1L)` 抛出 `ServiceException("删除文档片段失败")`，断言 `deleteDocument(1L)` 抛出该异常，且 `deleteTaskService.createIndexCleanupTask(1L)` 未调用。`deleteDocument` 为 `@Transactional`，异常会使此前的文档逻辑删除回滚。

- [x] **Step 2: 运行失败测试确认 RED**

Run: `mvn -pl nexa-rag-document -Dtest=DocumentServiceImplTest#deleteDocumentShouldNotCreateCleanupTaskWhenChunkDeletionFails test`

Expected: 测试失败，因为当前删除流程尚未调用 `DocumentChunkService.deleteByDocumentId`。

- [x] **Step 3: 写入成功顺序测试断言**

更新现有成功测试，验证 `DocumentChunkService.deleteByDocumentId(1L)` 被调用，并保留文档删除成功与 Outbox 创建成功断言。

### Task 2: 在事务内清理文档片段

**Files:**
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentServiceImpl.java:291-306`

- [x] **Step 1: 注入片段服务**

为 `DocumentServiceImpl` 增加构造器注入的 `private final DocumentChunkService documentChunkService`。

- [x] **Step 2: 在文档逻辑删除成功后删除片段**

在 `deleteDocument` 的事务中、确认 `logicDeleteDocument(documentId)` 成功后加入：

```java
// 1. 逻辑删除文档主记录
boolean deleted = logicDeleteDocument(documentId);

// 2. 删除全部文档片段；异常会回滚此前文档删除和后续 Outbox 写入
documentChunkService.deleteByDocumentId(documentId);
```

保留原有 Outbox 创建逻辑，并将步骤注释顺序调整为“文档删除、片段删除、Outbox 写入”。

- [x] **Step 3: 运行测试确认 GREEN**

Run: `mvn -pl nexa-rag-document -Dtest=DocumentServiceImplTest test`

Expected: `DocumentServiceImplTest` 全部通过。

### Task 3: 扩大验证并检查差异

**Files:**
- Verify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentServiceImpl.java`
- Verify: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentServiceImplTest.java`

- [x] **Step 1: 编译受影响模块**

Run: `mvn -pl nexa-rag-document -am -DskipTests compile`

Expected: BUILD SUCCESS。

- [x] **Step 2: 检查差异**

Run: `git diff --check` 与 `git diff -- nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentServiceImpl.java nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentServiceImplTest.java`

Expected: 无空白错误，修改仅覆盖事务内 chunk 删除与其回归测试。
