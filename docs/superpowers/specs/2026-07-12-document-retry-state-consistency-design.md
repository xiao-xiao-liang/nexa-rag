# 文档消息重试状态一致性设计

## 1. 背景

文档流水线迁移到 RocketMQ 后，消息重投由 Broker 管理，但 `document.retry_count`、
`document.max_retry_count` 和 `document.last_retry_time` 仍沿用旧的应用层重试语义。
因此消息实际发生多次重投时，数据库中的重试次数和最近重试时间没有同步更新。

同时，死信消费者将 DLQ 消费本身计入 `consumed_times`，导致告警次数与实际 Workflow
执行次数不一致。

## 2. 目标

1. 将文档消息消费和重试信息完整记录在 `document` 表。
2. 保证普通消费、RocketMQ 重投和 DLQ 最终失败使用一致的计数语义。
3. 保留 Outbox 的生产端可靠发布职责，不将消费状态写入 Outbox。
4. 不新增数据库字段，不执行 Flyway。

## 3. 字段语义

### 3.1 document 表

- `consumed_times`：当前处理轮次实际执行 Workflow 的总次数，首次执行记为 1。
- `retry_count`：当前处理轮次的 RocketMQ 重投次数，等于 `max(consumed_times - 1, 0)`。
- `max_retry_count`：当前处理轮次允许的 RocketMQ 最大重投次数，与
  `nexa.document.pipeline.messaging.max-reconsume-times` 保持一致。
- `last_retry_time`：最近一次 `retry_count > 0` 的实际消费时间。
- `last_message_id`：最近一次实际执行 Workflow 的 RocketMQ 消息 ID。
- `message_status`：首次消费为 `PROCESSING`，重投消费为 `RETRYING`，最终失败为 `FAILED`。
- `failure_stage`、`failure_reason`、`failure_detail`：保存最终失败信息。

### 3.2 document_pipeline_outbox 表

Outbox 只负责消息从数据库可靠发布到 RocketMQ，继续保留以下发布字段：

- `publish_status`
- `publish_retry_count`
- `next_retry_time`
- `lock_owner`
- `lock_time`
- `published_time`
- `failure_reason`

消费失败不回写 Outbox，避免混淆生产端发布失败和消费端处理失败。

## 4. 数据流

### 4.1 提交处理

1. 创建新 `process_id`。
2. 将消费次数和重试次数重置为 0。
3. 将 `max_retry_count` 设置为当前 RocketMQ 最大重投次数。
4. 清空最近消息、最近重试时间和失败信息。
5. 在同一事务中写入 Outbox。

### 4.2 普通消费

1. 根据 `MessageExt.reconsumeTimes + 1` 计算 `consumed_times`。
2. 根据 `max(consumed_times - 1, 0)` 计算 `retry_count`。
3. 首次消费保持 `last_retry_time` 为空。
4. 重投消费将 `last_retry_time` 更新为当前时间。
5. 更新 `last_message_id` 和 `message_status` 后执行 Workflow。

### 4.3 死信处理

DLQ 消费只是最终失败通知，不再次执行 Workflow，因此不增加 `consumed_times` 和
`retry_count`。死信消费者使用消息携带的最后一次实际消费次数，最终失败事务同步更新：

- `status=FAILED`
- `message_status=FAILED`
- `consumed_times`
- `retry_count`
- `last_message_id`
- `last_retry_time`
- 最终失败信息和结束时间

## 5. 兼容性

- 人工重试继续开启新的 `process_id`，并将当前轮次重试状态重置为 0。
- 旧轮次消息继续通过 `document_id + process_id` 条件更新被忽略。
- 已进入 `INDEXED` 或 `FAILED` 的终态文档不允许被旧消息覆盖。
- 不修改 Outbox 表结构和发布器行为。

## 6. 测试范围

1. 首次消费写入 `consumed_times=1`、`retry_count=0`，不写 `last_retry_time`。
2. 第三次实际消费写入 `consumed_times=3`、`retry_count=2`，并写入最近重试时间。
3. 新处理轮次写入与配置一致的 `max_retry_count`。
4. DLQ 最终失败不把 DLQ 消费本身计入执行次数。
5. DLQ 最终失败同步保存消费次数、重试次数和最后消息 ID。
6. 旧轮次和终态文档保持不变。
