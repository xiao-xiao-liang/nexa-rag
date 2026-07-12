# 文档消息重试状态一致性实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 RocketMQ 文档消费次数、重试次数、最近重试时间和最终失败上下文一致地写入 `document` 表。

**Architecture:** Outbox 继续只维护生产端发布状态。普通消费者按 `reconsumeTimes` 更新当前处理轮次的消费字段；死信消费者携带最后一次实际 Workflow 执行次数，最终失败事务原子写入消费和失败字段。

**Tech Stack:** Java 21、Spring Boot、MyBatis-Plus、RocketMQ Spring、JUnit 5、Mockito、AssertJ。

---

### Task 1: 锁定普通消费重试字段语义

**Files:**
- Modify: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentServiceImplTest.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentServiceImpl.java`

- [ ] **Step 1: 编写首次消费和重投消费失败测试**

在 `DocumentServiceImplTest` 中验证：首次消费写入 `consumedTimes=1`、`retryCount=0` 且不更新
`lastRetryTime`；第三次消费写入 `consumedTimes=3`、`retryCount=2` 并更新 `lastRetryTime`。

- [ ] **Step 2: 运行测试并确认失败**

```powershell
mvn -pl nexa-rag-document -am '-Dtest=DocumentServiceImplTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

预期新增断言因 `retry_count` 和 `last_retry_time` 尚未写入而失败。

- [ ] **Step 3: 最小修改普通消费更新逻辑**

在 `recordMessageConsumption` 中计算 `int retryCount = Math.max(consumedTimes - 1, 0)`，更新
`retry_count`，并仅在 `retryCount > 0` 时更新 `last_retry_time`。

- [ ] **Step 4: 运行定向测试并确认通过**

执行 Step 2 的命令，预期 `DocumentServiceImplTest` 全部通过。

### Task 2: 统一新处理轮次最大重试次数

**Files:**
- Modify: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentServiceImplTest.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentServiceImpl.java`

- [ ] **Step 1: 编写提交和人工重试失败测试**

验证新处理轮次设置 `retry_count=0`、`max_retry_count=maxReconsumeTimes`、
`last_retry_time=NULL`。

- [ ] **Step 2: 运行测试并确认失败**

执行 Task 1 的定向测试命令，预期最大重试次数或最近重试时间断言失败。

- [ ] **Step 3: 注入消息配置并初始化轮次字段**

在 `DocumentServiceImpl` 注入 `DocumentPipelineMessagingProperties`，提交和人工重试时执行：

```java
document.setRetryCount(0);
document.setMaxRetryCount(messagingProperties.getMaxReconsumeTimes());
document.setLastRetryTime(null);
```

- [ ] **Step 4: 更新测试夹具并验证通过**

调整 `DocumentServiceImpl` 构造参数，运行定向测试并确认通过。

### Task 3: 最终失败同步消费上下文

**Files:**
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentService.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentServiceImpl.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentProcessFailureService.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/messaging/RocketMqDocumentPipelineDeadLetterConsumer.java`
- Modify: `nexa-rag-document/src/test/java/com/nexarag/document/messaging/RocketMqDocumentPipelineConsumerTest.java`
- Modify: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentServiceImplTest.java`

- [ ] **Step 1: 编写 DLQ 计数和最终落库失败测试**

验证 DLQ 不额外增加 Workflow 执行次数，并最终保存 `consumed_times`、`retry_count`、
`last_message_id`、`last_retry_time`、消息失败状态和失败详情。

- [ ] **Step 2: 运行测试并确认失败**

```powershell
mvn -pl nexa-rag-document -am '-Dtest=RocketMqDocumentPipelineConsumerTest,DocumentServiceImplTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

预期 DLQ 次数或最终失败消费字段断言失败。

- [ ] **Step 3: 扩展最终失败更新参数**

让 `markProcessFailed` 接收 `consumedTimes`、`messageId` 和 `failureTime`，在同一个条件更新中写入
消费、重试和失败字段。DLQ 使用最后一次实际执行次数，不再额外 `+1`。

- [ ] **Step 4: 运行定向测试并确认通过**

执行 Step 2 的命令，预期全部通过。

### Task 4: 回归验证

**Files:**
- Verify only

- [ ] **Step 1: 运行 document 模块全量测试**

```powershell
mvn -pl nexa-rag-document -am test
```

- [ ] **Step 2: 运行编译和补丁检查**

```powershell
mvn -pl nexa-rag-boot -am -DskipTests compile
git diff --check
```

- [ ] **Step 3: 确认约束**

确认没有新增数据库字段、不运行 Flyway、不清理现有数据、不提交代码。
