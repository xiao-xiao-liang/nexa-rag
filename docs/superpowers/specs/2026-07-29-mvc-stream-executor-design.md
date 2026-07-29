# MVC 流式执行器设计

## 目标

为 Spring MVC 的 Flux SSE 响应提供受控的异步执行器，消除默认 `SimpleAsyncTaskExecutor` 的生产告警，并按单实例 80 条活跃流配置容量基线。

## 根因

项目已在 `ExecutorConfiguration` 中注册 `chatSummaryExecutor`。它属于 `Executor`，导致 Spring Boot 不再自动创建 `applicationTaskExecutor`。MVC 因而在处理 `Flux<ServerSentEvent<ChatStreamEvent>>` 时回退到框架内置的 `SimpleAsyncTaskExecutor`。

## 设计

仅修改 `nexa-rag-chat` 模块中的 `ExecutorConfiguration`：

- 保留现有 `chatSummaryExecutor`，继续专用于会话摘要任务。
- 新增 Bean 名称为 `applicationTaskExecutor` 的 `ThreadPoolTaskExecutor`，供 Spring Boot MVC 自动接入。
- 设置核心线程数与最大线程数均为 80，队列容量为 0，线程名前缀为 `mvc-stream-`。
- 不新增 `WebMvcConfigurer`、不改控制器与 Flux 链路，也不新增测试。

## 运行语义

该执行器与摘要任务隔离。80 条活跃流可立即获得执行资源；超过容量的任务不在服务端队列中堆积，由执行器拒绝，避免长时间占用 HTTP 连接。

## 验收标准

启动应用并请求 `/api/chat/stream` 后，日志不再出现 Spring MVC 默认 `SimpleAsyncTaskExecutor` 的生产告警；现有摘要执行器的名称与行为保持不变。
