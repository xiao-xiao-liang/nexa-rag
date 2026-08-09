# 文档删除与片段清理设计

## 目标

删除文档时，在关系库事务中一并逻辑删除 `document` 与 `document_chunk`；只有事务提交后，才允许清理 Milvus、Elasticsearch 和章节导航索引。

## 方案

复用现有 `CLEAN_DOCUMENT_INDEX` 事务 Outbox。`DocumentServiceImpl.deleteDocument` 在同一 `@Transactional` 方法内先逻辑删除文档、再删除文档片段并创建 Outbox 任务。任何数据库写入或 Outbox 写入失败均抛出异常，使整个事务回滚。

Outbox 记录只能在事务提交后被发布。`DocumentIndexCleanupConsumer` 收到任务后再执行 Milvus、Elasticsearch 和章节导航索引清理；外部索引失败继续由既有 RocketMQ 重试处理。删除接口不直接调用外部基础设施。

## 边界

- 没有片段时，按删除成功处理；`deleteByDocumentId` 的零行更新不是异常。
- 片段删除的 SQL 异常、文档删除失败、Outbox 创建失败均不得留下可消费的外部索引清理任务；文档删除返回 `false` 时不得删除 chunks。
- 不改变外部索引清理消费者的重试、死信和幂等策略。
- 不执行物理删除，也不调整数据库表结构。

## 验收

- 成功删除会调用片段删除，并在片段、文档与 Outbox 均成功后返回成功结果。
- 片段删除失败时，异常会触发当前事务回滚，且 Outbox 不会创建。
- 关系库删除失败时，Outbox 不会创建，因而不会触发 Milvus 或 Elasticsearch 删除。
