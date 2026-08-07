# 文档任务 Outbox 与最终失败告警 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将文档处理 Outbox 演进为可追踪最终处理结果的文档任务 Outbox，并以 RocketMQ 异步、幂等地完成删除后的外部索引清理和飞书/邮件最终失败告警。

**Architecture:** 文档模块在本地事务中同时完成逻辑删除和 `CLEAN_DOCUMENT_INDEX` 任务入库；统一发布器根据任务类型、Topic 和消息体投递 RocketMQ。检索模块消费独立清理 Topic 并幂等删除三类索引；各任务的专属终态消费者负责写入最终状态，原始处理/清理任务失败时再以独立 Outbox 任务投递两个通知渠道。

**Tech Stack:** Java 21、Spring Boot 3、MyBatis-Plus、Flyway、RocketMQ、Elasticsearch、Milvus、Spring Mail、JUnit 5、Mockito、Vitest。

---

## 实施前约束

- 本计划以 [设计规格](../specs/2026-08-07-document-task-outbox-alert-design.md) 和 [ADR](../../adr/2026-08-07-document-task-outbox-index-cleanup.md) 为唯一产品决策来源；实现前不得将 Spring 事件保留为第二条索引清理路径。
- 当前工作区已有“结构化章节检索”的未提交改动。每次提交只暂存本计划涉及的文件，禁止借机格式化、回退或混入其他改动。
- 迁移版本必须在编码当日以 `db/migration` 的最大已存在版本为准。本文以当前 `V16` 为基线，下面写作 `V17__evolve_document_task_outbox.sql`；若该编号已被占用，顺延到下一个唯一版本号。
- `application.yml` 当前含本地开发配置；新增告警配置只能写环境变量占位符，不能复制或写入任何真实 Webhook、SMTP 密码或收件人。

## 文件结构与职责

| 路径 | 职责 |
| --- | --- |
| `nexa-rag-boot/src/main/resources/db/migration/V17__evolve_document_task_outbox.sql` | 重命名 Outbox 表、迁移历史记录并新增任务状态字段和索引。 |
| `nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql` | 新环境的最终 `document_task_outbox` 建表契约。 |
| `nexa-rag-infra/.../messaging/document/task/*` | 与业务无关的 RocketMQ 通用任务发布契约、消息和实现。 |
| `nexa-rag-infra/.../config/DocumentTaskMessagingProperties.java` | 清理/告警 Topic、消费者组和重试次数配置。 |
| `nexa-rag-document/.../outbox/*` | `DocumentTaskOutbox` 实体、Mapper、状态流转、发布抢占与人工重试。 |
| `nexa-rag-document/.../service/impl/DocumentPipelineSubmitServiceImpl.java` | 保持处理流水线行为，同时给新处理消息写入 `outboxId` 并追踪任务状态。 |
| `nexa-rag-document/.../service/impl/DocumentServiceImpl.java` | 删除事务内创建清理任务，替代 `DocumentDeletedEvent`。 |
| `nexa-rag-retrieval/.../messaging/*` | 独立的索引清理消费者和其 DLQ 终态处理器。 |
| `nexa-rag-document/.../alert/*` | 任务终态失败事件、渠道选择、飞书/邮件渲染和发送。 |
| `nexa-rag-document/.../controller/DocumentTaskController.java` | 管理员任务详情与失败任务人工重试接口。 |
| `nexa-rag-front/src/features/knowledge-base/api/document-api.ts` | 将删除接口返回类型更新为异步清理响应；不新增告警管理页面。 |

### Task 1: 建立数据库和任务领域契约

**Files:**

- Create: `nexa-rag-boot/src/main/resources/db/migration/V17__evolve_document_task_outbox.sql`
- Modify: `nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql:132-154`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/outbox/enums/DocumentTaskType.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/outbox/enums/DocumentTaskStatus.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/outbox/enums/TaskClaimResult.java`
- Create: `nexa-rag-document/src/test/java/com/nexarag/document/outbox/DocumentTaskSchemaContractTest.java`
- Rename: `nexa-rag-document/src/main/java/com/nexarag/document/outbox/enums/OutboxPublishStatus.java` to `.../DocumentTaskPublishStatus.java`

- [ ] **Step 1: 写出迁移和 schema 契约测试。**

  测试读取两个 SQL 文件并断言最终表名、字段名和历史状态迁移，不连接真实数据库：

  ```java
  @Test
  void migrationShouldKeepHistoricalTasksNotTracked() throws IOException {
      String migration = read("V17__evolve_document_task_outbox.sql");

      assertThat(migration).contains("RENAME TABLE document_pipeline_outbox TO document_task_outbox");
      assertThat(migration).contains("CHANGE COLUMN process_id operation_id VARCHAR(64) NULL");
      assertThat(migration).contains("ADD COLUMN task_status VARCHAR(32) NOT NULL DEFAULT 'NOT_TRACKED'");
      assertThat(migration).contains("ADD COLUMN parent_outbox_id BIGINT NULL");
  }
  ```

- [ ] **Step 2: 运行迁移契约测试，确认其因新文件不存在而失败。**

  Run: `mvn -pl nexa-rag-document -Dtest=DocumentTaskSchemaContractTest test`

  Expected: FAIL，提示找不到 `V17__evolve_document_task_outbox.sql` 或预期 SQL 片段。

- [ ] **Step 3: 实现可前滚的 Flyway 迁移和最终建表 SQL。**

  迁移必须保留历史记录，不重建或删除表；先重命名，再改字段、加字段、加索引。使用下列核心 SQL，并同步完整 schema：

  ```sql
  RENAME TABLE document_pipeline_outbox TO document_task_outbox;

  ALTER TABLE document_task_outbox
      CHANGE COLUMN process_id operation_id VARCHAR(64) NULL COMMENT '任务操作版本ID',
      CHANGE COLUMN failure_reason publish_failure_reason VARCHAR(1024) NULL COMMENT '消息发布失败原因',
      ADD COLUMN parent_outbox_id BIGINT NULL COMMENT '父任务Outbox ID' AFTER document_id,
      ADD COLUMN task_type VARCHAR(64) NOT NULL DEFAULT 'PROCESS_DOCUMENT' COMMENT '任务类型' AFTER operation_id,
      ADD COLUMN task_status VARCHAR(32) NOT NULL DEFAULT 'NOT_TRACKED' COMMENT '任务最终状态' AFTER publish_status,
      ADD COLUMN consume_retry_count INT NOT NULL DEFAULT 0 COMMENT '消费者执行重试次数' AFTER publish_retry_count,
      ADD COLUMN task_completed_time DATETIME NULL COMMENT '任务最终成功时间' AFTER published_time,
      ADD COLUMN task_failure_reason VARCHAR(1024) NULL COMMENT '任务最终失败原因' AFTER publish_failure_reason,
      ADD KEY idx_document_task_outbox_parent (parent_outbox_id),
      ADD KEY idx_document_task_outbox_status (task_type, task_status, update_time);
  ```

  保留原有 `(publish_status, next_retry_time)` 索引作为全类型发布扫描索引；不得另建同列重复索引。最终 schema 使用 `document_task_outbox`、`operation_id`、`publish_failure_reason` 和新字段，表注释改为“文档任务消息Outbox表”。

- [ ] **Step 4: 定义任务类型、发布状态和最终状态。**

  `DocumentTaskType` 仅包含以下四项；`DocumentTaskStatus` 仅包含以下五项，禁止用字符串散落在服务中：

  ```java
  public enum DocumentTaskType {
      PROCESS_DOCUMENT,
      CLEAN_DOCUMENT_INDEX,
      SEND_FEISHU_FAILURE_ALERT,
      SEND_EMAIL_FAILURE_ALERT;

      public boolean isAlertTask() {
          return this == SEND_FEISHU_FAILURE_ALERT || this == SEND_EMAIL_FAILURE_ALERT;
      }
  }

  public enum DocumentTaskStatus {
      NOT_TRACKED, PENDING, PROCESSING, SUCCEEDED, FAILED
  }
  ```

  将既有 `OutboxPublishStatus` 重命名为 `DocumentTaskPublishStatus`，值保持 `PENDING`、`PUBLISHING`、`PUBLISHED`、`FAILED`，以显式区分“发布失败”与“消费者最终失败”。

- [ ] **Step 5: 运行迁移契约测试。**

  Run: `mvn -pl nexa-rag-document -Dtest=DocumentTaskSchemaContractTest test`

  Expected: PASS。

- [ ] **Step 6: 提交数据库和枚举契约。**

  ```bash
  git add nexa-rag-boot/src/main/resources/db/migration/V17__evolve_document_task_outbox.sql nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql nexa-rag-document/src/main/java/com/nexarag/document/outbox/enums nexa-rag-document/src/test/java/com/nexarag/document/outbox/DocumentTaskSchemaContractTest.java
  git commit -m "feat(document): 演进文档任务Outbox表结构"
  ```

### Task 2: 泛化 Outbox 持久化状态机与可靠发布器

**Files:**

- Rename: `nexa-rag-document/src/main/java/com/nexarag/document/outbox/entity/DocumentPipelineOutbox.java` to `.../DocumentTaskOutbox.java`
- Rename: `nexa-rag-document/src/main/java/com/nexarag/document/outbox/mapper/DocumentPipelineOutboxMapper.java` to `.../DocumentTaskOutboxMapper.java`
- Rename: `nexa-rag-document/src/main/java/com/nexarag/document/outbox/service/DocumentPipelineOutboxService.java` to `.../DocumentTaskOutboxService.java`
- Rename: `nexa-rag-document/src/main/java/com/nexarag/document/outbox/service/impl/DocumentPipelineOutboxServiceImpl.java` to `.../DocumentTaskOutboxServiceImpl.java`
- Rename: `nexa-rag-document/src/main/java/com/nexarag/document/outbox/service/impl/DocumentPipelineOutboxPublisher.java` to `.../DocumentTaskOutboxPublisher.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/outbox/model/DocumentTaskCreateCommand.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/outbox/model/DocumentTaskRetryResult.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/outbox/model/FinalFailureResult.java`
- Modify: `nexa-rag-document/src/test/java/com/nexarag/document/outbox/service/impl/DocumentPipelineOutboxServiceImplTest.java`
- Modify: `nexa-rag-document/src/test/java/com/nexarag/document/outbox/service/impl/DocumentPipelineOutboxPublisherTest.java`

- [ ] **Step 1: 编写状态流转失败测试。**

  覆盖以下两个不可互换的行为：终态任务不能再次进入 `PROCESSING`；重试只能从 `FAILED` 创建新记录，且新操作 ID 与新 message key 均不同。

  ```java
  @Test
  void retryFailedTaskShouldCreateNewPendingExecution() {
      DocumentTaskOutbox failed = failedTask(18L, DocumentTaskType.CLEAN_DOCUMENT_INDEX);
      when(mapper.selectById(18L)).thenReturn(failed);

      DocumentTaskRetryResult result = service.retryFailedTask(18L);

      assertThat(result.newOutboxId()).isNotEqualTo(18L);
      assertThat(result.operationId()).isNotEqualTo(failed.getOperationId());
      verify(mapper).insert(argThat(task -> task.getTaskStatus() == DocumentTaskStatus.PENDING
              && task.getPublishStatus() == DocumentTaskPublishStatus.PENDING));
  }
  ```

- [ ] **Step 2: 运行服务测试，确认新增 API 尚不存在。**

  Run: `mvn -pl nexa-rag-document -Dtest=DocumentPipelineOutboxServiceImplTest test`

  Expected: FAIL，提示 `retryFailedTask` 或 `DocumentTaskStatus` 不存在。

- [ ] **Step 3: 重命名实体并实现明确的状态写入方法。**

  `DocumentTaskOutbox` 字段必须为：

  ```java
  private Long outboxId;
  private Long documentId;
  private Long parentOutboxId;
  private String operationId;
  private DocumentTaskType taskType;
  private String messageKey;
  private String topic;
  private String messageBody;
  private DocumentTaskPublishStatus publishStatus;
  private DocumentTaskStatus taskStatus;
  private Integer publishRetryCount;
  private Integer consumeRetryCount;
  private LocalDateTime taskCompletedTime;
  private String publishFailureReason;
  private String taskFailureReason;
  ```

  创建和状态结果的契约如下；`DocumentTaskCreateCommand` 不接收已序列化的敏感告警内容，调用方只能传入类型、定位字段、Topic 和无敏感的消息体：

  ```java
  public record DocumentTaskCreateCommand(Long documentId, Long parentOutboxId,
                                          DocumentTaskType taskType, String operationId,
                                          String topic, String messageBody) { }

  public enum TaskClaimResult { CLAIMED, ALREADY_SUCCEEDED, ALREADY_FAILED }

  public record FinalFailureResult(boolean transitioned, boolean childAlertsCreated) {
      static FinalFailureResult alreadyTerminal() { return new FinalFailureResult(false, false); }
      static FinalFailureResult failedWithoutChildAlerts() { return new FinalFailureResult(true, false); }
      static FinalFailureResult failedWithChildAlerts() { return new FinalFailureResult(true, true); }
  }
  ```

  服务接口至少提供 `createTask`、`claimPublishableMessages`、`markPublished`、`markPublishFailed`、`markProcessing`、`markSucceeded`、`markFinalFailed`、`getRequiredTask` 与 `retryFailedTask`。`markProcessing` 和 `markSucceeded` 使用条件更新：`SUCCEEDED`/`FAILED` 返回“无需处理”而非抛异常；同一消息的重复投递不应再次访问外部系统。

- [ ] **Step 4: 实现安全的最终失败和告警任务原子创建。**

  `markFinalFailed` 必须在同一 `REQUIRES_NEW` 事务中完成条件更新和两个子任务创建；仅原始处理/清理任务会创建通知任务：

  ```java
  boolean transitioned = mapper.markFinalFailedIfNonTerminal(
          outboxId, retryCount, truncate(reason), failureTime);
  if (!transitioned) {
      return FinalFailureResult.alreadyTerminal();
  }
  if (task.getTaskType().isAlertTask()) {
      return FinalFailureResult.failedWithoutChildAlerts();
  }
  insertAlertTask(task, DocumentTaskType.SEND_FEISHU_FAILURE_ALERT);
  insertAlertTask(task, DocumentTaskType.SEND_EMAIL_FAILURE_ALERT);
  return FinalFailureResult.failedWithChildAlerts();
  ```

  `message_key` 使用 `documentId:taskType:operationId`；告警任务的 `parentOutboxId` 为原任务 ID，消息体只保存 `outboxId`、`documentId`、`parentOutboxId`、`operationId`、`taskType`、`schemaVersion` 和创建时间。

- [ ] **Step 5: 实现人工重试复制规则。**

  `retryFailedTask` 读取旧记录后只接受 `taskStatus=FAILED`；为新行分配 UUID `operationId`，根据新值重建 message key/消息体，写入 `PENDING`、零重试次数和空失败原因。不得修改旧记录、不得重新执行删除动作、不得把 `NOT_TRACKED` 或 `publishStatus=FAILED` 当成可重试任务。

- [ ] **Step 6: 运行 Outbox 服务测试。**

  Run: `mvn -pl nexa-rag-document -Dtest=DocumentPipelineOutboxServiceImplTest test`

  Expected: PASS，包含发布抢占、终态幂等、子告警任务只创建一次及重试复制断言。

- [ ] **Step 7: 提交任务状态机。**

  ```bash
  git add nexa-rag-document/src/main/java/com/nexarag/document/outbox nexa-rag-document/src/test/java/com/nexarag/document/outbox
  git commit -m "feat(document): 增加文档任务Outbox状态追踪"
  ```

### Task 3: 提供按 Topic 发布的通用 RocketMQ 契约，并兼容处理流水线

**Files:**

- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/messaging/document/task/DocumentTaskMessage.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/messaging/document/task/DocumentTaskMessagePublisher.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/messaging/document/task/DocumentMessagePublishResult.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/messaging/document/task/rocketmq/RocketMqDocumentTaskMessagePublisher.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/config/DocumentTaskMessagingProperties.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/messaging/document/model/DocumentPipelineMessage.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/messaging/document/model/DocumentPipelineFailureMessage.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/outbox/service/impl/DocumentTaskOutboxPublisher.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentPipelineSubmitServiceImpl.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/messaging/RocketMqDocumentPipelineConsumer.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/messaging/RocketMqDocumentPipelineFailureConsumer.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/messaging/RocketMqDocumentPipelineDeadLetterConsumer.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/messaging/document/task/rocketmq/RocketMqDocumentTaskMessagePublisherTest.java`

- [ ] **Step 1: 编写通用发布器测试。**

  测试发送目的地使用记录中的 Topic、RocketMQ key 使用记录中的 message key，且 `SEND_OK` 之外的结果抛出 `ServiceException`：

  ```java
  verify(rocketMQTemplate).syncSend(
          eq("nexa-document-index-cleanup"),
          argThat(message -> "6:CLEAN_DOCUMENT_INDEX:operation-1".equals(
                  message.getHeaders().get(RocketMQHeaders.KEYS))));
  ```

- [ ] **Step 2: 运行基础设施发布器测试，确认类尚不存在。**

  Run: `mvn -pl nexa-rag-infra -Dtest=RocketMqDocumentTaskMessagePublisherTest test`

  Expected: FAIL，提示测试目标类不存在。

- [ ] **Step 3: 定义两种消息体并保持旧处理消息反序列化兼容。**

  清理/告警任务使用以下无敏感数据的通用消息；`DocumentPipelineMessage` 与失败消息追加可空 `outboxId`，旧 JSON 未携带该字段时可继续消费：

  ```java
  public record DocumentTaskMessage(Long outboxId, Long documentId, Long parentOutboxId,
                                    String operationId, String taskType,
                                    Integer schemaVersion, LocalDateTime createdTime) { }

  public record DocumentPipelineMessage(Long documentId, String processId, Long outboxId,
                                        Integer schemaVersion, LocalDateTime createdTime) { }
  ```

  新处理任务必须在构建消息体前获得 Outbox ID；因此 `DocumentPipelineSubmitServiceImpl` 先生成 ID、再创建消息和 Outbox 行。旧处理消息 `outboxId=null` 只更新原有文档处理状态，不写新的任务最终状态。

- [ ] **Step 4: 实现通用发布器和 Outbox 类型分派。**

  通用接口采用 `topic`、`messageKey` 和实际 POJO payload，不把所有消息序列化为字符串，确保既有 `RocketMqDocumentPipelineConsumer` 仍能接收 `DocumentPipelineMessage`：

  ```java
  public interface DocumentTaskMessagePublisher {
      DocumentMessagePublishResult publish(String topic, String messageKey, Object payload);
  }

  private Object deserializePayload(DocumentTaskOutbox task) throws JsonProcessingException {
      return switch (task.getTaskType()) {
          case PROCESS_DOCUMENT -> objectMapper.readValue(task.getMessageBody(), DocumentPipelineMessage.class);
          case CLEAN_DOCUMENT_INDEX, SEND_FEISHU_FAILURE_ALERT, SEND_EMAIL_FAILURE_ALERT ->
                  objectMapper.readValue(task.getMessageBody(), DocumentTaskMessage.class);
      };
  }
  ```

  删除旧的仅处理流水线发布接口和实现，避免同一 Outbox 任务被两个定时发布器扫描。保留现有处理 Topic、消费者组和业务失败 Topic 配置不变。

- [ ] **Step 5: 让处理流水线写入并更新任务状态。**

  处理消费者收到新消息时调用 `markProcessing(outboxId, reconsumeTimes + 1)`；工作流成功后调用 `markSucceeded(outboxId, now)`。不可重试异常、重试耗尽消息和 DLQ 消息都必须携带同一个 `outboxId`，终态处理器以它调用通用 `markFinalFailed`，再保留现有文档状态更新。

- [ ] **Step 6: 运行基础设施和文档流水线相关测试。**

  Run: `mvn -pl nexa-rag-infra,nexa-rag-document -am -Dtest=RocketMqDocumentTaskMessagePublisherTest,DocumentPipelineOutboxPublisherTest,DocumentPipelineSubmitServiceImplTest,RocketMqDocumentPipelineConsumerTest,RocketMqDocumentPipelineFailureConsumerTest,RocketMqDocumentPipelineDeadLetterConsumerTest test`

  Expected: PASS；旧 `outboxId=null` 消息可消费，新消息的任务状态能转为 `PROCESSING` 或 `SUCCEEDED`。

- [ ] **Step 7: 提交通用发布器与处理兼容改动。**

  ```bash
  git add nexa-rag-infra/src/main/java/com/nexarag/infra/config/DocumentTaskMessagingProperties.java nexa-rag-infra/src/main/java/com/nexarag/infra/messaging/document nexa-rag-infra/src/test/java/com/nexarag/infra/messaging/document nexa-rag-document/src/main/java/com/nexarag/document/outbox nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentPipelineSubmitServiceImpl.java nexa-rag-document/src/main/java/com/nexarag/document/messaging nexa-rag-document/src/test/java/com/nexarag/document
  git commit -m "feat(messaging): 按任务类型可靠发布文档消息"
  ```

### Task 4: 用清理 Outbox 替换删除 Spring 事件并更新删除契约

**Files:**

- Create: `nexa-rag-document/src/main/java/com/nexarag/document/vo/DocumentDeleteResponse.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentService.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentServiceImpl.java:288-301`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/controller/DocumentController.java:94-105`
- Remove: `nexa-rag-document/src/main/java/com/nexarag/document/event/DocumentDeletedEvent.java`
- Remove: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/listener/DocumentDeletedEventListener.java`
- Modify: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentServiceImplTest.java:200-225`
- Modify: `nexa-rag-front/src/features/knowledge-base/api/document-api.ts:117-119`
- Modify: `nexa-rag-front/src/features/knowledge-base/api/document-api.test.ts:43-50`
- Modify: `nexa-rag-front/src/features/knowledge-base/pages/KnowledgeBaseListPage.test.tsx:99`

- [ ] **Step 1: 用失败测试锁定“同一事务删除并入队”。**

  以 mock 的任务服务验证删除成功时只写一条清理任务，删除失败时一条也不写；不再验证 `ApplicationEventPublisher`：

  ```java
  @Test
  void deleteDocumentShouldCreatePendingCleanupTask() {
      DocumentDeleteResponse response = documentService.deleteDocument(1L);

      assertThat(response.deleted()).isTrue();
      assertThat(response.cleanupStatus()).isEqualTo(DocumentTaskStatus.PENDING);
      verify(taskOutboxService).createTask(argThat(command ->
              command.taskType() == DocumentTaskType.CLEAN_DOCUMENT_INDEX
                      && command.documentId().equals(1L)));
      verifyNoInteractions(eventPublisher);
  }
  ```

- [ ] **Step 2: 运行删除服务测试，确认旧事件断言失败。**

  Run: `mvn -pl nexa-rag-document -Dtest=DocumentServiceImplTest test`

  Expected: FAIL，旧测试仍期望 Spring 事件或删除返回 `boolean`。

- [ ] **Step 3: 实现删除事务和异步响应。**

  将服务返回类型改为 `DocumentDeleteResponse`，并在同一 `@Transactional` 方法内依次执行逻辑删除与 Outbox 插入。若 Outbox 插入失败，异常必须使逻辑删除回滚；若文档已删除或不存在，保持现有异常/幂等语义，不生成第二个清理任务。

  ```java
  public record DocumentDeleteResponse(Long documentId, boolean deleted,
                                       Long cleanupOutboxId, DocumentTaskStatus cleanupStatus) { }

  boolean deleted = logicDeleteDocument(documentId);
  if (!deleted) {
      return new DocumentDeleteResponse(documentId, false, null, null);
  }
  DocumentTaskOutbox task = taskOutboxService.createTask(cleanupCommand(documentId));
  return new DocumentDeleteResponse(documentId, true, task.getOutboxId(), task.getTaskStatus());
  ```

  构造清理命令时显式写 `PENDING`、`CLEAN_DOCUMENT_INDEX`、清理 Topic、UUID operation ID 和 `documentId:CLEAN_DOCUMENT_INDEX:operationId` message key。删除构造器注入的 `ApplicationEventPublisher`，并删除事件类/监听器及其测试。

- [ ] **Step 4: 同步 REST 和前端删除调用的类型。**

  控制器返回 `Result<DocumentDeleteResponse>`；前端定义同名 TypeScript 类型并让 `deleteDocument` 返回 `Promise<DocumentDeleteResponse>`。列表页仍以 `deleted=true` 作为刷新列表的条件，不将 `PENDING` 误显示为“索引已清理”。本期不创建任务管理或告警配置页面。

- [ ] **Step 5: 运行删除链路测试。**

  Run: `mvn -pl nexa-rag-document -Dtest=DocumentServiceImplTest test`

  Run: `npm --prefix nexa-rag-front test -- --run src/features/knowledge-base/api/document-api.test.ts src/features/knowledge-base/pages/KnowledgeBaseListPage.test.tsx`

  Expected: PASS；删除成功返回 `cleanupOutboxId` 和 `PENDING`，前端不再把返回值当成布尔值。

- [ ] **Step 6: 提交删除事务边界。**

  ```bash
  git add nexa-rag-document/src/main/java/com/nexarag/document nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentServiceImplTest.java nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/listener nexa-rag-front/src/features/knowledge-base
  git commit -m "feat(document): 删除文档时可靠创建索引清理任务"
  ```

### Task 5: 实现独立索引清理消费者、重试和 DLQ 终态处理

**Files:**

- Create: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/messaging/RocketMqDocumentIndexCleanupConsumer.java`
- Create: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/messaging/RocketMqDocumentIndexCleanupDeadLetterConsumer.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/service/impl/DocumentIndexCleanerImpl.java`
- Modify: `nexa-rag-retrieval/src/test/java/com/nexarag/retrieval/cleanup/DocumentIndexCleanerTest.java`
- Create: `nexa-rag-retrieval/src/test/java/com/nexarag/retrieval/messaging/RocketMqDocumentIndexCleanupConsumerTest.java`
- Create: `nexa-rag-retrieval/src/test/java/com/nexarag/retrieval/messaging/RocketMqDocumentIndexCleanupDeadLetterConsumerTest.java`
- Modify: `nexa-rag-boot/src/main/resources/application.yml`

- [ ] **Step 1: 编写清理消费者的成功、重复和部分失败测试。**

  ```java
  @Test
  void consumeShouldAcknowledgeAlreadySucceededTaskWithoutCleaningAgain() {
      when(taskOutboxService.markProcessing(41L, 2)).thenReturn(TaskClaimResult.ALREADY_SUCCEEDED);

      consumer.onMessage(cleanupMessage(41L, 6L));

      verifyNoInteractions(documentIndexCleaner);
  }

  @Test
  void consumeShouldThrowWhenAnyIndexCleanupFails() {
      when(taskOutboxService.markProcessing(41L, 1)).thenReturn(TaskClaimResult.CLAIMED);
      when(documentIndexCleaner.cleanup(6L)).thenReturn(failedCleanup(6L, "Milvus不可用"));

      assertThatThrownBy(() -> consumer.onMessage(cleanupMessage(41L, 6L)))
              .hasMessageContaining("Milvus不可用");
  }
  ```

- [ ] **Step 2: 运行消费者测试，确认类尚不存在。**

  Run: `mvn -pl nexa-rag-retrieval -am -Dtest=RocketMqDocumentIndexCleanupConsumerTest,RocketMqDocumentIndexCleanupDeadLetterConsumerTest test`

  Expected: FAIL，提示新消费者类不存在。

- [ ] **Step 3: 保持清理器“尝试全部介质”的行为，并让消费者决定重试。**

  `DocumentIndexCleanerImpl` 必须始终尝试以下三项：Milvus 正文向量、ES 正文索引、ES 章节导航索引。任一失败时返回失败聚合结果；消费者记录 `consumeRetryCount` 后抛出异常，交给 RocketMQ 重试。不存在的索引记录视为成功，禁止按“删除数量为零”判断失败。

- [ ] **Step 4: 实现独立 Topic 的清理消费者。**

  监听配置化的 `nexa-document-index-cleanup` 和 `nexa-document-index-cleanup-worker`。消费者先验证 `taskType=CLEAN_DOCUMENT_INDEX`、`outboxId` 和 `documentId`，再调用 `markProcessing`；成功后执行 `markSucceeded`。日志必须带 `outboxId`、`documentId`、`operationId`、`taskType` 与消费次数。

- [ ] **Step 5: 实现清理 DLQ 的最终失败处理。**

  DLQ 消费者只负责清理 Topic 的死信消息。它以 `outboxId` 调用 `markFinalFailed`，将 `reconsumeTimes + 1`、RocketMQ 消息 ID、脱敏后的异常摘要写入任务；服务在相同事务中创建飞书和邮件子任务。不得调用现有处理流水线的 `DocumentProcessFailureService`，不得恢复逻辑删除的文档。

- [ ] **Step 6: 增加配置并运行检索测试。**

  `application.yml` 只加入以下无密钥配置：

  ```yaml
  nexa:
    document:
      task:
        cleanup-topic: nexa-document-index-cleanup
        cleanup-consumer-group: nexa-document-index-cleanup-worker
        alert-topic: nexa-document-alert
        alert-consumer-group: nexa-document-alert-worker
        max-reconsume-times: 5
  ```

  Run: `mvn -pl nexa-rag-retrieval -am -Dtest=DocumentIndexCleanerTest,RocketMqDocumentIndexCleanupConsumerTest,RocketMqDocumentIndexCleanupDeadLetterConsumerTest test`

  Expected: PASS；重复消息不重复清理，失败消息抛出以触发重试，DLQ 只写最终失败。

- [ ] **Step 7: 提交索引清理消费链路。**

  ```bash
  git add nexa-rag-retrieval/src/main/java/com/nexarag/retrieval nexa-rag-retrieval/src/test/java/com/nexarag/retrieval nexa-rag-boot/src/main/resources/application.yml
  git commit -m "feat(retrieval): 消费文档索引清理任务"
  ```

### Task 6: 将处理流水线最终失败接入通用告警任务创建

**Files:**

- Rename: `nexa-rag-document/src/main/java/com/nexarag/document/alert/DocumentPipelineAlertService.java` to `.../DocumentTaskFailureAlertService.java`
- Rename: `nexa-rag-document/src/main/java/com/nexarag/document/alert/DocumentPipelineFailureEvent.java` to `.../DocumentTaskFinalFailureEvent.java`
- Rename: `nexa-rag-document/src/main/java/com/nexarag/document/alert/LoggingDocumentPipelineAlertService.java` to `.../LoggingDocumentTaskFailureAlertService.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentProcessFailureService.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/messaging/RocketMqDocumentPipelineDeadLetterConsumer.java`
- Modify: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentProcessFailureServiceTest.java`
- Modify: `nexa-rag-document/src/test/java/com/nexarag/document/alert/LoggingDocumentPipelineAlertServiceTest.java`

- [ ] **Step 1: 写出处理任务终态失败的原子性测试。**

  ```java
  @Test
  void markFinalFailureShouldCreateExactlyTwoAlertTasksAfterDocumentFailureIsPersisted() {
      when(documentService.markProcessFailed(anyLong(), anyString(), anyString(), anyString(), anyString(),
              anyInt(), anyString(), any())).thenReturn(true);
      when(taskOutboxService.markFinalFailed(eq(71L), anyInt(), anyString(), any()))
              .thenReturn(FinalFailureResult.failedWithChildAlerts());

      boolean updated = failureService.markFinalFailure(failureMessageWithOutboxId(71L));

      assertThat(updated).isTrue();
      verify(taskOutboxService).markFinalFailed(eq(71L), anyInt(), anyString(), any());
      verify(taskOutboxService, never()).createTask(any(DocumentTaskCreateCommand.class));
  }
  ```

- [ ] **Step 2: 运行失败服务测试，确认它仍调用旧的同步告警接口。**

  Run: `mvn -pl nexa-rag-document -Dtest=DocumentProcessFailureServiceTest,LoggingDocumentPipelineAlertServiceTest test`

  Expected: FAIL，旧接口/事件名不再匹配新测试。

- [ ] **Step 3: 实现通用终态失败协调服务。**

  `DocumentProcessFailureService.markFinalFailure` 的独立事务内先保持已有 `Document` 最终失败状态更新；当新消息携带 `outboxId` 时再调用 `DocumentTaskOutboxService.markFinalFailed`。服务创建的事件仅用于结构化日志，不直接向飞书或邮件发网络请求。

  严重级别固定映射：`PROCESS_DOCUMENT -> WARNING`、`CLEAN_DOCUMENT_INDEX -> ERROR`。通知任务自身的 `FAILED` 状态只写结构化日志，绝不再次调用 `markFinalFailed` 的子任务创建分支。

- [ ] **Step 4: 保持历史消息可处理。**

  当旧处理失败消息 `outboxId=null` 时，只执行现有文档失败状态更新和结构化日志；不通过文档 ID、process ID 回查某条 Outbox，不创建无法可靠归属的通知任务。

- [ ] **Step 5: 运行失败状态测试。**

  Run: `mvn -pl nexa-rag-document -Dtest=DocumentProcessFailureServiceTest,RocketMqDocumentPipelineDeadLetterConsumerTest,LoggingDocumentPipelineAlertServiceTest test`

  Expected: PASS；新处理任务最终失败产生一次、且仅一次两个告警子任务；历史消息不会被误关联。

- [ ] **Step 6: 提交通用最终失败处理。**

  ```bash
  git add nexa-rag-document/src/main/java/com/nexarag/document/alert nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentProcessFailureService.java nexa-rag-document/src/main/java/com/nexarag/document/messaging/RocketMqDocumentPipelineDeadLetterConsumer.java nexa-rag-document/src/test/java/com/nexarag/document/alert nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentProcessFailureServiceTest.java
  git commit -m "feat(document): 记录文档任务最终失败告警"
  ```

### Task 7: 实现飞书和邮件告警任务消费者及其 DLQ

**Files:**

- Modify: `nexa-rag-document/pom.xml`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/alert/config/DocumentAlertProperties.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/alert/model/DocumentTaskAlertPayload.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/alert/DocumentTaskAlertChannel.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/alert/FeishuDocumentTaskAlertChannel.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/alert/EmailDocumentTaskAlertChannel.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/messaging/RocketMqDocumentAlertConsumer.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/messaging/RocketMqDocumentAlertDeadLetterConsumer.java`
- Modify: `nexa-rag-boot/src/main/resources/application.yml`
- Create: `nexa-rag-document/src/test/java/com/nexarag/document/alert/FeishuDocumentTaskAlertChannelTest.java`
- Create: `nexa-rag-document/src/test/java/com/nexarag/document/alert/EmailDocumentTaskAlertChannelTest.java`
- Create: `nexa-rag-document/src/test/java/com/nexarag/document/messaging/RocketMqDocumentAlertConsumerTest.java`

- [ ] **Step 1: 写出告警渲染和脱敏测试。**

  ```java
  @Test
  void payloadShouldContainTaskLocatorButNotSensitiveContent() {
      DocumentTaskAlertPayload payload = renderer.render(failedCleanupTask(), parentTask());

      assertThat(payload.content()).contains("ERROR", "CLEAN_DOCUMENT_INDEX", "outboxId=71");
      assertThat(payload.content()).doesNotContain("/private/files/demo.pdf", "文档正文", "webhook", "smtp-password");
  }
  ```

- [ ] **Step 2: 运行通知通道测试，确认类尚不存在。**

  Run: `mvn -pl nexa-rag-document -Dtest=FeishuDocumentTaskAlertChannelTest,EmailDocumentTaskAlertChannelTest,RocketMqDocumentAlertConsumerTest test`

  Expected: FAIL，提示通道和消费者类型不存在。

- [ ] **Step 3: 添加邮件依赖和受校验配置。**

  在 `nexa-rag-document/pom.xml` 增加 `spring-boot-starter-mail`。`DocumentAlertProperties` 使用 `nexa.alert` 前缀并拥有 `feishu.enabled/webhookUrl`、`email.enabled/recipients`。若渠道 `enabled=true`，启动时必须校验其必要配置非空；不允许创建“明知没有接收端”的可重试任务。

  ```yaml
  nexa:
    alert:
      feishu:
        enabled: true
        webhook-url: ${NEXA_ALERT_FEISHU_WEBHOOK_URL:}
      email:
        enabled: true
        recipients: ${NEXA_ALERT_EMAIL_RECIPIENTS:}
  spring:
    mail:
      host: ${NEXA_ALERT_SMTP_HOST:}
      port: ${NEXA_ALERT_SMTP_PORT:587}
      username: ${NEXA_ALERT_SMTP_USERNAME:}
      password: ${NEXA_ALERT_SMTP_PASSWORD:}
  ```

- [ ] **Step 4: 实现渠道隔离和统一脱敏消息。**

  告警消费者按 `taskType` 精确选择一个通道，加载父任务后渲染消息；不从消息体读取或打印原始异常详情。飞书使用 `RestClient` 向配置 URL 发送 text markdown；邮件使用 `JavaMailSender` 发送纯文本。HTTP 非 2xx、邮件发送异常均抛出以触发 RocketMQ 重试。

  告警正文包含级别、任务类型、文档 ID、原始/告警 Outbox ID、operation ID、Topic、消费次数、最终失败时间、截断脱敏原因和 `GET /api/document-tasks/{parentOutboxId}` 定位信息；禁止包含正文、路径、问题、提示词和任何凭据。

- [ ] **Step 5: 实现告警成功状态与 DLQ。**

  `RocketMqDocumentAlertConsumer` 成功发送后调用 `markSucceeded(alertOutboxId, now)`；其专属 DLQ 消费者调用 `markFinalFailed` 的“告警任务”分支，写入 `FAILED` 和结构化错误日志。该分支不得创建子任务，且不会让飞书成功的任务重复发送。

- [ ] **Step 6: 运行通知测试。**

  Run: `mvn -pl nexa-rag-document -Dtest=FeishuDocumentTaskAlertChannelTest,EmailDocumentTaskAlertChannelTest,RocketMqDocumentAlertConsumerTest test`

  Expected: PASS；两个渠道独立成功/失败，文本脱敏，告警 DLQ 不递归创建任务。

- [ ] **Step 7: 提交通知渠道。**

  ```bash
  git add nexa-rag-document/pom.xml nexa-rag-document/src/main/java/com/nexarag/document/alert nexa-rag-document/src/main/java/com/nexarag/document/messaging/RocketMqDocumentAlertConsumer.java nexa-rag-document/src/main/java/com/nexarag/document/messaging/RocketMqDocumentAlertDeadLetterConsumer.java nexa-rag-document/src/test/java/com/nexarag/document/alert nexa-rag-document/src/test/java/com/nexarag/document/messaging/RocketMqDocumentAlertConsumerTest.java nexa-rag-boot/src/main/resources/application.yml
  git commit -m "feat(alert): 投递文档任务飞书和邮件告警"
  ```

### Task 8: 提供管理员任务查询和人工重试接口

**Files:**

- Create: `nexa-rag-document/src/main/java/com/nexarag/document/vo/DocumentTaskVO.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/vo/DocumentTaskRetryVO.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/controller/DocumentTaskController.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/outbox/service/DocumentTaskOutboxService.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/outbox/service/impl/DocumentTaskOutboxServiceImpl.java`
- Create: `nexa-rag-document/src/test/java/com/nexarag/document/controller/DocumentTaskControllerTest.java`

- [ ] **Step 1: 编写控制器契约测试。**

  ```java
  @Test
  void retryShouldReturnNewExecutionInsteadOfMutatingFailedTask() {
      when(taskService.retryFailedTask(71L)).thenReturn(new DocumentTaskRetryResult(72L, "new-operation"));

      Result<DocumentTaskRetryVO> result = controller.retry(71L);

      assertThat(result.getData().outboxId()).isEqualTo(72L);
      assertThat(result.getData().taskStatus()).isEqualTo(DocumentTaskStatus.PENDING);
  }
  ```

- [ ] **Step 2: 运行控制器测试，确认接口不存在。**

  Run: `mvn -pl nexa-rag-document -Dtest=DocumentTaskControllerTest test`

  Expected: FAIL，提示 `DocumentTaskController` 不存在。

- [ ] **Step 3: 实现最小的管理端 REST 契约。**

  ```text
  GET  /api/document-tasks/{outboxId}
  POST /api/document-tasks/{outboxId}/retry
  ```

  `DocumentTaskVO` 返回任务 ID、父任务 ID、文档 ID、操作 ID、类型、Topic、发布/任务状态、两类重试次数、发布时间/完成时间和两类失败原因。重试响应返回新 Outbox ID、operation ID 和 `PENDING`。接口在 Sa-Token 接入前通过清晰的 `@PreAuthorize` 预留管理员权限表达式或现有管理权限机制保护；若项目当前尚无可用权限组件，控制器不伪造鉴权，而是在上线配置中明确标记为内部管理接口并在接入 Sa-Token 的同一变更中强制保护。

- [ ] **Step 4: 运行管理接口测试。**

  Run: `mvn -pl nexa-rag-document -Dtest=DocumentTaskControllerTest,DocumentPipelineOutboxServiceImplTest test`

  Expected: PASS；`NOT_TRACKED`、`PENDING`、`PROCESSING`、`SUCCEEDED` 任务重试均返回业务错误，只有 `FAILED` 创建新执行版本。

- [ ] **Step 5: 提交管理接口。**

  ```bash
  git add nexa-rag-document/src/main/java/com/nexarag/document/controller/DocumentTaskController.java nexa-rag-document/src/main/java/com/nexarag/document/vo/DocumentTaskVO.java nexa-rag-document/src/main/java/com/nexarag/document/vo/DocumentTaskRetryVO.java nexa-rag-document/src/main/java/com/nexarag/document/outbox nexa-rag-document/src/test/java/com/nexarag/document/controller/DocumentTaskControllerTest.java
  git commit -m "feat(document): 支持查询和重试失败文档任务"
  ```

### Task 9: 完成运行文档、全量验证和发布前检查

**Files:**

- Modify: `docs/adr/2026-08-07-document-task-outbox-index-cleanup.md`
- Modify: `docs/superpowers/specs/2026-08-07-document-task-outbox-alert-design.md`
- Create: `docs/operations/document-task-outbox-runbook.md`

- [ ] **Step 1: 写运行手册的可执行排障内容。**

  手册必须列出：迁移前备份校验；四个任务类型与两类状态含义；处理、清理、告警 Topic/消费者组；如何用 `GET` 查询任务；如何只对 `FAILED` 任务执行 `POST retry`；如何在 RocketMQ 控制台定位 DLQ；以及“清理失败时文档仍保持逻辑删除”的补偿边界。不得记录任何真实连接串、Webhook 或密码。

- [ ] **Step 2: 同步 ADR/规格边界。**

  将实际类名、Flyway 迁移号、告警配置键和 API 响应与规格同步。确认项目既有待办仍将前端告警配置管理（权限、加密、审计、脱敏读取、测试通知）与按严重级别路由通知目标标记为后续范围；不得暗示这些能力已在本期实现。

- [ ] **Step 3: 运行最相关的全模块回归。**

  Run: `mvn -pl nexa-rag-infra,nexa-rag-document,nexa-rag-retrieval -am test`

  Run: `npm --prefix nexa-rag-front test -- --run src/features/knowledge-base/api/document-api.test.ts src/features/knowledge-base/pages/KnowledgeBaseListPage.test.tsx`

  Expected: PASS。

- [ ] **Step 4: 做一次连接到隔离环境的验收演练。**

  使用专用测试文档和隔离的 MySQL/RocketMQ/Milvus/ES：删除后检查数据库文档逻辑删除和一个 `CLEAN_DOCUMENT_INDEX/PENDING` 任务；等待消费后检查三类索引都已无该文档、任务为 `SUCCEEDED`；人为让一个索引不可用并确认 DLQ 后原任务 `FAILED`、两个子告警任务独立创建；恢复服务后仅重试失败渠道或清理任务。

- [ ] **Step 5: 运行变更检查并提交文档。**

  Run: `git diff --check`

  Run: `git status --short`

  Expected: 无空白错误；暂存区仅含本计划的文档文件。

  ```bash
  git add docs/adr/2026-08-07-document-task-outbox-index-cleanup.md docs/superpowers/specs/2026-08-07-document-task-outbox-alert-design.md docs/operations/document-task-outbox-runbook.md
  git commit -m "docs(operations): 补充文档任务Outbox运行手册"
  ```

## 计划自检

| 规格要求 | 对应任务 |
| --- | --- |
| 复用并重命名 Outbox、历史记录 `NOT_TRACKED`、发布与执行状态分离 | Task 1、Task 2 |
| 保持处理流水线兼容并可追踪新处理任务状态 | Task 3、Task 6 |
| 删除事务内入队、异步返回、移除 Spring 事件 | Task 4 |
| 独立清理 Topic/消费组、三类索引幂等删除、DLQ 不恢复文档 | Task 5 |
| 最终失败创建独立飞书/邮件任务，不递归 | Task 2、Task 6、Task 7 |
| 后端环境变量配置、告警脱敏、5 次 RocketMQ 重试 | Task 5、Task 7 |
| 管理员查询与只重试 `FAILED` 的新执行版本 | Task 2、Task 8 |
| 前端不误报“已清理”、告警管理页和分级路由留在后续范围 | Task 4、Task 9 |

已检查：本计划未使用未定义的任务类型、状态值、Topic 或核心服务方法；所有 `outboxId` 相关状态更新均以任务 ID 为归属，不用文档 ID/处理流水号进行猜测；所有外部副作用均在 RocketMQ 消费者中发生。
