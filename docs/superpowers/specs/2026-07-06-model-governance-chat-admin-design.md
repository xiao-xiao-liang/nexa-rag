# 模型治理、Chat 接口与临时管理页设计

## 背景

`docs/superpowers/specs/2026-07-05-model-gateway-provider-design.md` 的模型网关 Provider 接入目标已基本落地。当前 `nexa-rag-model` 已具备统一 `ModelGateway`、Chat/Embedding/Rerank Provider、模型连接测试、模型配置管理和注册表刷新基础能力。

本设计承接剩余模型模块工作，补齐裸 Chat 调用入口、模型治理能力、fallback、权重路由和一个临时 HTML 管理页。`document` 与 `retrieval` 业务链路接入不在本轮实现，后续单独设计和实施。

## 目标

1. 提供裸 Chat Controller，支持同步 Chat 调用。
2. 提供流式 Chat Controller，后端使用 `Flux` 输出 SSE。
3. 新增模型治理配置表，治理配置落库。
4. 接入 Resilience4j 的限流、并发隔离、熔断、重试。
5. Provider 超时继续使用 HTTP 客户端超时，本轮不接 Resilience4j TimeLimiter。
6. 将路由从单个决策升级为候选计划，支持 fallback。
7. 实现 `PRIMARY_BACKUP` 与 `WEIGHT` 路由。
8. `RULE` 路由仅预留，不实现规则匹配。
9. 增强模型调用日志，记录多次尝试和 fallback 关系。
10. 提供临时 HTML 半管理页面，用于模型配置、路由、治理参数和 Chat 流式验证。

## 非目标

1. 本轮不接入 `document` 入库链路。
2. 本轮不接入 `retrieval` 检索链路。
3. 本轮不实现 RAG Chat 会话、记忆、召回、重排、生成工作流。
4. 本轮不实现 Resilience4j TimeLimiter。
5. 本轮不实现规则路由匹配逻辑。
6. 本轮不实现动态权重。
7. 本轮不实现正式 Vue/React 前端。
8. 本轮不实现 Sa-Token 鉴权。

## 阶段一收尾：裸 Chat Controller

阶段一仍需补一个 Chat Controller，作为模型模块的裸 Chat 验证入口。该 Controller 不承担最终聊天业务职责，不处理会话、记忆、RAG 召回或消息保存。

新增接口：

```text
POST /api/model/chat
POST /api/model/chat/stream
```

同步接口调用 `ModelGateway.chat(...)`，用于普通 JSON 响应。

流式接口调用新增的 `ModelGateway.streamChat(...)`，返回 `text/event-stream`。

请求体示例：

```json
{
  "routeKey": "chat.default",
  "messages": [
    {
      "role": "USER",
      "content": "你好"
    }
  ],
  "options": {}
}
```

同步 Chat 和流式 Chat 都应使用统一模型路由、调用日志和治理能力。连接测试接口仍使用同步 Chat，不使用流式 Chat。

## 流式 Chat

`ModelGateway` 增加流式方法：

```java
public Flux<ChatModelStreamResponse> streamChat(ChatModelRequest request)
```

同步 Chat 与流式 Chat 并存：

- `chat(...)`：适合连接测试、意图识别、标题生成等短调用。
- `streamChat(...)`：适合用户聊天体验和临时管理页验证。

Controller 建议返回：

```java
Flux<ServerSentEvent<ChatModelStreamResponse>>
```

SSE 事件建议：

```text
event: message
data: {"content":"你好"}

event: error
data: {"errorCode":"xxx","errorMessage":"模型流式输出失败"}

event: done
data: {"finishReason":"STOP"}
```

流式 Chat fallback 规则：

1. 首个 token 输出前允许 fallback。
2. 首个 token 输出后不再 fallback。
3. 已输出后发生异常时，发送 error 事件，记录失败日志并结束流。

流式 token usage 初版允许记录为 0，后续补充流式 token 统计增强。

## 治理配置表

新增 `model_governance_config` 表，与 `model_config` 一对一，表达某个模型实例的运行时保护策略。

核心字段：

| 字段 | 说明 |
| --- | --- |
| governance_id | 治理配置 ID |
| config_id | 模型配置 ID |
| timeout_ms | HTTP 客户端超时时间 |
| retry_enabled | 是否启用重试 |
| max_attempts | 最大尝试次数 |
| retry_wait_ms | 重试间隔 |
| circuit_enabled | 是否启用熔断 |
| failure_rate_threshold | 失败率阈值 |
| slow_call_rate_threshold | 慢调用比例阈值 |
| slow_call_duration_ms | 慢调用耗时阈值 |
| minimum_number_of_calls | 熔断统计最小调用数 |
| sliding_window_size | 熔断滑动窗口大小 |
| wait_duration_in_open_state_ms | 熔断打开后等待时间 |
| rate_limit_enabled | 是否启用限流 |
| limit_for_period | 单周期允许请求数 |
| limit_refresh_period_ms | 限流周期 |
| timeout_duration_ms | 限流等待时间 |
| bulkhead_enabled | 是否启用并发隔离 |
| max_concurrent_calls | 最大并发调用数 |
| max_wait_duration_ms | 并发隔离最大等待时间 |
| enabled | 是否启用治理配置 |
| remark | 备注 |
| create_time | 创建时间 |
| update_time | 更新时间 |
| del_flag | 逻辑删除标记 |
| delete_time | 删除时间 |

治理配置优先级：

```text
model_governance_config
  > model_config.timeout_ms / max_retries
  > yml 全局默认治理配置
```

如果某个 `model_config` 没有治理配置，则使用 yml 全局默认值，模型调用仍可正常执行。

## 治理执行链

模型执行链调整为：

```text
ModelGateway
  -> ModelExecutionTemplate
  -> ModelRouter 生成 ModelRoutePlan
  -> ModelGovernanceExecutor
  -> ModelProviderDispatcher
  -> Provider
```

Resilience4j 执行顺序：

```text
RateLimiter
  -> Bulkhead
    -> CircuitBreaker
      -> Retry
        -> Provider Call
```

语义：

1. 先限流，避免请求持续进入模型调用链。
2. 再并发隔离，保护 JVM 线程和模型服务。
3. 熔断观察重试后的最终调用结果。
4. 重试只包裹真实 Provider 调用。

本轮不接 Resilience4j TimeLimiter。超时由 Provider HTTP 客户端超时控制。TimeLimiter 写入 TODO。

## 路由计划与 Fallback

现有 `ModelRouter.route(...)` 返回单个 `ModelRouteDecision`，无法支持 fallback。需要引入：

```text
ModelRoutePlan
  routeKey
  strategy
  candidates: List<ModelRouteDecision>
```

职责划分：

- `ModelRouter`：根据路由策略生成候选链。
- `ModelExecutionTemplate`：按候选链依次尝试。
- `ModelGovernanceExecutor`：对单个候选执行治理保护。

fallback 触发条件：

1. Provider 调用异常。
2. CircuitBreaker 打开。
3. RateLimiter 拒绝。
4. Bulkhead 拒绝。

fallback 不关心 provider 边界，只要求 `modelType` 一致。候选可来自不同厂商，例如 OpenAI 主模型降级到 Ollama 备用模型。

管理接口保存 `model_route_config` 时需要校验：

```text
model_route.model_type == model_config.model_type
```

## 路由策略

### PRIMARY_BACKUP

实现规则：

```text
PRIMARY 按 priority 升序
BACKUP 按 priority 升序
```

调用时先尝试 PRIMARY，失败、熔断、限流或并发隔离拒绝时继续尝试 BACKUP。

### WEIGHT

实现静态权重路由。

候选筛选：

1. `model_route_config.enabled = true`
2. `model_config.enabled = true`
3. `route.model_type == config.model_type`
4. `weight > 0`

选择算法：

1. 计算候选权重总和。
2. 生成随机数，按权重区间选中候选。
3. 选中候选失败时，从剩余候选继续按权重选择。
4. 剩余候选权重都无效时，按 priority 兜底。

动态权重本轮不实现。后续可基于 `ModelRuntimeMetrics` 与 `DynamicWeightCalculator` 扩展。

### RULE

`RULE` 策略仅预留，不实现规则匹配。若运行时遇到 `RULE` 路由，返回明确错误：

```text
当前暂未支持规则路由
```

规则路由写入 TODO。

## 调用日志增强

`model_call_log` 需要记录 fallback 链路。

新增字段：

| 字段 | 说明 |
| --- | --- |
| attempt_no | 第几次尝试 |
| fallback_from_call_id | 从哪条调用日志降级而来 |
| fallback_reason | 降级原因 |

记录规则：

1. 每次实际模型尝试都写一条日志。
2. 主模型失败时记录 `FAILED`。
3. 备用模型成功时记录 `FALLBACK_SUCCESS`，并关联上一条失败日志。
4. 所有候选失败时，最后抛出模型不可用异常。

后续可基于这些字段统计主模型失败率、fallback 命中率和模型成本。

## 简单 HTML 管理页

新增临时管理页：

```text
nexa-rag-boot/src/main/resources/static/model-admin.html
```

定位：

- 开发验证用半管理页面。
- 不替代后续 Vue/React 正式前端。
- 不做登录鉴权。

页面能力：

1. 查看 provider 推荐值。
2. 查看模型配置列表。
3. 新增、编辑、删除模型配置。
4. 查看模型路由列表。
5. 新增、编辑、删除模型路由。
6. 给路由绑定模型配置。
7. 编辑模型治理配置。
8. 测试模型 config。
9. 测试模型 route。
10. 手动刷新 registry。
11. 同步裸 Chat 调用。
12. 流式裸 Chat 调用并实时追加输出。

实现方式：

- 原生 HTML + CSS + 少量 JS。
- 表格区展示列表。
- 表单区编辑配置。
- 响应输出区展示接口返回。
- 流式 Chat 使用 `fetch` + `ReadableStream` 读取 SSE。

## REST 接口补充

Chat：

```text
POST /api/model/chat
POST /api/model/chat/stream
```

治理配置：

```text
GET /api/model/configs/{configId}/governance
PUT /api/model/configs/{configId}/governance
```

也可以在模型配置详情中合并返回 governance，便于 HTML 页面统一展示。

## TODO 更新

落地时同步更新 `TODO.md`：

- 标记 Chat 真实调用完成。
- 标记 Chat 模型连接测试完成。
- 新增 TimeLimiter 未实现。
- 新增规则路由未实现。
- 新增动态权重未实现。
- 新增流式 token 统计增强。
- 新增正式 Vue/React 前端管理页。

## 测试策略

至少覆盖：

1. `ModelChatController` 同步 Chat 调用。
2. `ModelChatController` 流式 Chat SSE 输出。
3. `ModelGateway.streamChat(...)` 路由和 Provider 分发。
4. 治理配置 CRUD。
5. 治理配置默认值合并。
6. RateLimiter 拒绝触发 fallback。
7. Bulkhead 拒绝触发 fallback。
8. CircuitBreaker 打开触发 fallback。
9. Retry 包裹 Provider 调用。
10. `PRIMARY_BACKUP` 候选链排序。
11. `WEIGHT` 静态权重选择和失败后剩余候选选择。
12. `RULE` 策略返回明确不支持错误。
13. 调用日志记录 `attempt_no`、`fallback_from_call_id`、`fallback_reason`。
14. HTML 页面静态资源可访问。

真实外部模型调用继续作为可选集成测试，不纳入普通单元测试强依赖。
