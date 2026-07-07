# 模型网关底座完善设计

## 背景

`nexa-rag-model` 已经具备统一模型入口、真实 Provider 调用、数据库优先路由、注册表快照、基础治理执行器、调用日志和临时管理页。下一阶段暂不接入 `document` 和 `retrieval` 业务链路，先把模型模块完善为稳定、可插拔、可观测的统一模型访问层。

本设计聚焦模型底座本身，不编排 RAG 业务流程，不把业务 Prompt 内容集中放入模型模块。

## 目标

1. 让模型调用运行时配置可刷新、可治理、可观测。
2. 让模型配置、路由、治理、刷新、连接测试管理接口闭环。
3. 让 Provider 与 Token 用量统计具备可插拔扩展边界。
4. 为后续接入 `document`、`retrieval`、`chat`、`workflow` 提供稳定统一入口。

## 非目标

1. 当前阶段不接入 `document` 和 `retrieval` 业务链路。
2. 当前阶段不实现 `model_call_trace` 聚合表，只加入 TODO。
3. 当前阶段不实现 `INFRA_MQ` 真实消息适配测试，只保留接口与配置预留。
4. 当前阶段不实现 OpenAI、DeepSeek、智谱、火山、百度、腾讯等厂商的完整 Token 用量统计适配。

## 架构边界

`nexa-rag-model` 是统一模型访问层。业务模块只依赖 `ModelGateway`，传入 `routeKey`、`bizType`、`bizId` 和请求内容。

模块内部分层如下：

- `gateway`：统一入口，提供 Chat、Stream Chat、Embedding、Rerank。
- `route`：根据注册表快照生成候选模型链，支持主备、权重，预留规则路由。
- `execution`：执行候选链，处理 fallback、日志、同步治理和流式治理。
- `governance`：解析并执行 retry、熔断、限流、并发隔离、TimeLimiter、stream timeout。
- `provider`：屏蔽厂商协议差异，官方厂商走枚举，自定义 OpenAI-compatible 走 `CUSTOM_OPENAI`。
- `registry`：维护 JVM 内模型运行时配置快照，包含模型配置、路由、路由关联、治理配置和版本。
- `prompt`：提供模板加载、渲染、Nacos 覆盖能力，Prompt 内容归业务模块。
- `controller/service`：提供模型配置、路由、治理、刷新、连接测试等管理接口。
- `observability` 或现有日志服务：记录调用日志、状态、Token、耗时、fallback、流式指标。

## Prompt 边界

Prompt 能力在 `nexa-rag-model`，Prompt 内容在业务模块。

`nexa-rag-model` 负责：

- 模板加载。
- 模板渲染。
- 本地 resources 扫描。
- Nacos 覆盖本地模板。
- 模板来源、版本和缺失错误处理。

业务模块负责：

- Prompt key 命名。
- Prompt 文件内容。
- Prompt 输入变量 DTO。
- 选择哪个 Prompt 用于哪个 Node 或 Service。

Nacos 只覆盖 Prompt 模板，不覆盖模型配置、路由、密钥或治理参数。

## 同步调用链路

```text
业务模块
  -> ModelGateway.chat / embedding / rerank
  -> ModelExecutionTemplate
  -> ModelRouter.plan(routeKey)
  -> ModelRoutePlan(candidates)
  -> 对候选模型逐个尝试
      -> ModelGovernanceResolver.resolve(command, decision)
      -> ModelGovernanceExecutor.execute(...)
      -> ModelProviderDispatcher
      -> ProviderAdapter
      -> 厂商模型 API
      -> TokenUsageStatistics
      -> ModelCallLogService.markSuccess / markFailed / markTimeout
  -> 返回业务响应
```

规则：

- `ModelRouter` 只生成候选链，不执行调用。
- `ModelExecutionTemplate` 负责 attempt 循环和 fallback。
- 每个 attempt 一条 `model_call_log`。
- 主模型失败后备用模型成功时，主模型 attempt 记为 `FAILED` 或 `TIMEOUT`，备用模型 attempt 记为 `SUCCESS`，并记录 `fallback_from_call_id` 和 `fallback_reason`。
- 明细日志不使用 `FALLBACK_SUCCESS`。
- 所有候选失败时，向上抛统一业务异常。

## 流式调用链路

流式 Chat 只在首个 chunk 输出前允许 fallback。

```text
业务模块
  -> ModelGateway.streamChat
  -> ModelExecutionTemplate.executeStream
  -> ModelRouter.plan(routeKey)
  -> 逐个尝试候选模型
      -> 建立 RUNNING 日志
      -> 创建 Flux
      -> 等待首个 chunk
          -> 首 chunk 前失败或超时：记录失败并尝试下一个候选
          -> 首 chunk 成功：锁定该候选并向下游转发
      -> 已输出 chunk 后，不再 fallback
      -> complete/error/cancel/timeout 时更新日志
```

状态规则：

- 正常 complete：`SUCCESS`。
- 客户端主动断开：`CANCELED`。
- 首 chunk 超时或最大流持续时间超时：`TIMEOUT`。
- Provider 异常：`FAILED`。

流式治理配置包含：

- 首 chunk 超时。
- 最大流持续时间。

同步 TimeLimiter 不直接套用到流式调用。

## 治理配置

`application.yml` 只保留少量治理配置，并必须用中文注释说明作用和可选值：

```yaml
nexa:
  model:
    governance:
      # 治理配置绑定模式，用于决定运行时按什么维度查找 DB 治理配置。
      # 可选值：
      # CONFIG：按模型配置 config_id 绑定，同一个模型配置在所有路由下共用一套治理策略。
      # ROUTE：按模型路由 route_key 绑定，同一个模型在不同业务路由下可使用不同治理策略。
      binding-mode: CONFIG

      # 创建模型配置或模型路由时是否自动创建默认治理配置。
      # true：自动创建默认治理配置，已存在时不覆盖。
      # false：不自动创建，需要用户在模型治理页面手动维护。
      auto-create-default: true
```

不在 yml 中维护冗长默认治理参数。默认策略由代码中的 `DefaultModelGovernancePolicyFactory` 按 `ModelType` 生成。

创建规则：

- 创建 `model_config` 时自动创建 `CONFIG + config_id` 默认治理配置。
- 创建 `model_route` 时自动创建 `ROUTE + route_key` 默认治理配置。
- 已存在同绑定治理配置时不覆盖。
- 管理接口提供“恢复默认治理策略”，用户显式触发时才覆盖。
- 覆盖、修改、启停、删除治理配置后 bump registry version 并发布刷新事件。

运行时只启用一种绑定模式：

- `CONFIG`：按 `decision.configId` 查治理配置。
- `ROUTE`：按 `command.routeKey` 查治理配置。

## 默认治理策略

默认策略按模型类型分级生成，原则是默认不激进，优先保护系统不被模型服务拖死。

- Chat 同步：默认开启熔断、限流、并发隔离、TimeLimiter；默认不重试或极少重试。
- Chat 流式：默认开启熔断、限流、并发隔离；配置首 chunk 超时和最大流持续时间；默认不重试。
- Embedding：默认开启熔断、限流、并发隔离、TimeLimiter；可启用保守重试，因为向量化通常幂等。
- Rerank：默认开启熔断、限流、并发隔离、TimeLimiter；重试保持保守。

具体数值在实施计划中结合现有字段确定。

## 治理表结构倾向

`model_governance_config` 使用拆列绑定：

```text
binding_mode   CONFIG / ROUTE
config_id      CONFIG 模式使用
route_key      ROUTE 模式使用
```

约束规则：

- `CONFIG`：`config_id` 必填，`route_key` 为空。
- `ROUTE`：`route_key` 必填，`config_id` 为空。
- 同一未删除绑定对象只允许存在一条有效治理配置。

## 注册表刷新与一致性

`ModelRegistrySnapshot` 保存完整模型运行时配置：

- `model_config`
- `model_route`
- `model_route_config`
- `model_governance_config`
- `registry_version`

以下变更必须 bump version 并发布刷新事件：

- 模型配置新增、修改、启停、删除。
- 路由新增、修改、启停、删除。
- 路由候选关系新增、修改、启停、删除。
- 治理配置新增、修改、启停、删除、恢复默认。

刷新事件只传版本和事件元信息，不传密钥、baseUrl、Prompt 等敏感配置。

刷新通道：

- `LOCAL`：单机默认，发布刷新事件时直接触发本 JVM 的 `ModelRegistryRefresher.refreshIfNewer(version)`。
- `REDIS_PUB_SUB`：多实例轻量广播，当前阶段必须用真实 Redis 测试多实例通知和刷新动作。
- `INFRA_MQ`：预留，等 infra messaging 接 RabbitMQ、Kafka、Redis Stream 后实现。

刷新失败策略：

- 当前阶段记录中文错误日志并保留手动刷新接口。
- 自动重试、告警、多实例刷新状态观测加入 TODO。

## 客户端缓存

DB 配置来源：

```text
clientCacheKey = configId + ":" + registryVersion
```

本地 YAML fallback：

```text
clientCacheKey = "local:" + profileName + ":" + profileHash
```

`ModelRouteDecision` 需要携带：

- `configId`
- `registryVersion`
- `profileName`
- `profile`
- `routeConfigId`
- `priority`
- `weight`

使用全局 `registryVersion` 简化一致性。任意运行时配置变化后，新调用自然使用新 client。

## 调用日志与观测

`model_call_log` 表示单次模型 attempt，状态收敛为：

```text
RUNNING
SUCCESS
FAILED
TIMEOUT
CANCELED
```

不在明细日志使用 `FALLBACK_SUCCESS`。fallback 通过以下字段表达：

- `attempt_no`
- `fallback_from_call_id`
- `fallback_reason`

同步日志记录：

- `route_key`
- provider
- modelName
- requestType
- bizType、bizId、traceId
- promptTokens、completionTokens、totalTokens
- tokenUsageSource
- durationMs
- errorCode、errorMessage
- fallback 信息

流式日志记录：

- firstTokenLatencyMs
- chunkCount
- outputCharCount
- estimatedOutputTokens
- tokenUsageSource
- durationMs
- 终态

默认不记录完整用户输入，不记录完整模型输出。未来如需采样，必须脱敏且默认关闭。

`model_call_trace` 聚合表当前不实现，只加入 TODO。未来用于表达一次业务模型调用整体结果，例如 `FALLBACK_SUCCESS`、attempt_count、final_call_id 和总耗时。

## Token 用量统计

命名使用 `TokenUsageStatistics`，表示 Token 用量统计能力。

组件：

- `TokenUsageStatistics`
- `TokenUsageStatisticsDispatcher`
- `DashScopeTokenUsageStatistics`
- `DefaultUnknownTokenUsageStatistics`

优先级：

1. 厂商返回 usage。
2. 厂商官方规则计算。
3. 本地 tokenizer 或近似算法。
4. 无法统计时返回 `UNKNOWN`。

当前阶段只实现 DashScope Token 用量统计和默认未知统计。OpenAI、DeepSeek、智谱、火山、百度、腾讯等厂商统计适配加入 TODO。

`model_call_log` 现在就增加 `token_usage_source`，避免后续迁移统计口径。

## Provider 可插拔边界

采用双轨制：

- 官方支持厂商使用 `ModelProvider` 枚举，有 catalog、默认 endpoint、推荐模型、专属 adapter、专属 Token 用量统计。
- 自定义 OpenAI-compatible 使用 `CUSTOM_OPENAI`，用户填写 baseUrl、endpointPath、apiKey、modelName，复用 OpenAI-compatible Chat/Embedding adapter。

不使用完全任意字符串 provider，以保持核心类型安全和管理端可控。

## 管理接口范围

当前设计纳入以下接口：

- `model_config`
  - 创建、修改、详情、分页、启停、逻辑删除。
  - 连接测试。
  - 创建时自动生成 CONFIG 默认治理配置。
  - 修改影响运行时后 bump version 并发布刷新事件。

- `model_route`
  - 创建、修改、详情、分页、启停、逻辑删除。
  - 创建时自动生成 ROUTE 默认治理配置。
  - 修改影响运行时后 bump version 并发布刷新事件。

- `model_route_config`
  - 挂载模型配置到路由。
  - 移除关联。
  - 调整角色、优先级、权重、启停。
  - 修改后 bump version 并发布刷新事件。

- `model_governance_config`
  - 创建、修改、详情、分页、启停、逻辑删除。
  - 恢复默认治理策略。
  - 修改后 bump version 并发布刷新事件。

- `model_registry`
  - 查询当前版本。
  - 手动刷新。
  - 查看当前快照摘要。
  - 发布刷新消息。

- `provider_catalog`
  - 查询官方支持厂商。
  - 查询模型类型支持。
  - 查询默认 endpoint、推荐模型、默认治理策略说明。

动作类接口使用子资源：

```text
PATCH /api/model/configs/{configId}/enabled
POST  /api/model/configs/{configId}/connection-tests
POST  /api/model/routes/{routeId}/connection-tests
POST  /api/model/governance-configs/{governanceId}/reset-default
POST  /api/model/registry/refresh
```

## 删除策略

统一采用逻辑删除 + 引用保护。

- 删除 `model_config` 前检查是否仍被 `model_route_config` 引用，有引用则禁止删除。
- 删除 `model_route` 前检查是否仍有 `model_route_config`，有引用则禁止删除。
- 删除 `model_route_config` 只删除关联，不删除模型配置。
- 删除 `model_governance_config` 允许逻辑删除，但提示风险。
- 所有删除都更新 `del_flag` 和 `delete_time`。
- 所有影响运行时的删除都 bump version 并发布刷新事件。

## SQL 维护要求

实现完成后同时维护：

- Flyway 增量迁移 SQL。
- 完整初始化 SQL：`nexa-rag-boot/src/main/resources/db/schema`。

完整 SQL 必须体现本阶段模型表结构最终状态，包括治理绑定字段、日志状态、Token 来源、流式观测字段等。

## 实施分批

### 第一批：运行时稳定性

目标是让调用链路可信。

- `ModelGovernanceResolver` 接入注册表快照里的 DB 治理配置。
- 支持 `binding-mode: CONFIG / ROUTE`，`ROUTE` 使用 `route_key`。
- 创建 `model_config` 自动生成 CONFIG 默认治理配置。
- 创建 `model_route` 自动生成 ROUTE 默认治理配置。
- 治理配置变更 bump registry version 并发布刷新事件。
- `ModelRouteDecision` 携带 `registryVersion`。
- Client cache key 改成 `configId + registryVersion`，本地 fallback 用 profile hash。
- 同步调用接 TimeLimiter。
- 流式调用支持首 chunk 前 fallback、first chunk timeout、max duration。
- `ModelCallStatus` 增加 `TIMEOUT/CANCELED`，明细日志不用 `FALLBACK_SUCCESS`。
- LOCAL 刷新测试。
- REDIS_PUB_SUB 多实例刷新测试。

### 第二批：管理接口与观测

目标是让配置和观测闭环。

- 补齐 `model_config`、`model_route`、`model_route_config`、`model_governance_config` REST CRUD。
- 启停、逻辑删除、引用保护。
- 恢复默认治理策略接口。
- 连接测试、手动刷新、快照摘要接口。
- `provider_catalog` 完整接口。
- 流式日志指标：首 token 耗时、chunk 数、输出字符数、估算 token。
- `token_usage_source` 字段。
- DashScope `TokenUsageStatistics`。
- 同步维护 Flyway migration 和 `db/schema` 完整 SQL。

### 第三批：扩展体验与预留

目标是为后续接业务模块铺路。

- Nacos Prompt 覆盖本地 Prompt。
- Prompt 来源和版本观测。
- 管理页新手引导提示：
  - 自动创建默认治理配置。
  - 可在配置文件切换 `CONFIG/ROUTE`。
  - 可在模型治理页调整策略。
- 后续预留：
  - `model_call_trace` 聚合表。
  - 刷新失败重试和告警。
  - INFRA_MQ 真实接入测试。
  - OpenAI、DeepSeek、智谱、火山、百度、腾讯等 Token 用量统计适配器。

## 测试策略

第一批必须覆盖：

- `CONFIG/ROUTE` 两种治理绑定模式。
- 默认治理配置自动创建，已存在时不覆盖。
- 恢复默认策略显式覆盖。
- 治理配置变更后注册表刷新。
- LOCAL 刷新动作。
- REDIS_PUB_SUB 多实例刷新动作。
- 客户端缓存 key 随 registryVersion 变化。
- 同步 TimeLimiter 超时状态。
- 流式首 chunk 前 fallback。
- 流式输出后不再 fallback。
- 流式取消、超时、异常状态记录。

第二批必须覆盖：

- CRUD、启停、逻辑删除、引用保护。
- provider catalog 返回值。
- DashScope Token 用量统计。
- `token_usage_source` 写入。
- 完整 SQL 与 migration 字段一致性。
