# 文档对象存储清理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 文档删除后可靠地清理 MinIO 中的原始文件和解析文件。

**Architecture:** 在删除事务内为对象存储清理写入独立 Outbox 任务，事务提交后由独立 RocketMQ 消费者删除消息携带的对象名。失败由现有 RocketMQ 重试、死信及告警链路处理，避免在数据库事务中执行 MinIO I/O。

**Tech Stack:** Spring Boot、MyBatis-Plus、RocketMQ、MinIO、JUnit 5、Mockito。

---

### Task 1: 创建对象存储清理任务

**Files:**
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/enums/DocumentTaskType.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentDeleteTaskService.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentDeleteTaskServiceImpl.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentServiceImpl.java`
- Modify: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentServiceImplTest.java`

- [ ] **Step 1: 写入失败测试**

```java
verify(documentService.deleteTaskService).createStorageCleanupTask(document);
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `mvn --% -pl nexa-rag-document -am -Dtest=DocumentServiceImplTest test`

Expected: FAIL，提示对象存储清理任务未创建。

- [ ] **Step 3: 写入最小实现**

```java
deleteTaskService.createStorageCleanupTask(document);
```

创建 `CLEAN_DOCUMENT_STORAGE` 类型的 Outbox，消息保存文档 ID、原始对象名和解析对象名，并使用独立 Topic。

- [ ] **Step 4: 运行测试并确认通过**

Run: `mvn --% -pl nexa-rag-document -am -Dtest=DocumentServiceImplTest test`

Expected: PASS。

### Task 2: 消费并清理对象存储

**Files:**
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/messaging/document/task/DocumentStorageCleanupMessage.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/config/DocumentTaskMessagingProperties.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/messaging/consumer/RocketMqDocumentStorageCleanupConsumer.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/messaging/consumer/RocketMqDocumentStorageCleanupDeadLetterConsumer.java`
- Create: `nexa-rag-document/src/test/java/com/nexarag/document/messaging/RocketMqDocumentStorageCleanupConsumerTest.java`

- [ ] **Step 1: 写入失败测试**

```java
consumer.onMessage(message);
verify(fileStorageService).delete("original/demo.pdf");
verify(fileStorageService).delete("parsed/demo.md");
verify(outboxService).markTaskSucceeded(101L);
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `mvn --% -pl nexa-rag-document -am -Dtest=RocketMqDocumentStorageCleanupConsumerTest test`

Expected: FAIL，提示消费者类不存在。

- [ ] **Step 3: 写入最小实现**

```java
for (String objectName : distinctObjectNames(message)) {
    fileStorageService.delete(objectName);
}
outboxService.markTaskSucceeded(message.outboxId());
```

对空对象名跳过、对相同对象名去重；任何删除失败抛出异常以触发 MQ 重试。死信后复用现有任务失败标记和告警服务。

- [ ] **Step 4: 运行测试并确认通过**

Run: `mvn --% -pl nexa-rag-document -am -Dtest=RocketMqDocumentStorageCleanupConsumerTest test`

Expected: PASS。

### Task 3: 支持失败任务人工重试与完整验证

**Files:**
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentTaskAdminServiceImpl.java`
- Modify: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentTaskAdminServiceImplTest.java`

- [ ] **Step 1: 写入失败测试**

```java
assertThat(retry.getTaskType()).isEqualTo(DocumentTaskType.CLEAN_DOCUMENT_STORAGE);
assertThat(retryOutbox.getMessageBody()).contains("original/demo.pdf", "parsed/demo.md");
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `mvn --% -pl nexa-rag-document -am -Dtest=DocumentTaskAdminServiceImplTest test`

Expected: FAIL，提示新任务类型未处理。

- [ ] **Step 3: 写入最小实现**

```java
case CLEAN_DOCUMENT_STORAGE -> rebuildStorageCleanupMessage(...);
```

- [ ] **Step 4: 执行回归验证**

Run: `mvn --% -pl nexa-rag-document -am test`

Expected: PASS。
