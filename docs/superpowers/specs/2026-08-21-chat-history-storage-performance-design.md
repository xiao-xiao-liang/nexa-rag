# 对话历史存储与加载性能设计

## 1. 目标与非目标

本设计改善已有会话打开和切换时的消息首屏体验，同时保持 MySQL 为完整聊天历史的唯一事实源。

目标：

- 打开会话只读取并显示最近 20 条消息；用户上滑再以游标读取更早历史。
- 当前页面内对已访问会话提供最多 20 个会话的 LRU 历史缓存；缓存命中时立即渲染。
- 删除会话时一致地逻辑删除会话和消息，并清除 Redis 上下文缓存。
- 长会话摘要按准确增量边界异步生成，且不阻塞当前回答。

非目标：

- 不将 Redis、浏览器持久化存储或向量数据库作为历史消息事实源。
- 不做跨会话用户长期记忆、聊天附件模型或引用证据正文快照。
- 不物理删除或归档逻辑删除的会话和消息。
- 不新增 Redis 历史投影缓存。

## 2. 已有基础与问题

`chat_conversation`、`chat_message` 和 `chat_conversation_summary` 已在 MySQL 持久化；消息按 `(conversation_id, sequence)` 排序，历史接口已支持 `beforeSequence` 游标。模型上下文已使用 Redis 缓存“摘要 + 最近消息”，这与浏览器消息展示缓存是不同职责。

当前前端切换会话时固定调用历史接口并用加载态替换消息区；默认读取 50 条，且未消费 `hasMore` / `nextBeforeSequence`。当前摘要只保存 `last_message_id`，摘要任务读取最近固定窗口后在内存中寻找边界，超长会话可能重复摘要或无法精确定位增量。当前删除仅改变会话状态，未级联逻辑删除消息、清上下文缓存或阻止活动生成时删除。

## 3. 领域约束

| 对象 | 所有者 | 不变量 |
| --- | --- | --- |
| 会话 | 当前用户 | 删除后会话及消息对业务查询、历史接口和 LLM 上下文均不可见。 |
| 消息 | 会话 | `sequence` 在会话内单调递增；完整原文保留在 MySQL。 |
| 活动生成 | 会话 | 同一会话至多一个；存在活动生成时禁止删除。 |
| 会话摘要 | 会话上下文 | 派生数据；以 `summary_until_sequence` 精确描述已覆盖范围，可由原始消息重建。 |
| 页面内历史缓存 | 前端展示层 | 仅当前页面内存可见，最多 20 个会话，不能成为事实源。 |

## 4. 历史读取与前端缓存

### 4.1 接口契约

保留 `GET /api/conversations/{conversationId}/messages` 的游标形式：

```text
首次：GET .../messages?size=20
上滑：GET .../messages?beforeSequence={当前最早序号}&size=20
```

响应仍为 `records`（按 `sequence` 升序）、`hasMore`、`nextBeforeSequence`。服务端默认 `size` 改为 20；最大页大小仍保持现有防护上限。查询只投影历史页面真正需要的字段，避免读取或传输思考内容、引用 JSON、Token 用量和失败内部字段。

### 4.2 页面内缓存

`ChatPage` 使用会话 ID 键控的内存 LRU 缓存。每个条目保存已加载的升序消息、`hasMore`、下一次 `beforeSequence`、加载中状态和最近访问时间。

1. 选择会话时，命中缓存则立即设置消息列表，并将条目标记为最近访问；未命中才显示首屏加载态并请求最近 20 条。
2. 用户滚动到顶部且 `hasMore=true`、该条目未在加载时，按游标读取下一页；将更早消息去重后插入顶部，并以滚动前后 `scrollHeight` 差值补偿 `scrollTop`，避免视口跳动。
3. 完成当前会话的一轮生成后，把终态消息同步写回该会话缓存；删除会话时移除其缓存；刷新页面时缓存自然消失。
4. 新增或访问第 21 个会话时淘汰最久未访问条目的全部已加载页面。

缓存不写入 LocalStorage、IndexedDB、Service Worker 或其他持久化浏览器存储。历史请求失败时保留当前已展示缓存，不用空态覆盖，并展示可重试的非阻塞错误提示。

## 5. 会话删除与活动生成保护

删除由一个独立的会话删除应用服务协调，并与创建本轮用户消息/助手占位使用同一 `ConversationContextLock` 锁粒度（`userId + conversationId`）。在锁内：

1. 校验会话归属和未删除状态；
2. 查询该会话是否存在 `GENERATING` 助手消息；若存在，返回明确业务错误，不执行任何删除；
3. 将 `chat_conversation` 和该会话全部 `chat_message` 逻辑删除；
4. 事务提交后删除 Redis 会话上下文缓存。

前端收到或维护活动生成状态时禁用删除入口；这只是体验层保护，服务端锁内校验是最终一致性约束。生成请求若在删除完成后才取得锁，必须因会话不存在而失败。逻辑删除数据不做自动物理清理或归档。

## 6. 摘要增量模型

对 `chat_conversation_summary` 增加可空 `summary_until_sequence BIGINT`。存量摘要用其 `last_message_id` 关联 `chat_message` 回填该序号；无法回填的历史摘要保留旧字段并在下一次摘要时从可验证边界安全重建。

新摘要流程：

1. 回答终态后异步检查；绝不等待摘要完成才向用户结束回答。
2. 最新摘要后的新增用户消息达到 8 个，才调度摘要；不足阈值立即跳过。
3. 用 `(user_id, conversation_id, sequence)` 索引查询 `sequence > summary_until_sequence` 的已完成上下文消息，受单次读取上限保护。
4. 成功后追加一版摘要，同时保存最后覆盖消息 ID 和 `summary_until_sequence`；失败保留上一版摘要并记录无内容诊断。

Redis 上下文快照与领域对象同步携带摘要覆盖序号。摘要仍是派生数据，不改变原始聊天消息。

## 7. 测试与验收场景

1. 首次打开会话只请求 20 条最新消息；响应按序升序，且仍返回准确游标。
2. 上滑连续加载多页，消息无重复、无缺失、顺序稳定且视口不跳动。
3. 在同一页面切回已缓存会话时不发首屏历史请求；第 21 个会话访问后最久未访问缓存被淘汰。
4. 有活动生成时，前端无法发起删除；直接调用后端删除接口也得到业务拒绝，消息、缓存和生成不受影响。
5. 无活动生成时删除会话后，会话与消息均不可查询、Redis 上下文不存在、后续生成无法创建消息。
6. 摘要不足 8 个新增用户轮次不调用模型；达到阈值后仅读取 `summary_until_sequence` 之后的消息并写入新的覆盖序号。

## 8. 已确认 ADR

- `2026-08-21-load-recent-twenty-chat-messages-on-conversation-open.md`
- `2026-08-21-store-chat-citations-without-evidence-snapshots.md`
- `2026-08-21-logically-delete-conversation-messages-and-cache-together.md`
- `2026-08-21-forbid-deleting-conversation-with-active-generation.md`
- `2026-08-21-generate-conversation-summary-asynchronously-by-threshold.md`
- `2026-08-21-use-sequence-as-conversation-summary-boundary.md`
- `2026-08-21-retain-logically-deleted-chat-data-indefinitely.md`
- `2026-08-21-defer-cross-conversation-user-memory.md`
- `2026-08-21-keep-chat-history-view-cache-in-page-memory-only.md`
