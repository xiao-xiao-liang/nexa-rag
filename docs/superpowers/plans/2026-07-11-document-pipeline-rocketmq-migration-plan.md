# 文档流水线 RocketMQ 迁移实施计划

> **执行要求：** 实施时必须使用 `test-driven-development`，完成前使用 `verification-before-completion`，提交时统一使用 `git-commit-workflow`。每个任务先写失败测试并确认 RED，再编写最小实现。

**目标：** 删除现有 Redis 文档队列和本地轮询 Worker，使用 Outbox + RocketMQ 驱动完整 Workflow Graph，并通过 RocketMQ 重试、业务失败 Topic 和 DLQ 完成失败处理。

**架构：** HTTP 上传接口同步保存 MinIO，在同一数据库事务内创建文档与 Outbox。Outbox 发布器使用 `RocketMQTemplate` 发送单 Topic 文档流水线消息，Consumer 校验 `processId` 后调用现有 Workflow Graph。节点失败直接抛出，由 RocketMQ 重试；最终失败消费者将当前处理轮次更新为 `FAILED` 并执行结构化日志告警。

**技术栈：** Java 21、Spring Boot 3.5.13、MyBatis-Plus、Flyway、RocketMQ Spring Boot Starter 2.3.1、RocketMQ Client 5.3.0、Spring AI Alibaba Graph、JUnit 5、Mockito、AssertJ。

---

## 文件结构与职责

新增或重点修改的文件：

```text
nexa-rag-infra
└── com.nexarag.infra.messaging.document
    ├── DocumentPipelineMessagePublisher.java
    ├── DocumentPipelineMessageHandler.java
    ├── config/DocumentPipelineMessagingProperties.java
    ├── enums/DocumentPipelineMessagingType.java
    ├── enums/DocumentPipelinePublishMode.java
    ├── model/DocumentPipelineMessage.java
    ├── model/DocumentPipelineFailureMessage.java
    ├── model/DocumentPipelinePublishResult.java
    └── rocketmq
        ├── RocketMqDocumentPipelinePublisher.java
        ├── RocketMqDocumentPipelineConsumer.java
        └── RocketMqDocumentPipelineFailureConsumer.java

nexa-rag-document
├── entity/Document.java
├── enums/DocumentPipelineMessageStatus.java
├── outbox/entity/DocumentPipelineOutbox.java
├── outbox/enums/OutboxPublishStatus.java
├── outbox/mapper/DocumentPipelineOutboxMapper.java
├── outbox/service/DocumentPipelineOutboxService.java
├── outbox/service/impl/DocumentPipelineOutboxServiceImpl.java
├── outbox/service/impl/DocumentPipelineOutboxPublisher.java
├── alert/DocumentPipelineAlertService.java
├── alert/DocumentPipelineFailureEvent.java
├── alert/impl/LoggingDocumentPipelineAlertService.java
├── service/DocumentPipelineSubmitService.java
└── service/impl/DocumentPipelineSubmitServiceImpl.java

nexa-rag-workflow
├── handler/WorkflowDocumentPipelineMessageHandler.java
├── node/document/ParsingNode.java
├── node/document/ChunkingNode.java
├── node/document/IndexingNode.java
└── node/document/DocumentStatusRouterNode.java

nexa-rag-boot
├── resources/db/migration/V11__add_document_pipeline_messaging.sql
├── resources/db/schema/nexa_rag_schema.sql
├── resources/application.yml
└── resources/application-integration.yml
```

---

### 任务 1：接入 RocketMQ Starter 与消息配置模型

**文件：**

- 修改：`pom.xml`
- 修改：`nexa-rag-infra/pom.xml`
- 新增：`nexa-rag-infra/src/main/java/com/nexarag/infra/messaging/document/config/DocumentPipelineMessagingProperties.java`
- 新增：`nexa-rag-infra/src/main/java/com/nexarag/infra/messaging/document/enums/DocumentPipelineMessagingType.java`
- 新增：`nexa-rag-infra/src/main/java/com/nexarag/infra/messaging/document/enums/DocumentPipelinePublishMode.java`
- 测试：`nexa-rag-infra/src/test/java/com/nexarag/infra/messaging/document/config/DocumentPipelineMessagingPropertiesTest.java`

- [ ] **步骤 1：编写配置默认值失败测试**

测试应验证：

```java
DocumentPipelineMessagingProperties properties = new DocumentPipelineMessagingProperties();

assertThat(properties.getType()).isEqualTo(DocumentPipelineMessagingType.ROCKETMQ);
assertThat(properties.getPublishMode()).isEqualTo(DocumentPipelinePublishMode.OUTBOX);
assertThat(properties.getTopic()).isEqualTo("nexa-document-pipeline");
assertThat(properties.getFailureTopic()).isEqualTo("nexa-document-pipeline-failure");
assertThat(properties.getMaxReconsumeTimes()).isEqualTo(5);
```

- [ ] **步骤 2：运行测试并确认 RED**

```powershell
mvn -pl nexa-rag-infra -am "-Dtest=DocumentPipelineMessagingPropertiesTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

预期：因配置类和枚举不存在而编译失败。

- [ ] **步骤 3：增加依赖和配置类**

父 POM 将属性明确命名为：

```xml
<rocketmq-spring.version>2.3.1</rocketmq-spring.version>
```

依赖管理增加：

```xml
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-spring-boot-starter</artifactId>
    <version>${rocketmq-spring.version}</version>
</dependency>
```

`nexa-rag-infra` 引入该依赖。配置前缀使用：

```text
nexa.document.pipeline.messaging
```

配置类字段包含类型、发布模式、Topic、Consumer Group、失败 Topic、失败 Consumer Group 和最大重试次数，字段均添加简体中文注释。

- [ ] **步骤 4：运行定向测试**

执行步骤 2 的命令，预期通过。

- [ ] **步骤 5：提交**

提交信息：

```text
feat(infra): 增加文档流水线消息配置
```

---

### 任务 2：增加消息契约与 RocketMQ 发布适配器

**文件：**

- 新增：`nexa-rag-infra/src/main/java/com/nexarag/infra/messaging/document/DocumentPipelineMessagePublisher.java`
- 新增：`nexa-rag-infra/src/main/java/com/nexarag/infra/messaging/document/DocumentPipelineMessageHandler.java`
- 新增：`nexa-rag-infra/src/main/java/com/nexarag/infra/messaging/document/model/DocumentPipelineMessage.java`
- 新增：`nexa-rag-infra/src/main/java/com/nexarag/infra/messaging/document/model/DocumentPipelineFailureMessage.java`
- 新增：`nexa-rag-infra/src/main/java/com/nexarag/infra/messaging/document/model/DocumentPipelinePublishResult.java`
- 新增：`nexa-rag-infra/src/main/java/com/nexarag/infra/messaging/document/rocketmq/RocketMqDocumentPipelinePublisher.java`
- 测试：`nexa-rag-infra/src/test/java/com/nexarag/infra/messaging/document/rocketmq/RocketMqDocumentPipelinePublisherTest.java`

- [ ] **步骤 1：编写发布契约失败测试**

测试 Mock `RocketMQTemplate`，验证：

```java
DocumentPipelineMessage message = new DocumentPipelineMessage(
        1L, "process-1", 1, LocalDateTime.now());

DocumentPipelinePublishResult result = publisher.publish(message);

assertThat(result.success()).isTrue();
verify(rocketMQTemplate).syncSend(
        eq("nexa-document-pipeline"),
        argThat(springMessage -> "1:process-1".equals(
                springMessage.getHeaders().get("KEYS"))));
```

同时覆盖 `SendStatus` 非 `SEND_OK` 时抛出包含 `documentId/processId` 的中文异常。

- [ ] **步骤 2：运行测试并确认 RED**

```powershell
mvn -pl nexa-rag-infra -am "-Dtest=RocketMqDocumentPipelinePublisherTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- [ ] **步骤 3：实现消息模型和发布器**

发布器使用 `RocketMQTemplate.syncSend`，消息 Header 设置 RocketMQ Key。异常必须保留原始异常对象，日志不得输出完整消息正文。

- [ ] **步骤 4：运行定向测试并确认 GREEN**

- [ ] **步骤 5：提交**

```text
feat(infra): 实现RocketMQ文档流水线发布器
```

---

### 任务 3：增加数据库字段、Outbox 表和完整 Schema

**文件：**

- 新增：`nexa-rag-boot/src/main/resources/db/migration/V11__add_document_pipeline_messaging.sql`
- 修改：`nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql`
- 修改：`nexa-rag-document/src/main/java/com/nexarag/document/entity/Document.java`
- 新增：`nexa-rag-document/src/main/java/com/nexarag/document/enums/DocumentPipelineMessageStatus.java`
- 新增：`nexa-rag-document/src/main/java/com/nexarag/document/outbox/entity/DocumentPipelineOutbox.java`
- 新增：`nexa-rag-document/src/main/java/com/nexarag/document/outbox/enums/OutboxPublishStatus.java`
- 新增：`nexa-rag-document/src/main/java/com/nexarag/document/outbox/mapper/DocumentPipelineOutboxMapper.java`
- 测试：`nexa-rag-document/src/test/java/com/nexarag/document/outbox/DocumentPipelineSchemaContractTest.java`

- [ ] **步骤 1：编写 SQL 契约失败测试**

测试读取迁移文件和完整 Schema，验证两者都包含：

```text
process_id
message_status
consumed_times
last_message_id
document_pipeline_outbox
uk_document_pipeline_outbox_message_key
idx_document_pipeline_outbox_publish_task
```

并验证 SQL 包含 `COMMENT` 中文注释。

- [ ] **步骤 2：运行测试并确认 RED**

```powershell
mvn -pl nexa-rag-document -am "-Dtest=DocumentPipelineSchemaContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- [ ] **步骤 3：编写 V11 和完整 Schema**

`document` 新增：

```sql
process_id VARCHAR(64) NULL COMMENT '当前文档处理轮次ID',
message_status VARCHAR(32) NULL COMMENT '当前处理轮次消息状态',
consumed_times INT NOT NULL DEFAULT 0 COMMENT '当前处理轮次已消费次数',
last_message_id VARCHAR(128) NULL COMMENT '最近一次消费的RocketMQ消息ID'
```

Outbox 包含设计文档确认的全部字段、主键、唯一索引和发布任务索引。迁移 SQL 与完整 Schema 内容保持一致。

- [ ] **步骤 4：实现 Entity、枚举和 Mapper**

所有类和字段使用简体中文 Java doc，Entity 使用项目既有 Lombok 和 MyBatis-Plus 风格。

- [ ] **步骤 5：运行测试并提交**

```text
feat(document): 增加文档流水线Outbox模型
```

---

### 任务 4：实现 Outbox 服务和多实例抢占发布

**文件：**

- 新增：`nexa-rag-document/src/main/java/com/nexarag/document/outbox/config/DocumentPipelineOutboxProperties.java`
- 新增：`nexa-rag-document/src/main/java/com/nexarag/document/outbox/service/DocumentPipelineOutboxService.java`
- 新增：`nexa-rag-document/src/main/java/com/nexarag/document/outbox/service/impl/DocumentPipelineOutboxServiceImpl.java`
- 新增：`nexa-rag-document/src/main/java/com/nexarag/document/outbox/service/impl/DocumentPipelineOutboxPublisher.java`
- 测试：`nexa-rag-document/src/test/java/com/nexarag/document/outbox/service/impl/DocumentPipelineOutboxServiceImplTest.java`
- 测试：`nexa-rag-document/src/test/java/com/nexarag/document/outbox/service/impl/DocumentPipelineOutboxPublisherTest.java`

- [ ] **步骤 1：编写抢占和发布状态测试**

覆盖：

- `PENDING -> PUBLISHING` 必须使用 `outboxId + oldStatus` 条件更新。
- 发送成功后更新 `PUBLISHED/publishedTime`。
- 发送失败后增加 `publishRetryCount` 并设置 `nextRetryTime`。
- 达到上限后更新 `FAILED`。
- 超时 `PUBLISHING` 可以重新抢占。

- [ ] **步骤 2：运行测试并确认 RED**

```powershell
mvn -pl nexa-rag-document -am "-Dtest=DocumentPipelineOutboxServiceImplTest,DocumentPipelineOutboxPublisherTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- [ ] **步骤 3：实现 Outbox 服务**

Outbox 发布器每次最多处理 `batchSize` 条。关键步骤添加编号中文注释。发布循环内单条失败不得中断整批，但必须记录 `outboxId/documentId/processId` 和原始异常。

- [ ] **步骤 4：运行定向测试并提交**

```text
feat(document): 实现Outbox可靠消息发布
```

---

### 任务 5：改造上传、提交和人工重试事务

**文件：**

- 修改：`nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentUploadServiceImpl.java`
- 删除并替换：`DocumentPipelineTriggerService.java`
- 删除并替换：`DocumentPipelineTriggerServiceImpl.java`
- 新增：`nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentPipelineSubmitService.java`
- 新增：`nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentPipelineSubmitServiceImpl.java`
- 修改：`nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentService.java`
- 修改：`nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentServiceImpl.java`
- 修改：`nexa-rag-document/src/main/java/com/nexarag/document/vo/UploadDocumentResponse.java`
- 修改：`nexa-rag-document/src/main/java/com/nexarag/document/controller/DocumentController.java`
- 测试：`DocumentUploadServiceImplTest.java`
- 测试：`DocumentPipelineSubmitServiceImplTest.java`

- [ ] **步骤 1：编写上传重试和事务提交失败测试**

覆盖：

- MinIO 前两次失败、第三次成功。
- 三次失败后不创建文档和 Outbox。
- 文件成功但文档/Outbox事务失败时删除对象。
- 提交成功返回 `documentId/processId/QUEUED`。
- 人工重试生成新的 `processId` 和新 Outbox。

- [ ] **步骤 2：运行定向测试并确认 RED**

- [ ] **步骤 3：实现提交事务**

`DocumentPipelineSubmitService` 的事务方法必须同时：

1. 生成 `processId`。
2. 条件更新文档为 `QUEUED`。
3. 重置消息状态、消费次数和失败信息。
4. 写入 `PENDING` Outbox。

上传接口不直接调用 RocketMQ Publisher。

- [ ] **步骤 4：实现 MinIO 三次短退避重试**

重试配置可绑定，默认退避为 200、500、1000 毫秒。测试中使用可替换的等待器或零延迟配置，禁止真实休眠拖慢测试。

- [ ] **步骤 5：运行测试并提交**

```text
refactor(document): 使用Outbox提交文档流水线
```

---

### 任务 6：删除排队位置查询和 Redis Dispatcher

**文件：**

- 删除：`DocumentProcessTaskDispatcher.java`
- 删除：`LocalDocumentProcessTaskDispatcher.java`
- 删除：`RedisDocumentProcessTaskDispatcher.java`
- 删除：`DocumentQueueInfo.java`
- 删除：`DocumentQueueStatusService.java`
- 删除：`DocumentQueueStatusServiceImpl.java`
- 修改：`DocumentProcessStatusVO.java`
- 修改：`DocumentConverter.java`
- 修改：`DocumentController.java`
- 删除或重写相关测试。

- [ ] **步骤 1：编写响应契约测试**

验证上传响应和处理状态响应不再包含：

```text
queuePosition
waitingCount
running
workerId
leaseTtlSeconds
```

处理状态响应包含：

```text
documentId
processId
status
messageStatus
consumedTimes
failureStage
failureReason
```

- [ ] **步骤 2：运行测试并确认 RED**

- [ ] **步骤 3：删除旧类并调整接口**

`GET /api/documents/{documentId}/process-status` 直接查询数据库当前处理状态，不访问 Redis。

- [ ] **步骤 4：运行 document 模块测试并提交**

```text
refactor(document): 删除文档精确排队位置能力
```

---

### 任务 7：实现 RocketMQ Consumer、失败消息和告警

**文件：**

- 新增：`RocketMqDocumentPipelineConsumer.java`
- 新增：`RocketMqDocumentPipelineFailureConsumer.java`
- 新增：`DocumentPipelineAlertService.java`
- 新增：`DocumentPipelineFailureEvent.java`
- 新增：`LoggingDocumentPipelineAlertService.java`
- 修改：`DocumentService.java`
- 修改：`DocumentServiceImpl.java`
- 测试：`RocketMqDocumentPipelineConsumerTest.java`
- 测试：`RocketMqDocumentPipelineFailureConsumerTest.java`
- 测试：`LoggingDocumentPipelineAlertServiceTest.java`

- [ ] **步骤 1：编写 Consumer 行为失败测试**

正常 Consumer 使用 `MessageExt`，覆盖：

- 反序列化业务消息。
- 写入 `msgId/reconsumeTimes`。
- 旧 `processId` 直接完成。
- 当前轮次委托 Handler。
- Handler 抛可重试异常时继续向上抛出。
- 不可重试异常发布业务失败 Topic。

失败 Consumer 覆盖：

- 当前轮次条件更新为 `FAILED`。
- 旧轮次不覆盖新状态。
- 最终失败后调用结构化日志告警。

- [ ] **步骤 2：运行测试并确认 RED**

- [ ] **步骤 3：实现最终失败状态方法**

新增明确方法：

```java
boolean markProcessFailed(Long documentId,
                          String processId,
                          String failureStage,
                          String failureReason,
                          String failureDetail);
```

禁止复用会自动重新排队的旧失败方法。

- [ ] **步骤 4：实现 Consumer 和告警**

`@RocketMQMessageListener` 配置正常 Topic、Consumer Group 和 `maxReconsumeTimes=5`。所有异常日志使用中文并保留原始异常对象。

- [ ] **步骤 5：运行测试并提交**

```text
feat(workflow): 增加RocketMQ消费失败处理
```

---

### 任务 8：改造 Workflow Graph 状态恢复和节点职责

**文件：**

- 新增：`nexa-rag-workflow/src/main/java/com/nexarag/workflow/handler/WorkflowDocumentPipelineMessageHandler.java`
- 修改：`DocumentStatusRouterNode.java`
- 修改：`ParsingNode.java`
- 修改：`ChunkingNode.java`
- 修改：`IndexingNode.java`
- 修改：对应 Dispatcher 和配置测试。
- 测试：`WorkflowDocumentPipelineMessageHandlerTest.java`

- [ ] **步骤 1：编写状态路由失败测试**

路由断言：

```text
QUEUED/PARSING -> PARSING_NODE
PARSED/CHUNKING -> CHUNKING_NODE
CHUNKED/INDEXING -> INDEXING_NODE
INDEXED/FAILED -> END
```

Handler 断言旧 `processId` 不调用 Graph，当前轮次调用 Graph。

- [ ] **步骤 2：运行测试并确认 RED**

- [ ] **步骤 3：简化节点异常处理**

删除节点内部以下行为：

```text
recordProcessFailure
重新设置 QUEUED
主动重新入队
达到重试次数后返回0或结束
```

节点异常统一保留原始异常并向上抛出。

- [ ] **步骤 4：运行 workflow 测试并提交**

```text
refactor(workflow): 由RocketMQ接管流水线重试
```

---

### 任务 9：修复 Retrieval 索引和清理异常语义

**文件：**

- 修改：`DocumentIndexServiceImpl.java`
- 修改：`DocumentIndexCleanerImpl.java`
- 修改：`ElasticsearchKeywordIndexClient.java`
- 修改：对应测试。

- [ ] **步骤 1：编写失败测试**

覆盖：

- Embedding、Milvus、Elasticsearch 直接抛异常时向上抛出。
- Retrieval 不调用自动重新排队逻辑。
- Milvus 清理失败后仍执行 Elasticsearch 清理。
- Elasticsearch 清理失败后保留 Milvus 成功结果。
- IO、Interrupted、JSON 异常保留原始异常对象。

- [ ] **步骤 2：运行测试并确认 RED**

- [ ] **步骤 3：实现最小修复**

索引阶段使用条件状态更新并检查结果；清理阶段分别执行并聚合；Elasticsearch 非成功响应仅记录安全截断后的响应正文。

- [ ] **步骤 4：运行 retrieval 测试并提交**

```text
fix(retrieval): 完善索引异常和清理结果处理
```

---

### 任务 10：删除 Redis 队列和本地 Worker

**文件：**

- 删除：`nexa-rag-infra/src/main/java/com/nexarag/infra/queue/document/**`
- 删除：`nexa-rag-infra/src/test/java/com/nexarag/infra/queue/document/**`
- 删除：`nexa-rag-boot/src/main/java/com/nexarag/boot/worker/LocalDocumentPipelineWorker.java`
- 删除：`nexa-rag-boot/src/main/java/com/nexarag/boot/worker/DocumentPipelineWorkerProperties.java`
- 删除：相关 Worker 测试。
- 修改：模块 POM 中仅由旧队列使用的依赖。

- [ ] **步骤 1：新增架构边界测试**

验证代码库不再引用：

```text
com.nexarag.infra.queue.document
DocumentPipelineQueue
LocalDocumentPipelineWorker
DocumentProcessTaskDispatcher
```

- [ ] **步骤 2：运行测试并确认 RED**

- [ ] **步骤 3：删除旧实现和引用**

保留 Redis/Redisson 中仍被 MinerU 限流、模型刷新等功能使用的依赖和配置。

- [ ] **步骤 4：运行 infra、document、workflow、boot 测试并提交**

```text
refactor(infra): 删除Redis文档流水线队列
```

---

### 任务 11：更新 application 配置和配置绑定测试

**文件：**

- 修改：`nexa-rag-boot/src/main/resources/application.yml`
- 修改：`nexa-rag-boot/src/main/resources/application-integration.yml`
- 测试：配置绑定测试。

- [ ] **步骤 1：编写配置契约失败测试**

验证旧配置全部删除，新配置可绑定，并包含 NameServer 默认值：

```text
118.195.146.161:8082
```

- [ ] **步骤 2：运行测试并确认 RED**

- [ ] **步骤 3：更新配置并添加中文说明**

每个配置项必须说明用途、可选值、默认值、单位和约束关系。删除：

```text
mode
queue-mode
worker-enabled
max-concurrency
poll-interval-ms
lease-ttl-seconds
queue.*
```

- [ ] **步骤 4：运行测试并提交**

```text
chore(boot): 更新RocketMQ文档流水线配置
```

---

### 任务 12：完整回归和文档检查

**文件：**

- 修改：`TODO.md`（仅保留已确认的后续适配事项）
- 检查：设计文档、迁移 SQL、完整 Schema、所有改动文件。

- [ ] **步骤 1：运行格式和差异检查**

```powershell
git diff --check
rg -n "queuePosition|waitingCount|leaseTtlSeconds|DocumentPipelineQueue|LocalDocumentPipelineWorker" . -g '*.java' -g '*.yml' -g '*.yaml' -g '!target/**'
```

第二条命令预期无生产代码匹配；历史文档不在检查范围。

- [ ] **步骤 2：运行模块测试**

```powershell
mvn -pl nexa-rag-infra,nexa-rag-document,nexa-rag-retrieval,nexa-rag-workflow,nexa-rag-boot -am test
```

预期：构建成功，失败数和错误数均为 0。

- [ ] **步骤 3：运行完整测试**

```powershell
mvn test
```

- [ ] **步骤 4：检查数据库脚本一致性**

确认 `V11__add_document_pipeline_messaging.sql` 的最终结构已同步进入 `db/schema/nexa_rag_schema.sql`，并确认所有表、字段和索引均有简体中文注释。

- [ ] **步骤 5：按 git-commit-workflow 整理最终提交**

若前序任务已逐步提交，本步骤只提交必要的测试或文档收尾：

```text
test(document): 补全文档消息流水线回归验证
```

---

## 计划自检结果

- 已覆盖上传、Outbox、RocketMQ、Workflow、Retrieval、DLQ、告警、人工重试、SQL、配置和旧队列删除。
- 消息发布模式首期固定为 Outbox，事务消息仅保留配置枚举和 TODO，不创建不可用实现。
- Redis Stream 首期仅保留配置枚举和 TODO，不创建空 Publisher/Consumer Bean。
- 精确排队位置相关能力全部删除，处理状态接口继续保留。
- 历史设计文档不修改，新设计文档作为替代方案依据。
