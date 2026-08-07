# 文档任务 Outbox 与最终失败告警设计

## 目标

将现有仅用于文档处理流水线发布的 `document_pipeline_outbox` 演进为通用的 `document_task_outbox`。它既可靠发布 RocketMQ 消息，也持久化追踪每个文档异步任务的最终处理结果；首批覆盖文档处理、文档外部索引清理，以及其最终失败后的飞书/邮件告警。

告警模型、渠道配置、飞书/邮件适配、RocketMQ 告警消费者和告警 DLQ 消费者统一归属 `infra`。`document` 只负责识别文档任务终态失败、在自身 Outbox 中创建独立告警任务，并实现告警投递的任务状态回调；`infra` 不得访问 `document_task_outbox` 或依赖 `document` 模块。

本设计替代“删除文档后发布 Spring 事件、提交后监听器清理外部索引”的实现。删除后的外部资源清理必须通过事务 Outbox + RocketMQ 最终一致性完成。

## 范围与非目标

### 范围

- 文档处理、索引清理、飞书告警、邮件告警四类文档任务。
- Outbox 发布状态与任务最终执行状态分离。
- 文档删除的异步返回契约、任务查询与管理员人工重试。
- 索引清理最终失败后的告警、幂等、DLQ 与人工补偿。
- `infra` 中的飞书/邮件后端配置、脱敏告警内容及告警可靠投递。

### 非目标

- 不在本期实现告警渠道的前端管理页；已列入 `TODO.md`。
- 不按严重级别分流 Webhook/收件人；首期使用统一全局目标。
- 不迁移或猜测旧 Outbox 历史消息的实际执行结果。
- 不让告警任务失败递归产生新的告警任务。
- 不改变 RAGAS 评测系统的后续规划。

## 领域模型

| 对象 | 标识 | 含义 | 不变量 |
| --- | --- | --- | --- |
| 文档任务 | `outboxId` | 一次可可靠发布和可追踪执行的文档异步任务。 | 一条记录只对应一个 `taskType` 与一个执行版本。 |
| 父任务 | `parentOutboxId` | 导致告警任务产生的原始失败任务。 | 只有告警任务可设置；不形成循环。 |
| 操作 ID | `operationId` | 业务执行版本。处理任务使用 `processId`；清理、告警使用新 UUID。 | 同一重试创建新操作 ID。 |
| 发布状态 | `publishStatus` | Outbox 到 RocketMQ 的投递生命周期。 | 不能代表消费者执行成功。 |
| 任务状态 | `taskStatus` | 消费者实际执行任务的最终生命周期。 | 只能由任务消费者或 DLQ 终态处理更新。 |
| 索引清理任务 | `CLEAN_DOCUMENT_INDEX` | 按文档 ID 删除 Milvus 正文向量、ES 正文索引和 ES 章节导航索引。 | 删除操作幂等；不恢复逻辑删除文档。 |
| 告警任务 | `SEND_FEISHU_FAILURE_ALERT` / `SEND_EMAIL_FAILURE_ALERT` | 向一个渠道投递一条最终失败通知。 | 一个渠道一条任务；失败不再生成告警任务。 |
| 告警投递回调 | `AlertDeliveryLifecycle` | 由业务模块实现的通用任务状态回调。 | `infra` 仅调用接口，不访问业务任务表。 |

### 任务类型

```text
PROCESS_DOCUMENT
CLEAN_DOCUMENT_INDEX
SEND_FEISHU_FAILURE_ALERT
SEND_EMAIL_FAILURE_ALERT
```

### 状态模型

```text
发布状态：PENDING → PUBLISHING → PUBLISHED
                         └──────→ FAILED

新任务状态：PENDING → PROCESSING → SUCCEEDED
                                └→ FAILED

历史迁移状态：NOT_TRACKED
```

- `publishStatus=FAILED`：消息未能可靠发布，任务没有被执行。
- `taskStatus=FAILED`：消息已发布但消费者重试耗尽，任务执行最终失败。
- `NOT_TRACKED`：迁移前历史记录；旧表只有发布事实，不足以推断执行结果，不参与新任务重试。

## 数据库演进

在现有结构化检索迁移之后新增下一版本 Flyway 迁移；实施时以仓库当前最大版本分配迁移号。

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
    ADD KEY idx_document_task_outbox_dispatch (task_type, publish_status, next_retry_time),
    ADD KEY idx_document_task_outbox_parent (parent_outbox_id),
    ADD KEY idx_document_task_outbox_status (task_type, task_status, update_time);
```

迁移后：

- 历史行保留 `task_type=PROCESS_DOCUMENT`、`task_status=NOT_TRACKED`。
- 新任务插入时显式写 `task_status=PENDING`，不得依赖数据库默认值。
- `message_key` 保持唯一；人工重试必须使用新 `operationId` 生成新 key。
- 完整 schema、实体、Mapper SQL、契约测试必须同步表名和字段名。

## 消息契约与 Topic

| taskType | Topic | 消费组 | 消息体 |
| --- | --- | --- | --- |
| `PROCESS_DOCUMENT` | `nexa-document-pipeline` | 既有 `nexa-document-pipeline-worker` | 既有文档处理消息。 |
| `CLEAN_DOCUMENT_INDEX` | `nexa-document-index-cleanup` | `nexa-document-index-cleanup-worker` | `outboxId`、`documentId`、`operationId`、`schemaVersion`。 |
| `SEND_FEISHU_FAILURE_ALERT` | `nexa-alert` | `nexa-alert-worker` | 脱敏的最终失败告警事件。 |
| `SEND_EMAIL_FAILURE_ALERT` | `nexa-alert` | `nexa-alert-worker` | 脱敏的最终失败告警事件。 |

所有消息使用 `documentId:taskType:operationId` 作为 RocketMQ key。告警任务额外包含 `parentOutboxId`，其消息体为 `infra` 定义的脱敏 `AlertMessage`：严重级别、渠道、任务标识、脱敏失败原因和时间等元数据均由 `document` 在创建告警任务时写入。`infra` 消费者不得回查 `document_task_outbox`，也不得接收正文、路径、提示词或凭据。

## 模块边界与投递链路

```text
document 终态失败 / 专属 DLQ
  ↓ 创建两条文档 Outbox 告警任务
document Outbox 发布器
  ↓ 发送 infra.AlertMessage 到 nexa-alert
infra.RocketMqAlertConsumer
  ├─ AlertDeliveryLifecycle.markProcessing（document 实现）
  ├─ AlertDispatcher → FeishuAlertChannel / EmailAlertChannel
  └─ AlertDeliveryLifecycle.markSucceeded（document 实现）
       ↓ 重试耗尽
infra.RocketMqAlertDeadLetterConsumer
  └─ AlertDeliveryLifecycle.markFailed（document 实现）+ 结构化错误日志
```

`infra` 定义 `AlertDeliveryLifecycle` SPI 和条件化的 RocketMQ 消费者；`document` 提供唯一实现，将告警 Outbox 状态更新为 `PROCESSING`、`SUCCEEDED` 或 `FAILED`。因此告警消费者可复用，但任务持久化边界仍归业务模块。

## 文档删除与索引清理

```text
DELETE 文档
  ↓ 同一数据库事务
逻辑删除 document
写 CLEAN_DOCUMENT_INDEX Outbox（PENDING）
  ↓ 提交成功
返回 deleted=true + cleanupOutboxId + cleanupStatus=PENDING
  ↓ RocketMQ
索引清理消费者
  ├─ Milvus：按 documentId 删除正文向量
  ├─ ES：按 documentId 删除正文索引
  └─ ES：按 documentId 删除章节导航索引
```

规则：

1. 外部删除必须幂等；不存在的索引记录按成功处理。
2. 消费前以条件更新将任务从 `PENDING`/可重试状态置为 `PROCESSING`；重复消息发现 `SUCCEEDED` 后直接确认。
3. 成功后更新 `SUCCEEDED`、`taskCompletedTime`，清空 `taskFailureReason`。
4. 可重试异常更新 `consumeRetryCount` 后继续抛出，由 RocketMQ 重试。
5. 清理消费者的 DLQ 消费者在重试耗尽后更新 `FAILED`、`taskFailureReason`，随后创建两条告警 Outbox 任务。
6. 清理最终失败后文档仍保持逻辑删除、对用户不可见；不得自动恢复。
7. 必须移除 Spring `DocumentDeletedEvent` 与提交后索引清理监听器，禁止保留双路径。

## 最终失败告警

### 触发

`PROCESS_DOCUMENT` 和 `CLEAN_DOCUMENT_INDEX` 在其专属终态处理器将任务写为 `FAILED` 后，分别创建：

```text
SEND_FEISHU_FAILURE_ALERT
SEND_EMAIL_FAILURE_ALERT
```

两个告警任务共享 `parentOutboxId`，使用不同的 `taskType` 与 `messageKey`。飞书成功、邮件失败时，只重试邮件。

### 告警级别

| 原任务 | 级别 |
| --- | --- |
| `PROCESS_DOCUMENT` | `WARNING` |
| `CLEAN_DOCUMENT_INDEX` | `ERROR` |

### 脱敏内容

包含：严重级别、任务类型、文档 ID、原任务/告警任务 Outbox ID、操作 ID、Topic、消费重试次数、最终失败时间、脱敏失败原因、管理端任务定位信息。

禁止包含：文档正文、文件路径、用户问题、提示词、Webhook URL、SMTP 凭据、任何访问密钥。

### 告警重试与 DLQ

- `infra` 告警消费者复用 RocketMQ `max-reconsume-times=5`，通过 `AlertDeliveryLifecycle` 更新告警任务状态。
- 告警任务成功后标记 `SUCCEEDED`。
- 重试耗尽后标记告警任务 `FAILED` 并输出结构化错误日志。
- 告警任务失败不得再创建告警任务。
- 管理员可单独人工重试失败的告警任务。

## 配置

首期只由后端读取 `infra` 配置。敏感值使用环境变量，示例：

```yaml
nexa:
  document:
    task:
      cleanup-topic: nexa-document-index-cleanup
      cleanup-consumer-group: nexa-document-index-cleanup-worker
  alert:
    enabled: true
    topic: nexa-alert
    consumer-group: nexa-alert-worker
    feishu:
      enabled: true
      webhook-url: ${NEXA_ALERT_FEISHU_WEBHOOK_URL:}
    email:
      enabled: true
      recipients: ${NEXA_ALERT_EMAIL_RECIPIENTS:}

spring:
  mail:
    host: ${NEXA_ALERT_SMTP_HOST:}
    port: ${NEXA_ALERT_SMTP_PORT:465}
    username: ${NEXA_ALERT_SMTP_USERNAME:}
    password: ${NEXA_ALERT_SMTP_PASSWORD:}
```

飞书 Webhook、SMTP 密码和收件人首期全局统一，不按级别或任务类型分流。前端管理属于后续 TODO：管理员权限、脱敏读取、敏感值加密保存、审计日志与测试通知必须一起设计。

## 管理接口

暂定管理员接口：

```text
GET  /api/document-tasks/{outboxId}
POST /api/document-tasks/{outboxId}/retry
```

- 查询返回发布状态、任务状态、失败原因、发布/消费重试次数、父任务关系和时间。
- 重试仅接受 `taskStatus=FAILED` 的任务；创建新执行版本、新 `operationId`、新 `messageKey` 和新的 `PENDING` Outbox 记录。
- 普通用户只从文档摘要获得“清理中/清理失败”状态，不获得 MQ 细节或重试能力。
- 该权限边界在 Sa-Token 与前端管理页实施时重新审查。

## 需要删除或替换的现有实现

- `DocumentDeletedEvent` 与 `DocumentDeletedEventListener`：删除。
- `DocumentServiceImpl.deleteDocument`：改为同一事务内逻辑删除 + 写 `CLEAN_DOCUMENT_INDEX` Outbox，返回异步清理响应。
- `DocumentPipelineOutbox*`：重命名为 `DocumentTaskOutbox*`，发布器按 `taskType` 反序列化和发送，不再假定所有消息都是处理流水线消息。
- `document.alert` 下的 `DocumentPipelineAlertService`、`DocumentPipelineFailureEvent` 与日志实现：迁移为 `infra.alert` 的领域无关模型与结构化日志能力。文档处理的非终态失败只记录结构化日志；飞书和邮件仅由任务终态失败创建的告警 Outbox 触发。

## 验收与可观测性

实施后至少验证：

1. 删除文档事务失败时，文档删除和清理 Outbox 同时回滚。
2. 删除成功时返回 `cleanupOutboxId=PENDING`，外部索引由独立 Topic 清理。
3. 重复清理消息不会失败或误删其他文档。
4. Milvus、ES 正文、ES 导航任一清理失败时重试；DLQ 后任务为 `FAILED`，文档仍不可见。
5. 一个终态失败创建飞书、邮件各一条告警任务；单渠道重试不重复另一个渠道。
6. 告警任务耗尽重试只记录自身失败，不递归告警。
7. 历史记录为 `NOT_TRACKED`，不可被新重试接口重试。
8. 日志/告警无正文、路径、问题、提示词与密钥。

系统日志至少记录 `outboxId`、`parentOutboxId`、`documentId`、`taskType`、发布状态、任务状态、重试次数、Topic 和脱敏失败原因。
