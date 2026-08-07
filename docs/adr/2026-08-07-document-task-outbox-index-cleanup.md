# ADR：复用文档任务 Outbox 清理外部索引

## 背景

文档删除后需要清理 Milvus 正文向量、Elasticsearch 正文索引和章节导航索引。Spring 事件不能保证可靠投递，也无法完整记录外部清理的最终结果。

现有 `document_pipeline_outbox` 已具备事务内入库、轮询发布、发布重试、分布式抢占和 RocketMQ 投递能力，但其命名与 `process_id` 字段只表达文档处理流水线。

## 决策

1. 将表演进为 `document_task_outbox`，统一承载文档异步任务；不新建第二张 Outbox 表。
2. 新增 `task_type`：首批为 `PROCESS_DOCUMENT` 与 `CLEAN_DOCUMENT_INDEX`。
3. 将 `process_id` 泛化为可空 `operation_id`：处理任务存处理轮次 ID，索引清理任务存删除操作 ID。
4. 保留 `publish_status` 等发布状态字段；新增独立的任务最终状态字段：`task_status`、`consume_retry_count`、`task_completed_time`、`task_failure_reason`。
5. 删除文档的数据库事务中同时完成文档逻辑删除和 `CLEAN_DOCUMENT_INDEX` Outbox 记录写入；事务提交后立即向调用方返回“删除成功、外部索引清理中”。
6. RocketMQ 消费者按 `document_id` 幂等删除 Milvus 正文向量、Elasticsearch 正文索引和章节导航索引。重复删除必须成功。
7. 发布成功不等于任务完成。消费者成功后写入 `SUCCEEDED`；失败时更新消费重试信息并交由 RocketMQ 重试；重试耗尽后写入 `FAILED`，保留失败原因供人工重试。
8. Outbox 表和发布器统一复用，但 Topic 与消费者隔离：既有处理任务继续使用 `nexa-document-pipeline`，索引清理任务使用独立 Topic `nexa-document-index-cleanup`。两类任务不得复用同一个消费者或失败状态机。
9. 索引清理使用独立消费组 `nexa-document-index-cleanup-worker`。RocketMQ 自动重试耗尽后，由该消费组专属的 DLQ 消费者将 Outbox 任务标记为 `FAILED`、写入 `task_failure_reason` 并触发告警；不得交给现有文档处理流水线的 DLQ 消费者。
10. 管理端按 `outbox_id` 重试 `FAILED` 任务：创建新的执行版本与 `message_key`，清空最终失败信息并重新投递。该动作不得重新删除文档、解析文档或重新走索引构建。
11. 将告警能力下沉到 `infra`：由 `infra` 定义领域无关的告警模型、严重级别、渠道适配、RocketMQ 告警消费者、DLQ 消费者及结构化日志；`infra` 不访问文档任务表或依赖 `document` 模块。告警事件至少包含 `outboxId`、`documentId`、`taskType`、`operationId`、`topic`、最终失败原因、消费重试次数、RocketMQ 消息 ID 和失败时间。仅在 DLQ 消费者将任务持久化为 `FAILED` 后触发一次最终失败告警；普通重试不告警。
12. 首期告警渠道为飞书机器人 Webhook 与邮件。两者使用同一份脱敏的任务最终失败事件；Webhook URL、SMTP 凭据、收件人等均通过环境变量或受保护配置注入，禁止写入版本库。
13. 任务进入 `FAILED` 后，创建独立、可重试的告警 Outbox 任务；告警任务以 `parent_outbox_id` 关联原失败任务。告警投递失败不影响原任务的最终失败状态，且告警任务自身失败不得再次创建告警任务，避免无限递归。
14. 飞书和邮件分别创建一条告警任务，例如 `SEND_FEISHU_FAILURE_ALERT` 与 `SEND_EMAIL_FAILURE_ALERT`。每条任务独立记录最终状态与重试次数，单个渠道失败不得导致另一个已成功渠道重复通知。
15. 告警渠道必须可配置。Webhook URL、SMTP 密码等敏感配置只可由后端通过环境变量或加密存储读取，禁止下发给前端；前端如需管理，只能调用后端受保护接口更新脱敏后的配置模型。
16. `PROCESS_DOCUMENT` 与 `CLEAN_DOCUMENT_INDEX` 两类任务在最终进入 `FAILED` 时都触发告警。每个失败任务分别创建飞书和邮件两条独立、可重试的告警任务。
17. 首期告警分级按任务类型确定：`PROCESS_DOCUMENT` 最终失败为 `WARNING`；`CLEAN_DOCUMENT_INDEX` 最终失败为 `ERROR`。两类告警均投递飞书和邮件，级别用于通知标题、日志检索和后续升级策略。
18. 首期飞书 Webhook 与邮件收件人使用统一全局配置，不按任务类型或严重级别分流。按严重级别配置通知目标作为后续能力，避免首期引入重复配置和复杂路由。
19. 飞书和邮件告警统一包含严重级别、任务类型、文档 ID、Outbox ID、操作 ID、Topic、消费重试次数、最终失败时间、脱敏后的失败原因以及管理端重试定位信息。`document` 创建 Outbox 时即写入该脱敏载荷，`infra` 消费者不得回查文档任务表。不得包含文档正文、文件路径、用户问题、提示词、Webhook URL、SMTP 凭据或其他敏感配置。
20. 飞书和邮件告警任务复用 RocketMQ 的 `max-reconsume-times=5`。`infra` 消费者通过 `AlertDeliveryLifecycle` SPI 回调 `document` 实现以更新告警任务状态；重试耗尽后将告警任务标记为 `FAILED` 并输出结构化错误日志；不得创建新的告警任务。管理员可按 `outbox_id` 手动重试失败的告警任务。
21. 删除接口采用异步清理返回契约，至少返回 `documentId`、`deleted=true`、`cleanupOutboxId` 与初始 `cleanupStatus=PENDING`。这只表示文档数据库状态已删除且清理任务已可靠入队，不表示外部索引已经清理完成。
22. 暂定仅管理员可查询和人工重试文档任务：`GET /api/document-tasks/{outboxId}` 返回发布与任务状态、失败原因和重试次数；`POST /api/document-tasks/{outboxId}/retry` 仅对 `FAILED` 任务创建新执行版本。普通知识库用户只见“清理中/清理失败”摘要。待接入 Sa-Token 和前端管理页时复核该权限边界。
23. `CLEAN_DOCUMENT_INDEX` 最终失败时，文档仍保持逻辑删除并对用户不可见；不得因外部索引清理失败自动恢复文档。告警、任务查询和管理员人工重试是唯一补偿路径，用户删除意图优先于外部索引的最终一致性。

建议的任务状态为：`PENDING`、`PROCESSING`、`SUCCEEDED`、`FAILED`。Outbox 发布状态继续独立表达 `PENDING`、`PUBLISHING`、`PUBLISHED`、`FAILED` 等发布生命周期。

## 理由

同一条 Outbox 记录可同时追踪“消息是否发布”和“外部索引是否真正清理”，使删除操作具备可靠投递、幂等消费、失败可追踪和人工补偿能力。文档数据库状态先对用户不可见，外部索引清理采用最终一致性，不让外部系统短暂不可用阻塞删除请求。

## 后果

- 现有 Outbox 实体、Mapper、发布器、消息体和消费者需从“处理流水线”抽象为“文档任务”。
- 新旧表重命名与字段迁移必须通过 Flyway 迁移执行，并同步完整 schema。
- 失败主题/死信消费不能复用现有“文档处理失败”状态机；索引清理失败需独立记录并提供人工重试入口。
- 删除 API 的同步成功仅代表数据库删除与清理任务已可靠入队，不代表外部索引已清理完成。
