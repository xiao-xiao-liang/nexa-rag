# 对话会话基础能力设计

## 1. 设计目标

本设计只负责搭建对话会话基础能力，不设计对话 Workflow 的 Node、Edge 或流程编排。后续 Workflow 只组合调用本模块提供的会话、消息、上下文和摘要服务。

设计目标如下：

- MySQL 保存完整会话历史，作为唯一事实源。
- Redis 保存活跃会话上下文快照，减少模型调用前的数据库查询耗时。
- 会话摘要与最近若干轮消息共同组成模型上下文。
- Spring AI 只负责模型调用适配，不接管业务消息持久化。
- 当前未接入真实登录时，使用固定的雪花 ID 字符串作为用户 ID。
- 后续接入 Sa-Token 时，只替换认证边界，不修改会话领域能力。

## 2. 模块边界

会话基础能力放在 `nexa-rag-chat` 模块中，依赖模型网关和检索模块，但不让模型模块依赖会话模块。

```text
nexa-rag-chat
├── 会话领域对象
├── 会话生命周期服务
├── 消息持久化服务
├── 会话摘要服务
├── 会话上下文服务
├── Redis 上下文缓存适配
└── Spring AI / ModelGateway 上下文映射适配
```

模型模块只提供顶层传输对象：

- `ChatModelMessage`
- `ChatModelRequest`
- `ChatModelResponse`
- `ChatModelStreamResponse`

不使用 `ChatModelRequest.ChatMessage` 这种内部类。

## 3. 领域对象

### 3.1 会话

`ChatConversation` 表示用户可见的会话，包含以下字段：

- `conversationId`
- `userId`
- `title`
- `status`
- `lastMessageId`
- `lastMessageTime`
- `version`
- `createdTime`
- `updatedTime`

会话状态包括：`ACTIVE`、`ARCHIVED`、`DELETED`。

### 3.2 消息

`ChatMessage` 表示完整业务消息，包含以下字段：

- `messageId`
- `conversationId`
- `userId`
- `sequence`
- `role`
- `status`
- `content`
- `thinkingContent`
- `referencesJson`
- `promptTokens`
- `completionTokens`
- `totalTokens`
- `failureCode`
- `failureMessage`
- `createdTime`
- `updatedTime`

消息角色包括：`USER`、`ASSISTANT`。

助手消息状态包括：`GENERATING`、`COMPLETED`、`FAILED`、`CANCELLED`。用户消息保存后直接为 `COMPLETED`。

上下文只读取已完成的用户消息和助手消息，不读取生成中、失败或取消的助手消息。

### 3.3 会话摘要

`ChatConversationSummary` 表示会话的阶段性摘要，包含：

- `summaryId`
- `conversationId`
- `userId`
- `content`
- `lastMessageId`
- `summaryVersion`
- `createdTime`
- `updatedTime`

摘要采用追加版本模式。`lastMessageId` 表示摘要已经覆盖到哪一条消息，避免每次从头总结全部历史。

### 3.4 会话上下文

`ConversationContext` 表示一次模型调用前可直接使用的上下文快照，包含：

- `conversationId`
- `userId`
- `summary`
- `summaryLastMessageId`
- `recentMessages`
- `lastMessageId`
- `version`

当前用户问题不放入快照，由调用方在读取上下文后显式追加，避免缓存未命中时重复加入当前问题。

## 4. 用户身份

`know-engine` 和 `ragent` 都将 `userId` 按字符串传递，数据库使用字符串字段。NexaRAG 沿用该约定：

- Java 类型使用 `String`。
- 数据库字段使用 `VARCHAR(64)`。
- 当前认证模块未接入真实登录，使用固定的雪花 ID 字符串。
- 固定值只生成一次并写入 `AuthConstants.DEFAULT_USER_ID`。
- 请求上下文由认证过滤器写入，请求结束后清理。
- 业务层不读取客户端提交的 `userId`。
- 异步任务必须显式携带 `userId`，不能依赖线程上下文。

后续接入 Sa-Token 时，替换固定用户认证过滤器，使用 `StpUtil.getLoginIdAsString()` 获取用户 ID，会话模块无需修改。

## 5. MySQL 持久化

### 5.1 `chat_conversation`

保存会话主体信息。主键为 `conversation_id`，并建立以下索引：

```text
(user_id, status, update_time)
```

### 5.2 `chat_message`

保存完整消息和生成状态。主键为 `message_id`，并建立以下索引：

```text
(conversation_id, sequence)
(conversation_id, status, sequence)
(user_id, conversation_id, sequence)
```

`sequence` 用于保证会话内消息顺序，不依赖时间字段排序。

### 5.3 `chat_conversation_summary`

保存摘要版本和摘要覆盖的最后消息 ID，并建立以下索引：

```text
(conversation_id, summary_version)
(user_id, conversation_id, summary_version)
```

MySQL 保存完整历史、消息状态和摘要版本，是系统唯一事实源。

## 6. Redis 活跃上下文

### 6.1 缓存键

```text
nexa:chat:context:{userId}:{conversationId}:v1
```

用户 ID 必须包含在缓存键中，避免不同用户通过相同会话 ID 读取到其他用户的数据。

### 6.2 缓存内容

Redis 保存摘要与最近 N 轮已完成消息，不保存完整历史：

```json
{
  "schemaVersion": 1,
  "userId": "用户雪花ID",
  "conversationId": "会话ID",
  "summary": "会话摘要",
  "summaryLastMessageId": "消息ID",
  "messages": [],
  "lastMessageId": "消息ID",
  "version": 12,
  "updatedTime": "2026-07-12T12:00:00"
}
```

缓存使用滑动 TTL，默认值由会话模块常量提供。读取活跃会话时刷新 TTL。

### 6.3 读写时机

读取上下文时优先访问 Redis。缓存未命中时，从 MySQL 查询最新摘要和最近 N 轮消息，构建上下文后写入 Redis。

消息流式生成过程中不按 Token 更新 Redis。assistant 消息完成、失败或取消后，再根据最终状态重建上下文快照。

数据库事务提交后再更新 Redis，避免缓存写入早于数据库提交。

### 6.4 并发保护

按用户和会话加 Redisson 锁：

```text
nexa:chat:context:lock:{userId}:{conversationId}
```

锁用于缓存回源重建、摘要生成、上下文快照更新以及会话删除。

缓存更新必须校验 `version` 或 `lastMessageId`，只允许新版本覆盖旧版本，避免并发请求使用旧快照覆盖新快照。

## 7. 服务接口

### 7.1 `ConversationService`

负责会话生命周期：

```text
create(userId, title)
getOwned(conversationId, userId)
listByUser(userId)
rename(conversationId, userId, title)
archive(conversationId, userId)
delete(conversationId, userId)
```

所有操作必须同时校验 `conversationId` 和 `userId`。

### 7.2 `ConversationMessageService`

负责消息生命周期：

```text
appendUserMessage(conversationId, userId, content)
startAssistantMessage(conversationId, userId)
completeAssistantMessage(messageId, content, thinkingContent, tokenUsage, references)
failAssistantMessage(messageId, failureCode, failureMessage)
cancelAssistantMessage(messageId)
listHistory(conversationId, userId, query)
```

### 7.3 `ConversationSummaryService`

负责摘要生成和版本管理：

```text
scheduleIfNecessary(conversationId, userId)
generate(conversationId, userId)
getLatest(conversationId, userId)
```

摘要生成异步执行，失败时保留旧摘要，不影响当前会话回答。

### 7.4 `ConversationContextService`

负责构建模型上下文：

```text
loadForTurn(conversationId, userId)
rebuild(conversationId, userId)
evict(conversationId, userId)
```

`loadForTurn` 必须返回当前用户消息之前的上下文。调用方读取完成后，再持久化当前用户消息并追加到模型请求。

## 8. 一次对话的基础调用契约

```text
1. contextService.loadForTurn(conversationId, userId)
2. messageService.appendUserMessage(...)
3. messageService.startAssistantMessage(...)
4. 将 context 和当前用户问题转换为 ChatModelMessage
5. 调用 ModelGateway 或 Spring AI ChatClient
6. 成功时 completeAssistantMessage(...)
7. 失败时 failAssistantMessage(...)
8. 事务提交后重建 Redis 上下文
9. 异步检查并生成会话摘要
```

## 9. Spring AI 适配

不使用 `MessageChatMemoryAdvisor` 作为核心会话存储，因为它的自动消息写入会与业务消息持久化产生重复职责。

提供只读的 `ConversationContextAdvisor`：

1. 从调用上下文获取 `conversationId` 和 `userId`。
2. 调用 `ConversationContextService.loadForTurn(...)`。
3. 将领域上下文转换为 Spring AI `Message`。
4. 注入当前 ChatClient 调用。
5. 不保存用户消息。
6. 不保存 assistant 消息。
7. 不更新 Redis。

使用 `ModelGateway` 时，通过 `ConversationContextMessageMapper` 将领域消息转换为顶层的 `ChatModelMessage`。

## 10. 风险与降级

- Redis 不可用时，直接从 MySQL 构建上下文，不影响会话主流程。
- 摘要生成失败时，继续使用旧摘要和最近消息。
- Redis 快照版本落后时，丢弃旧快照并从 MySQL 重建。
- assistant 流式生成中断时，消息标记为 `CANCELLED` 或 `FAILED`，不将未完成内容加入上下文。
- 会话删除时同时删除 MySQL 业务数据和 Redis 快照。

## 11. 明确不包含的内容

本设计不包含：

- 对话 Workflow 的 Node 设计
- 对话 Workflow 的 Edge 设计
- 意图识别
- 查询改写
- 检索路由
- 多源检索
- 答案生成流程编排
- Sa-Token 真实登录接入
