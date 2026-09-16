# Langfuse 子 Span 注解化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 增加声明式 Langfuse 子 Span 注解，并将证据筛选和父子上下文扩展节点迁移至该注解。

**Architecture:** 在 infra 的 LangfuseTelemetry 门面与 SpEL 解析器上增加同步 LangfuseSpanAspect。切面在调用前解码 state carrier 并创建 Span，在正常返回后从结果 Map 提取安全属性，在异常时标记失败。workflow 节点只保留业务逻辑。

**Tech Stack:** Java 21、Spring AOP、OpenTelemetry Context、JUnit 5、AssertJ、Mockito。

---

### Task 1: 定义注解与结果表达式解析

**Files:**
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/observability/langfuse/aop/LangfuseSpan.java
- Modify: nexa-rag-infra/src/main/java/com/nexarag/infra/observability/langfuse/aop/LangfuseExpressionResolver.java
- Test: nexa-rag-infra/src/test/java/com/nexarag/infra/observability/langfuse/aop/LangfuseExpressionResolverTest.java

- [x] 新增测试：result Map 包含 quality record，表达式从 #result 读取 acceptedCount；非法键与非标量结果值被过滤。
- [x] 新增 LangfuseSpan，成员为 name、type、parentContextCarrier、attributes、resultAttributes；ExpressionResolver 增加 resolveResultAttributes，并复用 allowedKey 与 supportedValue。
- [x] 运行 mvn -pl nexa-rag-infra -am test "-Dtest=LangfuseExpressionResolverTest,LangfuseTelemetryAspectTest" "-Dsurefire.failIfNoSpecifiedTests=false"，通过。

### Task 2: 实现同步子 Span 切面

**Files:**
- Create: nexa-rag-infra/src/main/java/com/nexarag/infra/observability/langfuse/aop/LangfuseSpanAspect.java
- Modify: nexa-rag-infra/src/test/java/com/nexarag/infra/observability/langfuse/aop/LangfuseTelemetryAspectTest.java

- [x] 新增 Spring AOP 容器测试和 RecordingSpanScope，验证 name、EVALUATOR 类型、初始属性、结果属性、异常时 fail/close。
- [x] 实现仅拦截同步方法的 Around advice：调用前解析属性和 String carrier，startSpan；正常返回后写 resultAttributes；RuntimeException 时 fail 后重抛；finally close。不得影响 LangfuseTrace。
- [x] 运行 mvn -pl nexa-rag-infra -am test "-Dtest=LangfuseExpressionResolverTest,LangfuseTelemetryAspectTest" "-Dsurefire.failIfNoSpecifiedTests=false"，通过。

### Task 3: 迁移证据筛选节点

**Files:**
- Modify: nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/EvidenceQualityNode.java
- Modify: nexa-rag-workflow/src/main/java/com/nexarag/workflow/constants/ChatWorkflowTelemetryConstant.java
- Test: nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/chat/EvidenceQualityNodeTest.java

- [x] 新增节点测试：验证 EVALUATOR 类型、初始属性与 resultAttributes，并保留证据状态结果断言。
- [x] 删除 EvidenceQualityNode 中的显式 Langfuse Scope 生命周期；以 LangfuseSpan 声明 carrier、类型、初始属性、结果属性。SpEL 文本存入工作流遥测常量，禁止记录正文或片段 ID。
- [x] 运行 mvn -pl nexa-rag-workflow -am test "-Dtest=EvidenceQualityNodeTest,RetrievalNodeCandidateConfigTest" "-Dsurefire.failIfNoSpecifiedTests=false"，通过。

> 实施调整：RetrievalNode 的 topK 和阈值由节点内的 RetrievalProperties 补全，未必存在于 state。为避免注解化后上报错误的 `0` 值，保留其显式 Span，原有候选配置、重试和租户错误回归测试仍纳入最终验证。

### Task 4: 迁移父子上下文扩展节点

**Files:**
- Modify: nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/ParentContextExpansionNode.java
- Modify: nexa-rag-workflow/src/main/java/com/nexarag/workflow/constants/ChatWorkflowTelemetryConstant.java
- Modify: nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/chat/ParentContextExpansionNodeTest.java

- [x] 新增节点测试：验证 RETRIEVER 类型、重排序输入候选数和扩展后候选数；保留结果断言。
- [x] 给 apply 添加 LangfuseSpan；结果属性仅从返回的 RERANKED_RETRIEVAL_RESULTS 读取。
- [x] 运行 mvn -pl nexa-rag-workflow -am test "-Dtest=ParentContextExpansionNodeTest" "-Dsurefire.failIfNoSpecifiedTests=false"，通过。

### Task 5: 回归验证

**Files:**
- Modify: docs/superpowers/specs/2026-09-15-langfuse-span-annotation-design.md（仅当实现与设计不一致）

- [x] 运行 mvn -pl nexa-rag-infra,nexa-rag-workflow -am test "-Dtest=LangfuseExpressionResolverTest,LangfuseTelemetryAspectTest,EvidenceQualityNodeTest,RetrievalNodeCandidateConfigTest,ParentContextExpansionNodeTest,RerankNodeTest" "-Dsurefire.failIfNoSpecifiedTests=false"，8 项通过。
- [x] 运行 git diff --check，无空白错误。
- [x] 不提交：用户要求在 master 开发，且未授权提交、推送或创建 PR。

## 自检

- Task 1-2 覆盖注解、结果 SpEL、上下文与异常生命周期。
- Task 3-4 覆盖两个批准节点；RetrievalNode 因运行配置无法从 state 准确读取而保留显式 Scope，RerankNode 继续保留子上下文传递。
- Task 5 覆盖跨模块回归和变更质量。
