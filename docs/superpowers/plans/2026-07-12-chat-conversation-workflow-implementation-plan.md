# 会话对话 Workflow 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于 Spring AI Alibaba Graph 构建支持多轮上下文、混合检索、流式回答、模型路由、取消和异步摘要的会话对话 Workflow。

**Architecture:** 先在 Retrieval 模块补齐对话读侧基础能力，再由 Workflow 模块使用 Map State 编排 Node 和 Edge；Chat 模块仅提供会话与消息生命周期能力，Model 模块只负责数据库路由、治理和主备降级。Chat Graph 以 Graph 原生流式输出向 Boot 层提供 SSE 数据。

**Tech Stack:** Java 21、Spring Boot、Spring AI Alibaba Graph、Project Reactor、MyBatis-Plus、Redis、Milvus、Elasticsearch/BM25、Resilience4j。

---

## 实施顺序与提交边界

本需求包含两个有先后依赖的子系统，必须按以下顺序实施：

1. 对话检索基础能力；
2. 会话 Workflow 与流式 Web 接口；
3. 模型路由、治理数据迁移与端到端验证。

每个任务完成后使用用户的 Git 提交流程创建独立中文 Conventional Commit；不提交 `.superpowers/`、IDE 文件和构建产物。

### Task 1: 补齐流式与任务型 Workflow 抽象

**Files:**
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/service/StreamingWorkflowGraphRunner.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/service/WorkflowService.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/service/impl/WorkflowServiceImpl.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/service/impl/WorkflowServiceImplTest.java`

- [x] **Step 1: 写失败测试，验证任务型和流式 Runner 分别按图名分发**

```java
@Test
void streamShouldDispatchToMatchedStreamingRunner() {
    StreamingRecordingRunner runner = new StreamingRecordingRunner("chat-conversation");
    WorkflowService service = new WorkflowServiceImpl(List.of(), List.of(runner));

    StepVerifier.create(service.stream("chat-conversation", Map.of("question", "你好")))
            .verifyComplete();
    assertThat(runner.receivedState()).containsEntry("question", "你好");
}
```

- [x] **Step 2: 运行失败测试**

Run: `mvn -pl nexa-rag-workflow -am -Dtest=WorkflowServiceImplTest "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: FAIL，缺少流式 Runner 接口和 `WorkflowService.stream(...)`。

- [x] **Step 3: 实现流式 Runner 接口与双 Runner Map 分发**

```java
public interface StreamingWorkflowGraphRunner {
    String graphName();

    Flux<GraphResponse<StreamingOutput<?>>> stream(Map<String, Object> initialState);
}
```

`WorkflowServiceImpl` 构造器分别接收 `List<WorkflowGraphRunner>` 和 `List<StreamingWorkflowGraphRunner>`，对每种 Map 执行重复 Graph 名称校验；`run(...)` 只查询任务型 Map，`stream(...)` 只查询流式 Map。

- [x] **Step 4: 运行测试并提交**

Run: `mvn -pl nexa-rag-workflow -am -Dtest=WorkflowServiceImplTest "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: PASS。

Commit: `refactor(workflow): 支持流式工作流分发`

### Task 2: 建立对话检索读侧契约

**Files:**
- Create: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/chat/ConversationRetrievalService.java`
- Create: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/retriever/vector/MilvusConversationRetriever.java`
- Create: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/retriever/keyword/Bm25ConversationRetriever.java`
- Create: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/chat/model/ConversationRetrievalRequest.java`
- Create: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/chat/model/IntentRecognitionResult.java`
- Create: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/chat/model/RetrievalChunk.java`
- Create: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/chat/model/RetrievalScope.java`
- Test: `nexa-rag-retrieval/src/test/java/com/nexarag/retrieval/chat/ConversationRetrievalServiceTest.java`

- [x] **Step 1: 写失败测试，验证检索接口能接收范围和动态 Top-K**

```java
@Test
void retrieveShouldForwardScopeAndTopK() {
    retrievalService.retrieve("改写问题", intentResult, RetrievalScope.INTENT_AND_GLOBAL, 30);

    verify(milvusRetriever).retrieve("改写问题", intentResult, RetrievalScope.INTENT_AND_GLOBAL, 30);
    verify(bm25Retriever).retrieve("改写问题", intentResult, RetrievalScope.INTENT_AND_GLOBAL, 30);
}
```

- [x] **Step 2: 定义不可变结果模型和服务接口**

```java
public interface ConversationRetrievalService {
    List<RetrievalChunk> retrieve(ConversationRetrievalRequest request);
}
```

`RetrievalChunk` 必须包含 `chunkId`、`documentId`、`chunkIndex`、`title`、`source`、`content`、`score`、`channel` 和 `rank`，不能暴露底层 SDK 对象。

- [x] **Step 3: 实现并行 Milvus/BM25 召回和单路降级**

Milvus 与 BM25 使用 `CompletableFuture` 并行执行；每个通道接收改写问题、意图范围、候选 Top-K 与向量阈值。`MilvusConversationRetriever` 仅在 `nexa.retrieval.vector.type=milvus` 时注册，`Bm25ConversationRetriever` 仅在 `nexa.retrieval.keyword.type=elasticsearch` 时注册，与现有 `MilvusVectorIndexClient`、`ElasticsearchKeywordIndexClient` 保持一致。任一通道异常记录中文日志并返回空列表；两个通道都失败时返回空候选而不是抛出异常。RRF 仍由 Workflow 的 `RetrievalFusionNode` 执行，避免基础服务承担 Graph 编排职责。

- [x] **Step 4: 实现读侧适配器并保留父片段定位信息**

Milvus 适配器负责查询向量库并返回片段原始分数；BM25 适配器负责 Elasticsearch 全文检索并返回片段原始分数。两者均保留 `parentChunkId`，不在读侧展开父片段。

- [x] **Step 5: 运行检索测试并提交**

Run: `mvn -pl nexa-rag-retrieval -am test`

Expected: PASS。

Commit: `feat(retrieval): 增加对话混合检索基础能力`

### Task 3: 在 Workflow 节点中实现改写、意图识别和重排序

**Files:**
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/prompt/ChatWorkflowPromptBuilder.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/QuestionRewriteNode.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/IntentRecognitionNode.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/RerankNode.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/chat/QuestionRewriteNodeTest.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/chat/IntentRecognitionNodeTest.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/chat/RerankNodeTest.java`

- [x] **Step 1: 写失败测试，验证改写失败时回退原问题**

```java
when(modelGateway.chat(any())).thenThrow(new ServiceException("模型不可用"));

assertThat(queryRewriteService.rewrite("原问题", context)).isEqualTo("原问题");
verify(modelGateway).chat(requestCaptor.capture());
assertThat(requestCaptor.getValue().routeKey()).isEqualTo("chat-rewrite");
```

- [x] **Step 2: 实现改写和意图识别路由调用**

改写节点使用 `ChatWorkflowPromptBuilder` 组装提示词并通过 `ModelGateway.chat(...)` 调用 `chat-rewrite`；意图节点以相同方式调用 `chat-intent`。意图解析失败时返回“无明确意图”的结果，使后续检索走 `INTENT_AND_GLOBAL`。

- [x] **Step 3: 写失败测试并实现重排序**

```java
assertThat(rerankService.rerank("改写问题", chunks, 5)).hasSize(5);
verify(modelGateway).rerank(requestCaptor.capture());
assertThat(requestCaptor.getValue().routeKey()).isEqualTo("rerank");
```

空候选直接返回空列表，不调用 Rerank 模型。

- [x] **Step 4: 运行测试并提交**

Run: `mvn -pl nexa-rag-retrieval -am test`

Expected: PASS。

Commit: `feat(retrieval): 接入改写意图和重排序服务`

### Task 4: 扩展 Chat 消息生命周期以支持部分内容最终化

**Files:**
- Modify: `nexa-rag-chat/src/main/java/com/nexarag/chat/service/ConversationMessageService.java`
- Modify: `nexa-rag-chat/src/main/java/com/nexarag/chat/service/impl/ConversationMessageServiceImpl.java`
- Modify: `nexa-rag-chat/src/test/java/com/nexarag/chat/service/impl/ConversationMessageServiceImplTest.java`

- [x] **Step 1: 写失败测试，验证失败和取消能保存部分回答**

```java
conversationMessageService.failAssistantMessage("m1", "部分回答", "MODEL_UNAVAILABLE", "模型不可用");

verify(mapper).updateById(argThat(message ->
        "FAILED".equals(message.getStatus()) && "部分回答".equals(message.getContent())));
```

- [x] **Step 2: 扩展服务接口**

```java
void failAssistantMessage(String messageId, String partialContent,
                          String failureCode, String failureMessage);

void cancelAssistantMessage(String messageId, String partialContent);
```

- [x] **Step 3: 实现状态条件更新**

只允许 `GENERATING` 迁移至 `COMPLETED`、`FAILED` 或 `CANCELLED`；状态已最终化时直接返回，避免完成和取消竞争导致内容覆盖。

- [x] **Step 4: 运行测试并提交**

Run: `mvn -pl nexa-rag-chat -am test`

Expected: PASS。

Commit: `feat(chat): 支持流式消息失败和取消最终化`

### Task 5: 实现 Chat 流式运行时基础设施

**Files:**
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/stream/ChatStreamEventType.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/stream/ChatStreamEvent.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/stream/ChatGenerationAccumulator.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/stream/ChatGenerationTaskManager.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/stream/ChatGenerationCancellationHandler.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/stream/ChatWorkflowStreamingUtil.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/stream/ChatWorkflowStreamingUtilTest.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/stream/ChatGenerationTaskManagerTest.java`

- [x] **Step 1: 写失败测试，验证模型分片被映射为 TOKEN，结束时回写完整 State**

```java
StepVerifier.create(ChatWorkflowStreamingUtil.toGraphStream(nodeClass, state,
        Flux.just(ChatModelStreamResponse.message("你"), ChatModelStreamResponse.message("好"))))
        .assertNext(response -> assertThat(response.getResult().type()).isEqualTo(ChatStreamEventType.TOKEN))
        .assertNext(response -> assertThat(response.getResult().type()).isEqualTo(ChatStreamEventType.TOKEN))
        .assertNext(response -> assertThat(response.isDone()).isTrue())
        .verifyComplete();
```

- [x] **Step 2: 实现线程安全累积器和流式转换**

累积器使用同步快照保存正文、Token 和 finishReason。正常完成与模型最终失败均产生 `GraphResponse.done(...)`，分别写入 `COMPLETED` 与 `FAILED`；只有 Graph State 损坏等不可恢复系统错误才返回 `GraphResponse.error(...)`。

- [x] **Step 3: 实现跨实例取消管理**

使用本地缓存、Redis Key `nexa:chat:generation:cancel:{generationId}` 和 Topic `nexa:chat:generation:cancel`；取消操作校验 userId，使用 CAS 确保取消最终化回调只执行一次，并支持“先取消后绑定流”的情况。

- [x] **Step 4: 运行测试并提交**

Run: `mvn -pl nexa-rag-workflow -am -Dtest=ChatWorkflowStreamingUtilTest,ChatGenerationTaskManagerTest "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: PASS。

Commit: `feat(workflow): 增加会话流式输出和取消任务管理`

### Task 6: 实现 Chat Graph State、Node、检索回环与 Runner

**Files:**
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/constants/ChatWorkflowGraphConstants.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/constants/ChatWorkflowNodeConstants.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/constants/ChatWorkflowStateKeys.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/request/ChatWorkflowRequest.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/dispatcher/chat/RetrievalFusionDispatcher.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/ConversationValidationNode.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/ConversationContextNode.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/QuestionRewriteNode.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/IntentRecognitionNode.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/RetrievalNode.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/RetrievalFusionNode.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/RerankNode.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/AnswerGenerationNode.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/AssistantMessagePersistenceNode.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/config/ChatWorkflowConfiguration.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/service/chat/ChatWorkflowRunner.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/dispatcher/chat/RetrievalFusionDispatcherTest.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/config/ChatWorkflowConfigurationTest.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/service/chat/ChatWorkflowRunnerTest.java`

- [x] **Step 1: 写 Dispatcher 失败测试，验证仅回环一次且扩大 Top-K**

```java
assertThat(dispatcher.apply(stateWith(List.of(), 1, 2, 10))).isEqualTo(RETRIEVAL_NODE);
assertThat(state.value(RETRIEVAL_ROUND)).isEqualTo(2);
assertThat(state.value(RETRIEVAL_TOP_K)).isEqualTo(30);
assertThat(state.value(RETRIEVAL_SCOPE)).isEqualTo(INTENT_AND_GLOBAL);

assertThat(dispatcher.apply(stateWith(List.of(), 2, 2, 30))).isEqualTo(RERANK_NODE);
```

- [x] **Step 2: 实现节点并限制职责**

会话校验节点新会话时创建临时标题并通过虚拟线程触发 `chat-title`；上下文节点使用 `ConversationContextService.loadForTurn(...)` 后保存用户消息；回答节点先创建 `GENERATING` 占位消息，再用 `chat-answer` 调用 `ModelGateway.streamChat(...)`；最终化节点按 `STREAM_STATUS` 调用完成、失败或取消服务，并只在 `COMPLETED` 时刷新上下文和触发摘要。

- [x] **Step 3: 实现 Graph 条件边并写配置测试**

配置必须包含：`START → 校验 → 上下文 → 改写 → 意图 → 检索 → 融合`，融合条件边映射 `RETRIEVAL_NODE` 与 `RERANK_NODE`，最后 `Rerank → Answer → Persistence → END`。

- [x] **Step 4: 实现 Chat Runner 并运行测试**

Runner 将 `ChatWorkflowRequest` 转为初始 State，生成 `chat:{traceId}` 的 `RunnableConfig.threadId`，调用编译后 Graph 的流式方法。

Run: `mvn -pl nexa-rag-workflow -am test`

Expected: PASS。

Commit: `feat(workflow): 实现会话对话 Graph 编排`

### Task 7: 实现 Chat Controller 与 SSE 映射

**Files:**
- Create: `nexa-rag-boot/src/main/java/com/nexarag/boot/controller/ChatController.java`
- Create: `nexa-rag-boot/src/main/java/com/nexarag/boot/controller/request/ChatStreamRequest.java`
- Create: `nexa-rag-boot/src/test/java/com/nexarag/boot/controller/ChatControllerTest.java`

- [x] **Step 1: 写失败测试，验证用户身份不从请求体读取且返回 SSE TOKEN 事件**

```java
mockMvc.perform(post("/api/chat/stream")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.TEXT_EVENT_STREAM)
        .content("{\"content\":\"你好\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/event-stream")));
```

- [x] **Step 2: 实现发起与取消接口**

`POST /api/chat/stream` 从 `CurrentUserContext` 获取 userId，生成雪花 `generationId` 和 UUID `traceId`，调用 Chat Workflow 并映射为 `ServerSentEvent<ChatStreamEvent>`；`DELETE /api/chat/generations/{generationId}` 由任务管理器校验用户后取消。

- [x] **Step 3: 在 SSE 取消时触发任务取消并运行测试**

Run: `mvn -pl nexa-rag-boot -am -Dtest=ChatControllerTest "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: PASS。

Commit: `feat(chat): 提供会话流式对话和取消接口`

### Task 8: 生成模型路由与治理迁移数据

**Files:**
- Create: `nexa-rag-boot/src/main/resources/db/migration/V13__configure_chat_workflow_model_routes.sql`
- Modify: `nexa-rag-chat/src/main/java/com/nexarag/chat/constants/ChatContextConstants.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/constants/ChatModelRouteConstants.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/route/RegistryFirstModelRouterTest.java`

- [x] **Step 1: 写路由测试，验证五个 routeKey 都能选中 CHAT 候选配置**

```java
for (String routeKey : List.of("chat-answer", "chat-rewrite", "chat-intent", "chat-summary", "chat-title")) {
    assertThat(router.plan(new ModelRouteContext(routeKey, false)).candidates()).isNotEmpty();
}
```

- [x] **Step 2: 编写可重复执行的 SQL 迁移**

迁移必须修复当前 `answer` 路由错误关联和失效治理绑定，创建五个 `CHAT + PRIMARY_BACKUP` 路由、对应主备 `model_route_config` 关联，以及 `ROUTE` 绑定的治理记录；`chat-answer` 使用高能力模型配置，改写和意图使用普通模型配置，摘要和标题使用轻量模型配置。每次路由数据变化后递增 `model_registry_version.version_no`。

- [x] **Step 3: 配置路由常量并运行模型模块测试**

Run: `mvn -pl nexa-rag-model -am test`

Expected: PASS。

Commit: `feat(model): 配置会话工作流模型路由和治理`

### Task 9: 端到端回归与文档收尾

**Files:**
- Modify: `docs/superpowers/specs/2026-07-12-chat-conversation-workflow-design.md`
- Modify: `TODO.md`

- [x] **Step 1: 添加 Workflow 集成测试**

覆盖新会话 META、Redis 回源、检索回环、模型首片前 fallback、正常 COMPLETE、模型最终失败、主动取消和摘要触发。

- [x] **Step 2: 运行完整验证**

Run: `mvn -pl nexa-rag-boot -am test`

Expected: PASS；若现有 `IntegrationProfileConfigurationTest` 因环境变量或外部 MySQL 配置失败，单独记录该既有环境阻塞，并补跑 Chat、Workflow、Model 的定向测试。

- [x] **Step 3: 更新设计文档和 TODO 并提交**

在设计文档中记录实施状态，在 `TODO.md` 勾选已完成的模型路由、流式 Workflow 和取消能力。

Commit: `docs(chat): 更新会话工作流实施状态`
