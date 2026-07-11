# 文档流水线 RocketMQ 迁移设计

## 1. 设计目标

将现有 Redis 公平队列、本地轮询 Worker 和节点内部重试机制统一迁移为 RocketMQ 驱动的可靠文档流水线，同时保留现有 Workflow Graph 作为解析、分块和索引阶段的业务编排器。

本次改造需要达到以下目标：

- 文件上传成功后自动进入文档流水线，不再区分手动和自动执行模式。
- 使用 RocketMQ 承担消息投递、消费重试和死信处理。
- 使用 Outbox 保证数据库状态与消息发布的一致性。
- 使用 `processId` 隔离不同处理轮次，避免旧消息覆盖人工重试后的新状态。
- 节点只处理阶段业务，异常直接向上抛出，不再自行重新排队。
- 删除精确排队位置、Redis 租约、本地 Worker 及其相关接口和配置。
- 首期提供结构化日志告警，并为钉钉、企业微信和邮件告警预留统一接口。
- 为后续 Redis Stream 和 RocketMQ 事务消息适配保留扩展点。

## 2. 总体架构

文档上传接口同步完成文件持久化，然后在数据库事务中创建文档记录和 Outbox 消息。Outbox 发布器将消息发送到 RocketMQ，Consumer 收到消息后调用 Workflow Graph，根据数据库当前状态从对应节点继续执行。

```text
客户端上传文件
  -> 校验并同步保存 MinIO
  -> 数据库事务：创建文档 + 生成 processId + 写入 Outbox
  -> 返回 QUEUED
  -> Outbox 发布器
  -> RocketMQ 正常 Topic
  -> RocketMQ Consumer
  -> Workflow Graph
  -> 解析节点 -> 分块节点 -> 索引节点
  -> 成功后 ACK

节点异常
  -> Consumer 抛出异常
  -> RocketMQ 自动重试
  -> 超过上限进入 DLQ
  -> 失败 Consumer 更新 FAILED 并告警
```

## 3. 文件上传流程

文件内容不进入 MQ。HTTP 请求结束前必须先可靠保存原始文件。

上传流程：

1. 校验文件是否为空、文件名、类型和大小。
2. 同步保存原始文件到 MinIO。
3. MinIO 临时异常最多重试三次，退避时间依次为 200 毫秒、500 毫秒和 1 秒。
4. 在同一数据库事务中创建文档、生成 `processId`、设置 `QUEUED` 并写入 `PENDING` Outbox。
5. 数据库事务提交后返回上传结果。
6. MinIO 成功但数据库事务失败时，补偿删除本次上传产生的对象。
7. Outbox 发布失败不影响上传接口结果，由后台发布器继续重试。

上传响应调整为：

```java
public record UploadDocumentResponse(
        Long documentId,
        String processId,
        DocumentStatus status) {
}
```

## 4. 消息模型与 Topic

文档流水线使用单 Topic 驱动完整 Workflow Graph，不为解析、分块和索引阶段分别创建 Topic。

正常消息模型：

```java
public record DocumentPipelineMessage(
        Long documentId,
        String processId,
        Integer schemaVersion,
        LocalDateTime createdTime) {
}
```

消息资源：

```text
正常 Topic：nexa-document-pipeline
业务失败 Topic：nexa-document-pipeline-failure
正常 Consumer Group：nexa-document-pipeline-worker
失败 Consumer Group：nexa-document-pipeline-failure-handler
Producer Group：nexa-document-pipeline-producer
```

消息 Key 使用：

```text
documentId:processId
```

## 5. 处理轮次与幂等

`document` 表新增以下当前处理轮次字段：

```text
process_id：当前处理轮次ID
message_status：当前轮次消息状态
consumed_times：当前轮次已消费次数
last_message_id：最近一次消费的 RocketMQ 消息ID
```

首次提交和人工重试都生成新的 `processId`，MQ 自动重试保持原 `processId`。人工重试时同时重置消息状态和消费次数。

Consumer 执行规则：

- 消息 `processId` 与数据库一致：允许继续处理。
- 消息 `processId` 与数据库不一致：判定为旧轮次消息，直接 ACK。
- 文档已为 `INDEXED` 且处理轮次一致：判定为幂等完成，直接 ACK。
- 文档已为 `FAILED`：普通 Consumer 不再执行。

各阶段必须保证幂等：

- 解析阶段使用稳定对象名，重复执行覆盖或复用同一解析结果。
- 分块阶段在数据库事务内替换全部有效片段。
- Milvus 使用 `chunkId` 作为主键执行 upsert。
- Elasticsearch 使用 `chunkId` 作为文档 `_id` 执行 PUT。
- 状态更新使用 `documentId + processId + 当前状态` 作为条件。

## 6. Workflow 状态机

节点失败后不再把文档状态改回 `QUEUED`。RocketMQ 负责消费重试，文档状态保留当前真实执行阶段。

状态流转：

```text
QUEUED   -> PARSING
PARSING  -> PARSED
PARSED   -> CHUNKING
CHUNKING -> CHUNKED
CHUNKED  -> INDEXING
INDEXING -> INDEXED
```

Workflow Router 路由规则：

```text
QUEUED / PARSING   -> 解析节点
PARSED / CHUNKING  -> 分块节点
CHUNKED / INDEXING -> 索引节点
INDEXED            -> END
FAILED             -> END
```

节点职责统一为：

1. 使用条件更新推进到当前执行中状态。
2. 执行当前阶段业务。
3. 成功后使用条件更新推进到阶段完成状态。
4. 失败直接抛出异常，由 MQ Consumer 决定重试或死信。

现有 `DocumentStatusRouterDispatcher`、`DocumentNodeDispatcher` 和 `DocumentStatusRouterNode` 属于 Workflow Graph 内部路由，继续保留。

## 7. RocketMQ 重试与死信

首期使用 `rocketmq-spring-boot-starter:2.3.1`。该 Starter 实际传递引入 RocketMQ Client 5.3.0，并已有 Spring Boot 3.5、Java 21 项目正常启动先例。

消费最大重试次数为五次。异常分为：

- 可重试异常：网络超时、连接失败、限流、模型服务临时异常、外部服务 5xx。
- 不可重试异常：处理配置非法、文件永久丢失、向量维度错误、状态数据损坏。

处理规则：

- 可重试异常向上抛出，由 RocketMQ 自动重新投递。
- 不可重试异常发布到业务失败 Topic，原消息处理完成。
- 超过 RocketMQ 最大重试次数后进入 Consumer Group 对应 DLQ。
- 业务失败 Consumer 和 DLQ Consumer 复用同一个最终失败处理服务。
- 最终失败处理必须校验 `processId`，仅允许当前处理轮次进入 `FAILED`。
- 最终失败状态和告警事件持久化成功后才确认失败消息。

普通 MQ 重试期间不调用现有 `DocumentProcessFailureService`，避免 MQ 重试和数据库自动重试重复计数。最终失败处理需要使用明确的 `markProcessFailed` 语义。

## 8. 人工重试

继续使用现有接口：

```text
POST /api/documents/{documentId}/retry
```

人工重试流程：

1. 仅允许 `FAILED` 文档重试。
2. 生成新的 `processId`。
3. 清理失败信息和上一轮消息状态。
4. 在同一数据库事务中将文档更新为 `QUEUED` 并写入新 Outbox。
5. 旧轮次消息因 `processId` 不匹配而直接 ACK。

## 9. Outbox 设计

提交文档处理时，在同一数据库事务内更新文档状态并写入 Outbox。

Outbox 状态：

```text
PENDING：等待发布
PUBLISHING：已被发布器实例抢占
PUBLISHED：Broker 已确认接收
FAILED：发布重试达到上限
```

建议表结构：

```sql
CREATE TABLE document_pipeline_outbox (
    outbox_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    process_id VARCHAR(64) NOT NULL,
    message_key VARCHAR(128) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    message_body TEXT NOT NULL,
    publish_status VARCHAR(32) NOT NULL,
    publish_retry_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME NULL,
    lock_owner VARCHAR(128) NULL,
    lock_time DATETIME NULL,
    published_time DATETIME NULL,
    failure_reason VARCHAR(1024) NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    PRIMARY KEY (outbox_id),
    UNIQUE KEY uk_document_pipeline_outbox_message_key (message_key),
    KEY idx_document_pipeline_outbox_publish_task (publish_status, next_retry_time)
);
```

正式 SQL 必须包含简体中文表注释、字段注释和索引说明。

发布器规则：

1. 批量查询 `PENDING` 且已到重试时间的记录。
2. 使用条件更新抢占为 `PUBLISHING`，避免多实例重复发送。
3. 发送成功后更新为 `PUBLISHED`。
4. 发送失败后增加重试次数并计算下一次重试时间。
5. 达到发布上限后更新为 `FAILED` 并告警。
6. `PUBLISHING` 超过抢占超时时间后允许其他实例重新抢占。

首期使用 Outbox。配置中预留 `transaction-message`，后续可使用 RocketMQ 事务消息替换发布机制。

## 10. 消息适配抽象与包结构

`nexa-rag-infra` 使用领域优先包结构：

```text
com.nexarag.infra.messaging.document
├── DocumentPipelineMessagePublisher.java
├── DocumentPipelineMessageHandler.java
├── model
├── config
├── rocketmq
└── redisstream
```

首期实现 RocketMQ Publisher 和 Consumer。Redis Stream 只保留配置枚举，不提供不可用的空实现。

Outbox 属于文档业务事务，放在：

```text
com.nexarag.document.outbox.entity
com.nexarag.document.outbox.mapper
com.nexarag.document.outbox.service
com.nexarag.document.outbox.service.impl
```

Workflow 模块实现 `DocumentPipelineMessageHandler`，负责校验处理轮次并调用 Graph。

## 11. 处理状态查询

删除精确排队位置能力，不再返回以下字段：

```text
queuePosition
waitingCount
running
workerId
leaseTtlSeconds
```

保留处理状态查询接口：

```text
GET /api/documents/{documentId}/process-status
```

返回数据库文档状态和可靠消息状态：

```java
public record DocumentProcessStatusVO(
        Long documentId,
        String processId,
        DocumentStatus status,
        DocumentPipelineMessageStatus messageStatus,
        Integer consumedTimes,
        String failureStage,
        String failureReason) {
}
```

消息状态包括：

```text
PENDING_PUBLISH
PUBLISHED
PROCESSING
RETRYING
FAILED
COMPLETED
```

正常 Consumer 使用 `MessageExt` 获取 `msgId` 和 `reconsumeTimes`，反序列化业务消息后再委托 `DocumentPipelineMessageHandler`。每次收到当前处理轮次消息时，更新 `message_status`、`consumed_times` 和 `last_message_id`。RocketMQ 不提供稳定的单消息下次消费时间查询，因此状态接口不返回 `nextRetryTime`。

## 12. 外部索引和清理

Retrieval 节点不再维护自己的自动重试。Embedding、Milvus 和 Elasticsearch 异常向上抛给 MQ Consumer。

Elasticsearch 客户端捕获异常时必须保留原始异常对象。非成功响应应记录经过长度限制的响应内容，且不得输出认证信息。

文档索引清理需要分别执行向量索引和关键词索引清理：

- 一个清理阶段失败后仍继续执行另一个阶段。
- 聚合两个阶段的删除数量、成功状态和失败原因。
- 不允许吞掉异常后返回整体成功。

## 13. 告警

定义统一告警接口：

```java
public interface DocumentPipelineAlertService {

    void alert(DocumentPipelineFailureEvent event);
}
```

首期提供结构化日志告警实现，事件包含：

```text
documentId
processId
failureStage
failureReason
failureDetail
consumedTimes
messageId
failureTime
```

钉钉、企业微信和邮件告警实现记录到 `TODO.md`，不在首期实现。

## 14. 删除范围

删除 `nexa-rag-infra` 中整个旧队列包：

```text
com.nexarag.infra.queue.document
```

删除本地 Worker：

```text
LocalDocumentPipelineWorker
DocumentPipelineWorkerProperties
```

删除文档队列和排队状态相关类：

```text
DocumentProcessTaskDispatcher
LocalDocumentProcessTaskDispatcher
RedisDocumentProcessTaskDispatcher
DocumentQueueInfo
DocumentQueueStatusService
DocumentQueueStatusServiceImpl
```

`DocumentPipelineTriggerService` 重命名为 `DocumentPipelineSubmitService`，职责调整为处理轮次提交和 Outbox 写入。

同步删除相关单元测试、集成测试、响应字段和 `application.yml` 配置。

历史设计文档继续保留，新设计文档说明旧 Redis 队列方案已被替代。

## 15. 配置设计

RocketMQ 使用标准 Starter 配置：

```yaml
rocketmq:
  # RocketMQ NameServer 地址；多个地址使用分号分隔，可通过环境变量覆盖。
  name-server: ${ROCKETMQ_NAME_SERVER:118.195.146.161:8082}
  producer:
    # 文档流水线生产者组。
    group: nexa-document-pipeline-producer
    # 同步发送超时时间，单位：毫秒。
    send-message-timeout: 5000
    # 同步发送失败后的额外重试次数。
    retry-times-when-send-failed: 2
```

业务配置：

```yaml
nexa:
  document:
    pipeline:
      messaging:
        # 消息中间件类型，可选值：rocketmq、redis-stream；首期仅支持 rocketmq。
        type: rocketmq
        # 可靠发布模式，可选值：outbox、transaction-message；首期仅支持 outbox。
        publish-mode: outbox
        topic: nexa-document-pipeline
        failure-topic: nexa-document-pipeline-failure
        consumer-group: nexa-document-pipeline-worker
        failure-consumer-group: nexa-document-pipeline-failure-handler
        max-reconsume-times: 5
      outbox:
        batch-size: 100
        poll-interval-ms: 1000
        publishing-timeout-seconds: 60
        max-publish-retries: 10
      upload-retry:
        max-attempts: 3
        backoff-delays-ms: 200,500,1000
```

正式 `application.yml` 中每个配置项必须使用简体中文说明用途、可选值、默认值、单位和约束关系。

删除旧配置：

```text
nexa.document.pipeline.mode
nexa.document.pipeline.queue-mode
nexa.document.pipeline.worker-enabled
nexa.document.pipeline.max-concurrency
nexa.document.pipeline.poll-interval-ms
nexa.document.pipeline.lease-ttl-seconds
nexa.document.pipeline.queue.*
```

## 16. SQL 文件要求

新增 Flyway 迁移：

```text
nexa-rag-boot/src/main/resources/db/migration/V11__add_document_pipeline_messaging.sql
```

同步更新完整初始化脚本：

```text
nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql
```

完整初始化脚本必须包含所有历史表和本次新增字段、Outbox 表，不依赖 Flyway 增量脚本才能建立完整数据库。

## 17. 测试策略

至少覆盖以下场景：

- MinIO 上传瞬时失败后三次重试成功或最终失败。
- 上传成功后文档与 Outbox 在同一事务中提交。
- 数据库事务失败时补偿删除 MinIO 对象。
- Outbox 多实例条件抢占。
- Outbox 发布成功、失败退避、抢占超时和最终失败。
- RocketMQ 消息序列化、Topic、Key 和发送结果校验。
- 重复消息和旧 `processId` 消息幂等处理。
- Workflow 从 `PARSING`、`CHUNKING`、`INDEXING` 恢复对应节点。
- 可重试和不可重试异常分类。
- 最大重试次数和 DLQ 最终失败处理。
- 人工重试生成新的 `processId` 和 Outbox。
- 双索引部分清理失败时仍执行另一阶段。
- Elasticsearch 异常链完整保留。
- 旧 Redis 队列、本地 Worker 和排队位置接口完全移除。
- `application.yml` 和 `application-integration.yml` 配置绑定测试。
- document、infra、retrieval、workflow、boot 模块测试和架构边界测试。

## 18. 后续扩展

以下能力不在首期实现：

- `publish-mode=transaction-message` 的 RocketMQ 事务消息发布器。
- `messaging.type=redis-stream` 的 Redis Stream Publisher 和 Consumer。
- 钉钉、企业微信和邮件告警适配器。

这些事项统一记录在 `TODO.md`。
