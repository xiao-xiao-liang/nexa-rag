# 飞书式智能体流式回答 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `nexa-rag-studio` 使用可恢复的 SSE 展示“工具运行卡在前、Markdown 正文在后”的智能体回答，并在历史会话中完整还原终态工具卡。

**Architecture:** 后端将工具状态以版本化快照推送，Markdown 以批量增量推送；两类事件均写入 Redis 的短期重放缓冲。终态将 Markdown 与最小工具展示投影写入 `chat_message`，历史接口返回该投影。`nexa-rag-studio` 按事件版本幂等合并，自动恢复流，并复用既有飞书 Markdown 与工具卡组件。

**Tech Stack:** Spring Boot、Reactor SSE、Spring Data Redis、MyBatis-Plus、MySQL/Flyway、React 19、TypeScript、Vite、react-markdown。

**约束：** 一会话仅一条活动生成；一期工具只读；工具完成后再输出正文；取消保留部分结果并标记已停止；工具卡仅展示任务状态与工具名称；不在未获授权时提交 Git。

---

## 文件结构

- `nexa-rag-workflow/.../stream/`：流事件 DTO、Redis 缓冲、跨实例实时转发、工具状态发布与恢复订阅。
- `nexa-rag-chat/.../`：助手消息终态快照的持久化与历史安全投影。
- `nexa-rag-boot/.../ChatController.java`：初始流与恢复流的 SSE 入口。
- `nexa-rag-workflow/.../node/chat/`：只读检索工具的事件、重试、失败上下文与工具优先编排。
- `nexa-rag-studio/src/`：SSE 恢复客户端、事件 reducer、真实工具卡输入及 Markdown 安全修正。

### Task 1: 固化数据库与历史消息契约

**Files:**
- Create: `nexa-rag-boot/src/main/resources/db/migration/V21__add_chat_message_generation_snapshot.sql`
- Modify: `nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql`
- Modify: `nexa-rag-chat/src/main/java/com/nexarag/chat/entity/ChatMessage.java`
- Modify: `nexa-rag-chat/src/main/java/com/nexarag/chat/domain/ChatMessageVO.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/domain/ChatGenerationTurnBO.java`
- Modify: `nexa-rag-chat/src/main/java/com/nexarag/chat/domain/ConversationMessageItemVO.java`
- Modify: `nexa-rag-chat/src/main/java/com/nexarag/chat/service/ConversationMessageService.java`
- Modify: `nexa-rag-chat/src/main/java/com/nexarag/chat/service/impl/ConversationMessageServiceImpl.java`
- Modify: `nexa-rag-chat/src/main/java/com/nexarag/chat/controller/ConversationController.java`
- Test: `nexa-rag-chat/src/test/java/com/nexarag/chat/service/impl/ConversationMessageServiceImplTest.java`
- Test: `nexa-rag-chat/src/test/java/com/nexarag/chat/controller/ConversationControllerTest.java`

- [ ] **Step 1: 写出快照字段与历史投影的失败测试**

```java
assertThat(ConversationMessageItemVO.class.getDeclaredFields())
        .extracting(Field::getName)
        .contains("generationId", "toolOperationsJson");

verify(mapper).update(argThat(message ->
        "CANCELLED".equals(message.getStatus())
                && "部分回答".equals(message.getContent())
                && "[{\"opId\":\"g1:tool:1\"}]".equals(message.getToolOperationsJson())), any());
```

- [ ] **Step 2: 运行 chat 模块测试并确认失败**

Run: `mvn -pl nexa-rag-chat -am test "-Dtest=ConversationMessageServiceImplTest,ConversationControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: 新字段和新服务签名尚不存在导致测试失败。

- [ ] **Step 3: 增加最小持久化模型**

```sql
ALTER TABLE chat_message
    ADD COLUMN generation_id VARCHAR(64) NULL COMMENT '生成任务ID' AFTER status,
    ADD COLUMN tool_operations_json MEDIUMTEXT NULL COMMENT '工具运行卡终态快照JSON' AFTER references_json,
    ADD KEY idx_chat_message_generation_id (generation_id);
```

```java
public record ChatMessageVO(String messageId, String conversationId, String userId,
                            long sequence, ChatMessageRole role, ChatMessageStatus status,
                            String content, String thinkingContent, String referencesJson,
                            String generationId, String toolOperationsJson,
                            Integer promptTokens, Integer completionTokens, Integer totalTokens,
                            String failureCode, String failureMessage,
                            LocalDateTime createdTime, LocalDateTime updatedTime) { }

public record ChatGenerationTurnBO(ChatMessageVO userMessage,
                                   ChatMessageVO assistantMessage) { }
```

新增 `beginGenerationTurn(conversationId, userId, userContent, generationId)`，返回 `ChatGenerationTurnBO`：在同一个会话锁与事务中先拒绝已有 `GENERATING` 助手消息，再按顺序写入用户消息和携带 `generationId` 的助手占位消息。完成、失败与取消方法均接收 `toolOperationsJson`，并只在原子 `GENERATING` 更新成功时完成最终化。`ConversationController` 只返回 `generationId`、`toolOperationsJson`，不返回内部思考、工具参数或工具结果正文。

- [ ] **Step 4: 运行 chat 回归测试**

Run: `mvn -pl nexa-rag-chat -am test "-Dtest=ConversationMessageServiceImplTest,ConversationControllerTest,ConversationControllerWebTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: PASS。

### Task 2: 定义版本化 SSE 事件与 Redis 重放缓冲

**Files:**
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/stream/ChatToolOperation.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/stream/ChatToolOperationStatus.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/stream/ChatStreamEventBuffer.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/stream/RedisChatStreamEventBuffer.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/stream/ChatStreamResumeService.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/stream/ChatStreamEvent.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/stream/ChatStreamEventType.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/constants/ChatGenerationRedisConstants.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/stream/RedisChatStreamEventBufferTest.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/stream/ChatStreamResumeServiceTest.java`

- [ ] **Step 1: 为版本顺序、越权恢复和过期缓冲写失败测试**

```java
assertThat(buffer.eventsAfter("g1", 1L))
        .extracting(ChatStreamEvent::eventVersion)
        .containsExactly(2L, 3L);
assertThatThrownBy(() -> resumeService.resume("g1", "other-user", 0L))
        .isInstanceOf(ClientException.class);
```

- [ ] **Step 2: 运行 workflow 流测试并确认失败**

Run: `mvn -pl nexa-rag-workflow -am test "-Dtest=RedisChatStreamEventBufferTest,ChatStreamResumeServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: 缓冲与恢复类型尚不存在导致失败。

- [ ] **Step 3: 实现公共事件模型和 Redis 缓冲**

```java
public record ChatToolOperation(String opId, String processId, long sequence,
                                String name, ChatToolOperationStatus status) { }

public record ChatStreamEvent(ChatStreamEventType type, long eventVersion,
                              String generationId, String conversationId, String messageId,
                              String content, List<ChatToolOperation> operations,
                              String errorCode, String errorMessage) { }
```

事件类型使用 `META`、`SNAPSHOT`、`ANSWER_DELTA`、`COMPLETE`、`ERROR`、`CANCELLED`。`RedisChatStreamEventBuffer` 使用 `generationId` 分区的 ZSET 保存 JSON 事件，以 `eventVersion` 为 score，并以“先写入缓冲、后发布”顺序发布按生成任务划分的 Redis Pub/Sub 主题；键、主题、TTL、最大事件数和最大载荷集中在 `ChatGenerationRedisConstants` 与配置中。恢复服务先按 Redis owner key 校验用户，读取恢复高水位并重放大于 `lastEventVersion` 的事件，订阅主题后再补读一次高水位之后的 ZSET 事件；前端按版本去重，从而消除重放与订阅之间的竞态窗口。

- [ ] **Step 4: 运行 workflow 流测试**

Run: `mvn -pl nexa-rag-workflow -am test "-Dtest=RedisChatStreamEventBufferTest,ChatStreamResumeServiceTest,ChatGenerationTaskManagerTest,ChatWorkflowStreamingUtilTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: PASS。

### Task 3: 在工作流中发布只读工具快照并执行工具优先编排

**Files:**
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/stream/ChatGenerationEventPublisher.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/tool/ReadOnlyToolExecutor.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/RetrievalNode.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/AnswerGenerationNode.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/ConversationContextNode.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/AssistantMessagePersistenceNode.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/constants/ChatWorkflowStateKeys.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/stream/ChatWorkflowStreamingUtil.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/chat/RetrievalNodeTest.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/chat/ChatWorkflowIntegrationTest.java`

- [ ] **Step 1: 写工具成功、重试耗尽和输出顺序的失败测试**

```java
InOrder order = inOrder(eventPublisher, modelGateway);
order.verify(eventPublisher).publishSnapshot(eq("g1"), argThat(ops ->
        ops.getFirst().status() == ChatToolOperationStatus.RUNNING));
order.verify(eventPublisher).publishSnapshot(eq("g1"), argThat(ops ->
        ops.getFirst().status() == ChatToolOperationStatus.SUCCESS));
order.verify(modelGateway).streamChat(any());

assertThat(finalState.get(TOOL_FAILURE_SUMMARIES)).contains("知识库检索不可用");
```

- [ ] **Step 2: 运行 workflow 节点测试并确认失败**

Run: `mvn -pl nexa-rag-workflow -am test "-Dtest=RetrievalNodeTest,ChatWorkflowIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: 还没有工具事件、失败上下文与重试执行器。

- [ ] **Step 3: 实现只读工具生命周期**

`RetrievalNode` 为每次检索生成稳定 `opId`，先发布 `RUNNING` 快照；经 `ReadOnlyToolExecutor` 在限定次数与退避内执行检索，成功后发布 `SUCCESS`，重试耗尽后发布 `FAILED` 并返回空检索结果及不含内部异常的失败摘要。失败摘要写入新的 `TOOL_FAILURE_SUMMARIES` 状态键并加入回答 Prompt。

`ConversationContextNode` 使用 `beginGenerationTurn` 原子写入用户消息和助手占位消息，并创建全轮 `ChatGenerationAccumulator`、注册 `ChatGenerationTaskManager`，使检索阶段已可响应取消；累积器经 `ChatWorkflowStateKeys.GENERATION_ACCUMULATOR` 传递，并持有当前 Markdown 和按 `opId` 合并的操作快照。`AnswerGenerationNode` 在所有工具节点终态后只读取既有 `ASSISTANT_MESSAGE_ID` 与累积器并调用模型；它通过 `ChatGenerationEventPublisher` 为正文发布约 30–80ms 合并的 `ANSWER_DELTA`。最终化节点把 `ChatToolOperation` 的最小 JSON 投影与正文一并持久化。

- [ ] **Step 4: 运行 workflow 回归测试**

Run: `mvn -pl nexa-rag-workflow -am test "-Dtest=RetrievalNodeTest,ChatWorkflowIntegrationTest,ChatWorkflowStreamingUtilTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: PASS。

### Task 4: 建立会话级活动生成约束与取消终态

**Files:**
- Modify: `nexa-rag-chat/src/main/java/com/nexarag/chat/service/ConversationMessageService.java`
- Modify: `nexa-rag-chat/src/main/java/com/nexarag/chat/service/impl/ConversationMessageServiceImpl.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/stream/ChatGenerationTaskManager.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/ConversationContextNode.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/AnswerGenerationNode.java`
- Test: `nexa-rag-chat/src/test/java/com/nexarag/chat/service/impl/ConversationMessageServiceImplTest.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/stream/ChatGenerationTaskManagerTest.java`

- [ ] **Step 1: 写同会话并发拒绝与取消保留快照的失败测试**

```java
assertThatThrownBy(() -> messageService.beginGenerationTurn("c1", "u1", "第二个问题", "g2"))
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("已有进行中的回答");

verify(messageService).cancelAssistantMessage("m1", "部分正文", expectedToolOperationsJson);
```

- [ ] **Step 2: 运行目标测试并确认失败**

Run: `mvn -pl nexa-rag-chat,nexa-rag-workflow -am test "-Dtest=ConversationMessageServiceImplTest,ChatGenerationTaskManagerTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: 并发检查与取消快照参数尚不存在导致失败。

- [ ] **Step 3: 实现后端强制约束**

在会话锁与事务中检查该会话的 `GENERATING` 助手消息；存在时在写入用户消息前拒绝整个新回合。无活动生成时，按“用户消息 → 助手占位消息”的顺序创建消息。`ConversationContextNode` 在工具阶段前注册任务；取消回调从累积器读取部分 Markdown 与当前操作快照，原子地将消息更新为 `CANCELLED`。不允许前端状态改变覆盖后端终态。

- [ ] **Step 4: 运行目标测试**

Run: `mvn -pl nexa-rag-chat,nexa-rag-workflow -am test "-Dtest=ConversationMessageServiceImplTest,ChatGenerationTaskManagerTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: PASS。

### Task 5: 暴露初始流与跨实例恢复流

**Files:**
- Modify: `nexa-rag-boot/src/main/java/com/nexarag/boot/controller/ChatController.java`
- Modify: `nexa-rag-boot/src/test/java/com/nexarag/boot/controller/ChatControllerTest.java`
- Create: `nexa-rag-boot/src/test/java/com/nexarag/boot/controller/ChatStreamResumeControllerTest.java`

- [ ] **Step 1: 写 SSE 初始元数据、恢复重放和越权恢复的失败测试**

```java
StepVerifier.create(controller.resume("g1", 4L))
        .assertNext(event -> assertThat(event.id()).isEqualTo("5"))
        .thenCancel()
        .verify();
```

- [ ] **Step 2: 运行 boot 控制器测试并确认失败**

Run: `mvn -pl nexa-rag-boot -am test "-Dtest=ChatControllerTest,ChatStreamResumeControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: 恢复端点不存在导致失败。

- [ ] **Step 3: 实现 SSE 映射与恢复端点**

保留 `POST /api/chat/stream` 作为初始生成入口，首个事件发送包含 `generationId`、`conversationId`、`messageId` 的 `META`。新增 `GET /api/chat/generations/{generationId}/stream?afterVersion={version}`，从 Redis 缓冲重放并连接 Pub/Sub 后续事件。所有 `ServerSentEvent` 使用 `eventVersion` 作为 SSE `id`；用户身份始终从 `CurrentUserContext` 读取。恢复缓冲缺失时返回可识别的业务错误，供前端进入后台运行降级态。

- [ ] **Step 4: 运行 boot 控制器测试**

Run: `mvn -pl nexa-rag-boot -am test "-Dtest=ChatControllerTest,ChatStreamResumeControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: PASS。

### Task 6: 将 Studio 事件模型改为结构化 reducer 与可恢复 SSE

**Files:**
- Create: `nexa-rag-studio/src/features/chat/chat-stream-reducer.ts`
- Create: `nexa-rag-studio/src/features/chat/chat-stream-reducer.test.ts`
- Modify: `nexa-rag-studio/src/types/index.ts`
- Modify: `nexa-rag-studio/src/lib/api.ts`
- Modify: `nexa-rag-studio/src/features/chat/ChatPage.tsx`

- [ ] **Step 1: 写 reducer 去重、快照覆盖、正文增量和恢复降级测试**

```ts
expect(reduceStream(state, snapshot(2, [tool('g1:tool:1', 'SUCCESS')])).messages[0].operations)
  .toEqual([tool('g1:tool:1', 'SUCCESS')])
expect(reduceStream(state, delta(1, '旧内容'))).toBe(state)
expect(reduceStream(state, { type: 'RECOVERY_EXHAUSTED' }).connectionState)
  .toBe('BACKGROUND_RUNNING')
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm --prefix nexa-rag-studio exec vitest run src/features/chat/chat-stream-reducer.test.ts`

Expected: 测试脚本、reducer 和事件类型尚不存在导致失败。

- [ ] **Step 3: 实现前端状态与恢复客户端**

在 `ChatMessageVO` 增加 `generationId`、`operations`、`connectionState`；`ChatStreamEvent` 增加 `eventVersion`、`operations` 与 `SNAPSHOT/ANSWER_DELTA`。`streamChat` 保持 `fetch + ReadableStream`，新增 `resumeChat(generationId, afterVersion)` 调用恢复 GET 端点。

`ChatPage` 改用 reducer：`SNAPSHOT` 以 `opId` 覆盖操作，`ANSWER_DELTA` 追加正文，旧版本忽略；断线以有限指数退避自动调用 `resumeChat`，重试耗尽仅显示后台运行状态，不把消息标记失败。正文状态更新以约 50ms 批处理，工具快照立即应用；生成结束后刷新会话列表。

- [ ] **Step 4: 配置并运行 Studio 测试与构建**

在 `nexa-rag-studio/package.json` 添加 `test` 脚本及 Vitest、React Testing Library 开发依赖；添加最小 `vitest.config.ts`。然后运行：

Run: `npm --prefix nexa-rag-studio test -- --run && npm --prefix nexa-rag-studio run build`

Expected: PASS；构建可能保留现有包体积告警，但不得有 TypeScript 错误。

### Task 7: 让工具卡使用真实操作，并修复 Markdown 安全边界

**Files:**
- Modify: `nexa-rag-studio/src/components/chat/ChatMessageItem.tsx`
- Modify: `nexa-rag-studio/src/components/chat/AgentToolExecutionBox.tsx`
- Modify: `nexa-rag-studio/src/components/chat/markdown/utils.ts`
- Modify: `nexa-rag-studio/src/components/chat/markdown/FeishuMarkdown.tsx`
- Modify: `nexa-rag-studio/src/styles/globals.css`
- Modify: `nexa-rag-studio/src/styles/feishu-markdown.css`
- Test: `nexa-rag-studio/src/components/chat/markdown/FeishuMarkdown.test.tsx`
- Test: `nexa-rag-studio/src/components/chat/AgentToolExecutionBox.test.tsx`

- [ ] **Step 1: 写真实工具卡、取消状态和原始 HTML 安全回归测试**

```tsx
render(<AgentToolExecutionBox status="CANCELLED" tools={[tool('g1:tool:1', 'system:knowledge_search', 'FAILED')]} />)
expect(screen.getByText('工具调用：system:knowledge_search')).toBeInTheDocument()
expect(screen.getByText('任务已停止')).toBeInTheDocument()

render(<FeishuMarkdown content={'<img src=x onerror=alert(1) />'} />)
expect(document.querySelector('img')).toBeNull()
```

- [ ] **Step 2: 运行组件测试并确认失败**

Run: `npm --prefix nexa-rag-studio exec vitest run src/components/chat/markdown/FeishuMarkdown.test.tsx src/components/chat/AgentToolExecutionBox.test.tsx`

Expected: 真实操作输入和 HTML 安全断言尚不成立。

- [ ] **Step 3: 接通展示组件并移除猜测逻辑**

`ChatMessageItem` 直接使用 `message.operations`，按 `processId` 聚合后传入 `AgentToolExecutionBox`；删除 `thinkingContent` 和默认伪工具名回退。工具卡只显示“任务执行中/任务执行完成/任务执行失败/任务已停止”与工具名称。

`FeishuMarkdown` 删除 `rehypeRaw`，继续使用 GFM 和已有自定义表格渲染器。删除 `globals.css` 中对飞书 Markdown 的重复覆盖，使 `feishu-markdown.css` 成为唯一规格来源；将表格单元格最大宽度、列表项间距和粗体字重对齐已确认的导出规格。

- [ ] **Step 4: 运行 Studio 全量测试与构建**

Run: `npm --prefix nexa-rag-studio test -- --run && npm --prefix nexa-rag-studio run build`

Expected: PASS。

### Task 8: 执行跨层验收与文档一致性检查

**Files:**
- Modify: `docs/glossary/chat-generation.md`（仅在实现术语与已确认决定不一致时修正）
- Modify: `docs/adr/2026-08-20-*.md`（仅在实现偏离已确认决定时修正）

- [ ] **Step 1: 执行后端定向回归**

Run: `mvn -pl nexa-rag-boot -am test "-Dtest=ChatControllerTest,ChatStreamResumeControllerTest,ConversationMessageServiceImplTest,RetrievalNodeTest,ChatWorkflowIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: PASS。

- [ ] **Step 2: 执行前端完整验证**

Run: `npm --prefix nexa-rag-studio test -- --run && npm --prefix nexa-rag-studio run build`

Expected: PASS。

- [ ] **Step 3: 审查工作区改动**

Run: `git diff --check && git diff -- docs/glossary/chat-generation.md docs/adr/2026-08-20-*.md`

Expected: 无空白错误；只核对本功能产生的文件，绝不暂存、回退或覆盖用户已有改动。

## 自检

- 规格覆盖：任务 1 覆盖最终快照和历史恢复；任务 2、5 覆盖 Redis 多实例恢复；任务 3、4 覆盖只读工具、重试、取消与单活动生成；任务 6、7 覆盖 Studio 流、工具卡和 Markdown 安全。
- 无占位符：每个实现任务都给出了文件、测试、命令和具体数据结构或行为。
- 命名一致：流事件统一使用 `eventVersion`；工具稳定标识统一使用 `opId`；跨实例恢复统一使用 `generationId + lastEventVersion`。
