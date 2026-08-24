# Chat SSE Event Driver Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让新对话创建后的 `META` 事件可立即写入 SSE 响应，避免页面一直停留在 `/chat`。

**Architecture:** HTTP 响应仍在同一 Reactor 链路中订阅并驱动 Chat 工作流，但不再消费 Graph 输出中的未完成 Future。SSE 数据只来自 `ChatGenerationEventPublisher` 的实时事件流，工作流异常仍向调用方传播。

**Tech Stack:** Spring MVC、Project Reactor、Spring AI Alibaba Graph、Redis 事件缓冲。

---

### Task 1: 移除旧 Graph 输出阻塞路径

**Files:**

- Modify: `nexa-rag-boot/src/main/java/com/nexarag/boot/controller/ChatController.java:87-111`
- Test: 未新增。用户明确要求无需回归测试。

- [x] **Step 1: 保留工作流订阅，但忽略其 Graph 输出**

将 Graph 执行流转换为不产生 `ChatStreamEvent` 的完成信号，再与实时事件流合并。这样仍由 HTTP 请求生命周期订阅、取消并感知工作流异常，同时不再调用 `response.getOutput().join()`。

- [x] **Step 2: 删除不再使用的旧事件兼容方法与导入**

删除 `NodeOutput`、`StreamingOutput` 的导入及 `enrichLegacyEvent` 私有方法；不修改引用预览等无关代码。

- [x] **Step 3: 执行编译与差异检查**

运行 `mvn -pl nexa-rag-boot -am -DskipTests compile` 和对目标文件的 `git diff --check`。

**不提交：** 用户明确要求直接修改 `master`，未授权创建 Git 提交。
