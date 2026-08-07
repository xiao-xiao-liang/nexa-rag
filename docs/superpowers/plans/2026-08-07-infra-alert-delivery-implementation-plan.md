# Infra 告警投递能力实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将飞书和邮件最终失败告警实现为 `infra` 可复用能力，并通过文档任务 Outbox 可靠投递且独立追踪渠道状态。

**Architecture:** `infra` 定义脱敏告警消息、渠道适配器、投递器、RocketMQ 消费/DLQ 消费和 `AlertDeliveryLifecycle` SPI；`document` 创建告警 Outbox 并实现 SPI，以维护自己的 `document_task_outbox` 状态。`infra` 不访问文档表，文档模块不实现渠道协议。

**Tech Stack:** Spring Boot、RocketMQ、Jackson、Spring `RestClient`、Spring Mail、MyBatis-Plus、JUnit 5、Mockito。

---

## 文件结构

| 范围 | 文件 | 职责 |
| --- | --- | --- |
| Infra 模型 | `nexa-rag-infra/.../alert/model/AlertMessage.java` | 脱敏 MQ 告警消息。 |
| Infra SPI | `.../alert/AlertDeliveryLifecycle.java` | 业务任务状态回调，不暴露文档表。 |
| Infra 渠道 | `.../alert/channel/*` | 飞书、邮件和结构化日志渠道。 |
| Infra 消费 | `.../alert/messaging/*` | 告警 Topic 与 DLQ 消费。 |
| Document 编排 | `nexa-rag-document/.../service/DocumentTaskAlertService.java` | 为父失败任务创建两条渠道 Outbox。 |
| Document 回调 | `.../service/impl/DocumentTaskAlertLifecycle.java` | 实现 `AlertDeliveryLifecycle`，维护 Outbox 状态。 |
| Document 终态 | 处理/清理 DLQ 消费者与失败服务 | 标记失败后创建告警任务。 |
| 管理接口 | `.../controller/DocumentTaskController.java` | 查询和重试 FAILED 文档任务。 |

### Task 1: 建立 Infra 告警契约与配置

**Files:**

- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/alert/model/AlertMessage.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/alert/model/AlertChannel.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/alert/model/AlertSeverity.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/alert/AlertDeliveryLifecycle.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/config/AlertProperties.java`
- Modify: `nexa-rag-boot/src/main/resources/application.yml`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/alert/model/AlertMessageTest.java`

- [ ] **Step 1: 写失败测试，固定脱敏消息与渠道契约。**

```java
assertThat(new AlertMessage(11L, 7L, 3L, "operation", "CLEAN_DOCUMENT_INDEX",
        AlertSeverity.ERROR, AlertChannel.FEISHU, "索引清理失败", 5, now).failureReason())
        .doesNotContain("/home", "sk-", "Bearer ");
```

- [ ] **Step 2: 运行测试，确认契约尚不存在。**

Run: `mvn -pl nexa-rag-infra -Dtest=AlertMessageTest test`

- [ ] **Step 3: 实现领域无关对象与 SPI。**

```java
public interface AlertDeliveryLifecycle {
    boolean markProcessing(AlertMessage message, int consumeRetryCount);
    void markSucceeded(AlertMessage message);
    void markFailed(AlertMessage message, int consumeRetryCount, String failureReason);
}
```

`AlertMessage` 仅包含任务 ID、渠道、级别、脱敏失败原因、次数和时间；构造器拒绝空 ID/渠道/级别，失败原因限制 1024 字符并移除换行。`AlertProperties` 使用 `nexa.alert`，包含 `enabled`、Topic、消费者组、飞书 Webhook、邮件开关、收件人和发件人；所有敏感字段只通过环境变量写入。

- [ ] **Step 4: 补充配置。**

```yaml
nexa:
  alert:
    enabled: false
    topic: nexa-alert
    consumer-group: nexa-alert-worker
    feishu:
      enabled: false
      webhook-url: ${NEXA_ALERT_FEISHU_WEBHOOK_URL:}
    email:
      enabled: false
      recipients: ${NEXA_ALERT_EMAIL_RECIPIENTS:}
      from: ${NEXA_ALERT_EMAIL_FROM:}
```

- [ ] **Step 5: 运行测试并提交。**

Run: `mvn -pl nexa-rag-infra -Dtest=AlertMessageTest test`

Commit: `feat(infra): 定义通用告警契约`

### Task 2: 实现 Infra 渠道与分发器

**Files:**

- Modify: `nexa-rag-infra/pom.xml`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/alert/channel/AlertChannelSender.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/alert/channel/FeishuAlertChannelSender.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/alert/channel/EmailAlertChannelSender.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/alert/channel/LoggingAlertChannelSender.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/alert/AlertDispatcher.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/alert/AlertDispatcherTest.java`

- [ ] **Step 1: 写渠道选择与禁用渠道的失败测试。**

```java
verify(feishuSender).send(message);
assertThatThrownBy(() -> dispatcher.dispatch(disabledEmailMessage))
        .isInstanceOf(ServiceException.class);
```

- [ ] **Step 2: 添加 `spring-boot-starter-mail` 并实现渠道接口。**

```java
public interface AlertChannelSender {
    AlertChannel channel();
    void send(AlertMessage message);
}
```

飞书使用 `RestClient` POST `{"msg_type":"text","content":{"text":"..."}}`；邮件使用 `JavaMailSender` 发送纯文本。发送前校验渠道启用和必要配置，异常保留原异常并抛出 `ServiceException`。日志渠道只记录脱敏字段，用于禁用外部渠道时的可观测性，不能伪造投递成功。

- [ ] **Step 3: 实现 `AlertDispatcher`。**

```java
public void dispatch(AlertMessage message) {
    AlertChannelSender sender = senders.get(message.channel());
    if (sender == null) {
        throw new ServiceException("未配置告警渠道，channel=" + message.channel());
    }
    sender.send(message);
}
```

- [ ] **Step 4: 运行测试并提交。**

Run: `mvn -pl nexa-rag-infra -Dtest=AlertDispatcherTest test`

Commit: `feat(infra): 支持飞书与邮件告警渠道`

### Task 3: 实现 Infra 告警 Topic 与 DLQ 消费

**Files:**

- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/alert/messaging/RocketMqAlertConsumer.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/alert/messaging/RocketMqAlertDeadLetterConsumer.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/config/AlertProperties.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/alert/messaging/RocketMqAlertConsumerTest.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/alert/messaging/RocketMqAlertDeadLetterConsumerTest.java`

- [ ] **Step 1: 写消费状态回调测试。**

```java
when(lifecycle.markProcessing(message, 1)).thenReturn(true);
consumer.onMessage(message);
verify(dispatcher).dispatch(message);
verify(lifecycle).markSucceeded(message);
```

另写 DLQ 测试，验证 `markFailed(message, 5, reason)` 被调用且 `AlertDispatcher` 从不被调用。

- [ ] **Step 2: 实现正常消费者。**

```java
if (!lifecycle.markProcessing(message, consumeRetryCount)) {
    return;
}
dispatcher.dispatch(message);
lifecycle.markSucceeded(message);
```

`RocketMQMessageListener` 的 topic/group 从 `AlertProperties` 读取；通道异常继续抛出以触发 RocketMQ 重试。仅记录 `outboxId`、`parentOutboxId`、渠道、级别和脱敏原因。

- [ ] **Step 3: 实现 DLQ 消费者。**

DLQ 消费者读取原始 `AlertMessage`，调用 `markFailed` 并记录 `error` 日志；不调用分发器、不创建任何新消息。消费者类用 `@ConditionalOnBean(AlertDeliveryLifecycle.class)`，确保 infra 独立启动时不因缺少业务回调失败。

- [ ] **Step 4: 运行测试并提交。**

Run: `mvn -pl nexa-rag-infra -Dtest=RocketMqAlertConsumerTest,RocketMqAlertDeadLetterConsumerTest test`

Commit: `feat(infra): 消费告警任务并处理死信`

### Task 4: 文档模块创建告警 Outbox 并实现状态回调

**Files:**

- Create: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentTaskAlertService.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentTaskAlertServiceImpl.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentTaskAlertLifecycle.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentPipelineOutboxService.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentPipelineOutboxServiceImpl.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentTaskAlertServiceImplTest.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentTaskAlertLifecycleTest.java`

- [ ] **Step 1: 写两渠道独立建任务测试。**

```java
verify(outboxService, times(2)).save(captor.capture());
assertThat(captor.getAllValues()).extracting(DocumentTaskOutboxDO::getTaskType)
        .containsExactly(DocumentTaskType.SEND_FEISHU_FAILURE_ALERT,
                DocumentTaskType.SEND_EMAIL_FAILURE_ALERT);
```

- [ ] **Step 2: 实现 `DocumentTaskAlertService`。**

父任务只允许 `PROCESS_DOCUMENT` 和 `CLEAN_DOCUMENT_INDEX`；分别映射为 `WARNING`、`ERROR`。每个渠道生成新 operation ID、唯一 message key、`parentOutboxId` 和 `AlertMessage` JSON。输入失败原因经统一脱敏/截断后写入消息体；保存任一渠道任务失败即抛异常，使父任务终态与两条告警任务保持同一事务边界。

- [ ] **Step 3: 实现 `AlertDeliveryLifecycle`。**

```java
public boolean markProcessing(AlertMessage message, int times) {
    return outboxService.markTaskProcessing(message.outboxId(), times);
}
```

成功和失败分别调用既有 `markTaskSucceeded`、`markTaskFailed`。回调验证 task type 必须为告警类型，避免 `infra` 消费者误更新其他文档任务。

- [ ] **Step 4: 运行测试并提交。**

Run: `mvn -pl nexa-rag-document -am -Dtest=DocumentTaskAlertServiceImplTest,DocumentTaskAlertLifecycleTest "-Dsurefire.failIfNoSpecifiedTests=false" test`

Commit: `feat(document): 为终态失败创建渠道告警任务`

### Task 5: 连接终态失败、消息发布与旧告警迁移

**Files:**

- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/messaging/publisher/DocumentPipelineOutboxPublisher.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/messaging/consumer/RocketMqDocumentPipelineConsumer.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentProcessFailureService.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/messaging/consumer/RocketMqDocumentPipelineDeadLetterConsumer.java`
- Create: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/messaging/DocumentIndexCleanupDeadLetterConsumer.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/messaging/document/model/DocumentPipelineFailureMessage.java`
- Delete: `nexa-rag-document/src/main/java/com/nexarag/document/alert/DocumentPipelineAlertService.java`
- Delete: `nexa-rag-document/src/main/java/com/nexarag/document/alert/DocumentPipelineFailureEvent.java`
- Delete: `nexa-rag-document/src/main/java/com/nexarag/document/alert/LoggingDocumentPipelineAlertService.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/messaging/RocketMqDocumentPipelineDeadLetterConsumerTest.java`
- Test: `nexa-rag-retrieval/src/test/java/com/nexarag/retrieval/messaging/DocumentIndexCleanupDeadLetterConsumerTest.java`

- [ ] **Step 1: 写终态失败创建告警任务的失败测试。**

```java
LocalDateTime failureTime = LocalDateTime.of(2026, 8, 7, 18, 0);
DocumentPipelineFailureMessage message = new DocumentPipelineFailureMessage(
        101L, 1L, "process-1", "INDEXING", "索引写入失败", "连接超时", 5,
        "rocketmq-message-1", failureTime);
when(documentService.markProcessFailed(1L, "process-1", "INDEXING", "索引写入失败",
        "连接超时", 5, "rocketmq-message-1", failureTime)).thenReturn(true);
assertThat(failureService.markFinalFailure(message)).isTrue();
verify(taskAlertService).createFailureAlerts(101L, 5, "索引写入失败");
```

- [ ] **Step 2: 发布器按任务类型反序列化。**

```java
return switch (outbox.getTaskType()) {
    case PROCESS_DOCUMENT -> objectMapper.readValue(body, DocumentPipelineMessage.class);
    case CLEAN_DOCUMENT_INDEX -> objectMapper.readValue(body, DocumentTaskMessage.class);
    case SEND_FEISHU_FAILURE_ALERT, SEND_EMAIL_FAILURE_ALERT ->
            objectMapper.readValue(body, AlertMessage.class);
};
```

- [ ] **Step 3: 仅在父任务已写入 FAILED 后创建告警。**

处理流水线 DLQ 和清理 DLQ 都先调用状态更新，再调用 `DocumentTaskAlertService`。清理 DLQ 使用原 `DocumentTaskMessage.outboxId` 标记任务失败；处理流水线为保证父任务可定位，`DocumentPipelineFailureMessage` 新增首字段 `outboxId`，并由正常消费者/DLQ 原样传递。`DocumentProcessFailureService` 不再直接发送外部告警；其原有结构化日志迁移至 infra 日志渠道。任何告警任务不会触发 `DocumentTaskAlertService`。

- [ ] **Step 4: 运行测试并提交。**

Run: `mvn -pl nexa-rag-document,nexa-rag-retrieval -am -Dtest=RocketMqDocumentPipelineDeadLetterConsumerTest,DocumentIndexCleanupDeadLetterConsumerTest "-Dsurefire.failIfNoSpecifiedTests=false" test`

Commit: `feat(document): 在任务终态失败后触发告警`

### Task 6: 提供文档任务查询与人工重试

**Files:**

- Create: `nexa-rag-document/src/main/java/com/nexarag/document/controller/DocumentTaskController.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/model/vo/DocumentTaskVO.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentPipelineOutboxService.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentPipelineOutboxServiceImpl.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/mapper/DocumentPipelineOutboxMapper.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentPipelineOutboxServiceImplTest.java`

- [ ] **Step 1: 写 FAILED 任务重试测试。**

```java
DocumentTaskVO retried = service.retryFailedTask(11L);
assertThat(retried.operationId()).isNotEqualTo(failed.getProcessId());
assertThat(retried.taskStatus()).isEqualTo(DocumentTaskStatus.PENDING);
```

- [ ] **Step 2: 实现查询和重试。**

重试只接受 `taskStatus=FAILED`，复制安全消息体和父关联，生成新 `outboxId`、operation ID、message key，发布/任务状态都初始化为 `PENDING`；`NOT_TRACKED` 与非失败任务抛出客户端异常。Controller 只返回脱敏失败原因，不返回消息全文或渠道凭据。

- [ ] **Step 3: 运行测试并提交。**

Run: `mvn -pl nexa-rag-document -am -Dtest=DocumentPipelineOutboxServiceImplTest "-Dsurefire.failIfNoSpecifiedTests=false" test`

Commit: `feat(document): 支持文档任务查询与人工重试`

### Task 7: 集成验证与文档同步

**Files:**

- Modify: `docs/adr/2026-08-07-document-task-outbox-index-cleanup.md`
- Modify: `docs/superpowers/specs/2026-08-07-document-task-outbox-alert-design.md`
- Modify: `docs/operations/structured-section-rebuild.md`（仅在接口或操作步骤变化时）

- [ ] **Step 1: 使用环境变量配置联调账户。**

```powershell
$env:NEXA_ALERT_FEISHU_WEBHOOK_URL = 'https://open.feishu.cn/open-apis/bot/v2/hook/申请后的值'
$env:NEXA_ALERT_SMTP_HOST = 'smtp.example.com'
$env:NEXA_ALERT_SMTP_PORT = '587'
$env:NEXA_ALERT_SMTP_USERNAME = '申请后的用户名'
$env:NEXA_ALERT_SMTP_PASSWORD = '申请后的密码'
$env:NEXA_ALERT_EMAIL_FROM = 'noreply@example.com'
$env:NEXA_ALERT_EMAIL_RECIPIENTS = 'ops@example.com'
```

- [ ] **Step 2: 运行模块测试与编译。**

Run: `mvn -pl nexa-rag-infra,nexa-rag-document,nexa-rag-retrieval -am test`

Expected: `BUILD SUCCESS`。

- [ ] **Step 3: 验证失败链路。**

制造一个索引清理失败，确认父任务为 `FAILED`、飞书和邮件各有一条子任务；让其中一渠道失败，确认另一渠道不被重发；让告警任务进入 DLQ，确认仅有结构化日志而没有新 Outbox。

- [ ] **Step 4: 同步实际 Topic、环境变量和人工重试接口后提交。**

Commit: `docs(alert): 补充告警运维说明`

## 自查结论

- 覆盖了 `infra` 告警边界、双渠道独立任务、DLQ 无递归、脱敏、配置、任务状态回调和管理员重试。
- 所有新增 MQ 消息、配置和展示对象均使用 `Message`、`Properties`、`VO` 后缀；数据库对象继续使用 `DocumentTaskOutboxDO`。
- 飞书和 SMTP 凭据只在任务 7 的环境变量联调步骤需要，代码和版本库不写入真实值。
