# Chat 前置系统工具推送 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在问题改写和意图识别阶段推送 `system:question_rewrite`、`system:intent_recognition` 的最小工具快照，缩短用户无反馈等待时间。

**Architecture:** 两个节点复用任务共享的 `ChatGenerationAccumulator` 和 `ChatGenerationEventPublisher`。每个节点在调用模型前推送 `RUNNING` 快照，结束或降级后推送 `SUCCESS` 快照；仅发送工具名称、顺序和状态。前端沿用已有 `SNAPSHOT` 渲染，不传递或持久化问题改写、意图识别的内容。

**Tech Stack:** Spring Boot、Alibaba Cloud AI Graph、Reactor SSE、Redis 事件缓冲、JUnit 5、Mockito。

---

### Task 1: 问题改写工具快照

**Files:**
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/QuestionRewriteNode.java`
- Modify: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/chat/QuestionRewriteNodeTest.java`

- [ ] **Step 1: 写失败测试**

在 `applyShouldFallbackToOriginalQuestionWhenModelUnavailable` 中注入 `ChatGenerationEventPublisher` mock 和 `ChatGenerationAccumulator`，断言按顺序发布两个 `SNAPSHOT`：

```java
verify(eventPublisher).publish(eventCaptor.capture());
assertThat(eventCaptor.getAllValues())
        .extracting(event -> event.operations().getFirst().status())
        .containsExactly(ChatToolOperationStatus.RUNNING, ChatToolOperationStatus.SUCCESS);
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl nexa-rag-workflow -am test "-Dtest=QuestionRewriteNodeTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: FAIL，因为节点尚未接收事件发布器或发布工具快照。

- [ ] **Step 3: 实现最小推送逻辑**

在模型调用前后创建同一 `opId` 的操作并发布快照：

```java
new ChatToolOperationDTO(generationId + ":tool:question-rewrite:1", generationId,
        1L, "system:question_rewrite", ChatToolOperationStatus.RUNNING);
```

无论模型成功还是按现有策略回退原问题，终态均更新为 `SUCCESS`；快照使用当前会话、追踪、生成和助手消息标识。

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -pl nexa-rag-workflow -am test "-Dtest=QuestionRewriteNodeTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: PASS。

### Task 2: 意图识别工具快照

**Files:**
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/IntentRecognitionNode.java`
- Modify: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/chat/IntentRecognitionNodeTest.java`

- [ ] **Step 1: 写失败测试**

在 `applyShouldUseEmptyIntentWhenResponseCannotBeParsed` 中断言发布器收到 `system:intent_recognition` 的 `RUNNING` 与 `SUCCESS` 两个快照。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl nexa-rag-workflow -am test "-Dtest=IntentRecognitionNodeTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: FAIL，因为节点尚未发布前置工具状态。

- [ ] **Step 3: 实现最小推送逻辑**

使用顺序 `2L` 和稳定标识 `generationId + ":tool:intent-recognition:1"`。意图模型成功和现有空意图降级路径均发布 `SUCCESS`，不将意图 ID、置信度或异常详情放入事件。

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -pl nexa-rag-workflow -am test "-Dtest=IntentRecognitionNodeTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: PASS。

### Task 3: 前置工具链回归验证

**Files:**
- Modify: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/chat/ChatWorkflowIntegrationTest.java`

- [ ] **Step 1: 补充集成断言**

在现有流式事件断言中要求前置工具快照的名称按顺序包含：

```java
assertThat(snapshot.operations()).extracting(ChatToolOperationDTO::name)
        .contains("system:question_rewrite", "system:intent_recognition", "system:knowledge_search");
```

- [ ] **Step 2: 运行最小回归集**

Run: `mvn -pl nexa-rag-workflow -am test "-Dtest=QuestionRewriteNodeTest,IntentRecognitionNodeTest,ChatWorkflowIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: PASS。

- [ ] **Step 3: 执行模块编译检查**

Run: `mvn -pl nexa-rag-workflow -am test-compile -DskipTests`

Expected: BUILD SUCCESS。
