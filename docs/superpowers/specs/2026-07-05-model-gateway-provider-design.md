# 模型网关真实 Provider 接入设计

## 背景

NexaRAG 当前已经具备模型治理基础骨架，包括模型调用日志、模型路由雏形、Prompt 模板服务和 Chat、Embedding、Rerank 三类请求/响应契约。下一阶段需要先打通真实模型访问链路，让系统能够通过统一模型网关完成模型配置、刷新、连接测试和真实调用。

本设计聚焦 `nexa-rag-model` 模块自闭环，优先实现 Embedding 与 Rerank 的真实调用。Chat 调用保留统一入口，但不在本轮实现，避免在聊天 Workflow 未定稿前扩大范围。

## 目标

1. 模型配置统一落库，支持本地模型和云端模型作为同一类模型配置参与路由。
2. 提供模型配置、模型路由、路由模型关系的 REST 管理接口。
3. API Key 加密存储，查询接口只返回脱敏值。
4. 提供统一 `ModelGateway` 门面，暴露 `chat`、`embedding`、`rerank` 三个方法。
5. Embedding 真实调用复用 Spring AI OpenAI 相关 API。
6. Rerank 真实调用优先复用 Spring AI Alibaba DashScope Rerank 能力，默认支持 `qwen3-rerank`。
7. 提供模型配置和模型路由连接测试接口，并将测试调用写入模型调用日志。
8. 支持模型注册表刷新消息，刷新通道可配置为 MQ 或 Redis PubSub。

## 非目标

1. 本轮不实现 Chat 真实调用。
2. 本轮不实现 Chat 连接测试。
3. 本轮不实现模型熔断、限流、重试、并发隔离。
4. 本轮不实现权重路由和规则路由。
5. 本轮不实现前端模型管理页面。
6. 本轮不接入 `document` 和 `retrieval` 业务链路，仅完成 `nexa-rag-model` 自闭环。

## 模块边界

`nexa-rag-model` 负责模型访问相关能力：

- 模型配置管理 REST。
- 模型配置 DB 持久化。
- API Key 加密和脱敏。
- 模型注册表内存快照。
- 模型配置刷新消息发布与监听。
- 动态客户端池。
- Embedding 真实调用。
- DashScope Rerank 真实调用。
- 模型连接测试。
- 模型调用日志记录。

业务模块后续只依赖 `ModelGateway`，不直接读取模型配置表，不直接持有 API Key，也不直接创建 Spring AI 客户端。

## 统一模型网关

对外只暴露一个模型网关门面类：

```java
public class ModelGateway {

    public ChatModelResponse chat(ChatModelRequest request) {
        // 本轮保留入口，暂不实现真实调用。
    }

    public EmbeddingModelResponse embedding(EmbeddingModelRequest request) {
        // 本轮实现真实调用。
    }

    public RerankModelResponse rerank(RerankModelRequest request) {
        // 本轮实现真实调用。
    }
}
```

`ModelGateway` 是业务模块使用的统一入口，不设计为多实现接口。真正需要扩展的是内部 Provider Adapter，而不是 Gateway 本身。

内部调用链：

```text
ModelGateway
  -> ModelExecutionTemplate
  -> ModelRouter
  -> ModelProviderDispatcher
  -> Provider Adapter
  -> ModelClientFactory
  -> Spring AI / Spring AI Alibaba
```

原有 `ChatModelGateway`、`EmbeddingModelGateway`、`RerankModelGateway` 不再作为业务侧入口。实现时应统一替换为 `ModelGateway`，避免新项目刚起步就保留重复抽象。

## 数据库设计

### model_config

`model_config` 表示一个具体可调用模型配置。

核心字段：

| 字段 | 说明 |
| --- | --- |
| config_id | 模型配置 ID |
| config_key | 模型配置唯一 key |
| model_type | 模型类型：`CHAT`、`EMBEDDING`、`RERANK` |
| provider | 模型厂商 |
| base_url | 模型服务地址 |
| api_key_cipher | 加密后的 API Key，可为空 |
| api_key_mask | API Key 脱敏展示值 |
| model_name | 实际模型名 |
| enabled | 是否启用 |
| timeout_ms | 超时时间 |
| max_retries | 最大重试次数，治理能力后续接入 |
| version | 单条模型配置版本 |
| extra_config | Provider 特有扩展配置 JSON |
| remark | 备注 |
| create_time | 创建时间 |
| update_time | 更新时间 |
| del_flag | 逻辑删除标记 |
| delete_time | 删除时间 |

本轮不设计 `deploy_type`。本地或云端由 `provider`、`base_url`、`api_key_cipher` 自然表达。

Provider 初版枚举：

```text
OPENAI
OLLAMA
DASHSCOPE
DEEPSEEK
SILICONFLOW
ZHIPU
MOONSHOT
CUSTOM_OPENAI
```

多数 provider 底层复用 OpenAI-compatible 调用实现，但用户配置时按厂商选择，避免 `OPENAI_COMPATIBLE` 过于笼统。

### model_route

`model_route` 表示业务模型路由。

示例：

```text
embedding.document
embedding.query
rerank.retrieval
chat.default
```

核心字段：

| 字段 | 说明 |
| --- | --- |
| route_id | 路由 ID |
| route_key | 业务使用的路由 key |
| model_type | 路由对应模型类型 |
| strategy | 路由策略，初版为 `PRIMARY_BACKUP` |
| enabled | 是否启用 |
| remark | 备注 |
| create_time | 创建时间 |
| update_time | 更新时间 |
| del_flag | 逻辑删除标记 |
| delete_time | 删除时间 |

### model_route_config

`model_route_config` 表示某个路由可选择哪些模型配置，以及主备、优先级、权重关系。

核心字段：

| 字段 | 说明 |
| --- | --- |
| route_config_id | 路由配置关系 ID |
| route_id | 路由 ID |
| config_id | 模型配置 ID |
| role | 角色：`PRIMARY`、`BACKUP`、`CANDIDATE` |
| priority | 优先级 |
| weight | 权重，后续权重路由使用 |
| enabled | 是否启用 |
| create_time | 创建时间 |
| update_time | 更新时间 |
| del_flag | 逻辑删除标记 |
| delete_time | 删除时间 |

### model_registry_version

`model_registry_version` 表示全局模型注册表版本。

核心字段：

| 字段 | 说明 |
| --- | --- |
| version_id | 版本记录 ID |
| version_no | 全局版本号 |
| update_time | 更新时间 |

只要 `model_config`、`model_route`、`model_route_config` 任意一张表发生管理侧变更，就递增 `version_no`。

## 注册表快照与刷新

模型调用不直接查询 DB，而是读取内存快照：

```text
ModelRegistry
  -> AtomicReference<ModelRegistrySnapshot>
```

`ModelRegistrySnapshot` 是不可变快照，包含：

- 当前全局版本号。
- 启用的模型配置集合。
- 启用的模型路由集合。
- 路由到配置的关系集合。

刷新流程：

```text
REST 管理接口保存配置
  -> 事务内更新模型配置相关表
  -> 递增 model_registry_version.version_no
  -> 事务提交后发布刷新消息
  -> 各实例收到刷新消息
  -> 比较 remoteVersion 与 localVersion
  -> remoteVersion 更新时重新加载 DB
  -> 构建新的 ModelRegistrySnapshot
  -> 原子替换当前快照
  -> 清空 ModelClientFactory 客户端缓存
```

刷新通道配置：

```yaml
nexa:
  model:
    registry:
      refresh-channel: MQ
      refresh-topic: nexa.model.registry.changed
```

`refresh-channel` 支持：

- `MQ`：走 infra 统一消息适配，后续支持 RocketMQ、RabbitMQ、Kafka、Redis Stream。
- `PUB_SUB`：走 Redis PubSub。

本设计不使用 Spring Event。Spring Event 只适合单 JVM 内部通知，不适合作为本项目的模型配置刷新主通道。

## Provider 适配

Provider 枚举按厂商表达，Provider Adapter 按实际协议复用实现。

### OpenAI-compatible 家族

适用 provider：

```text
OPENAI
OLLAMA
DEEPSEEK
SILICONFLOW
ZHIPU
MOONSHOT
CUSTOM_OPENAI
```

本轮实现 Embedding，优先复用 Spring AI OpenAI 相关 API 动态创建客户端。`base_url`、`api_key`、`model_name` 来自 DB 中的 `model_config`。

Ollama 和部分本地服务允许 API Key 为空。内部构造客户端时可使用占位值满足框架参数要求，但日志和响应不得暴露占位密钥。

Chat 入口保留，不在本轮实现真实调用。

### DashScope Rerank

适用 provider：

```text
DASHSCOPE
```

本轮实现 Rerank，默认推荐模型为 `qwen3-rerank`，但实际使用 `model_config.model_name`。

优先复用 Spring AI Alibaba：

```text
DashScopeRerankModel
DashScopeRerankOptions
RerankModel
RerankRequest
RerankResponse
```

如果框架 API 无法满足动态 DB 配置，则仅在 DashScope Provider 内维护 HTTP 调用逻辑，外部 `ModelGateway` 和请求响应契约保持不变。

## 动态客户端池

使用 `ModelClientFactory` 管理动态客户端。

缓存 key：

```text
configId + ":" + configVersion
```

行为：

1. 请求进入 Provider Adapter。
2. Provider Adapter 按模型配置向 `ModelClientFactory` 获取客户端。
3. 客户端不存在时创建并缓存。
4. 模型注册表刷新成功后清空客户端缓存。

初版使用简单 `ConcurrentHashMap` 缓存即可。暂不实现精确淘汰、空闲过期、连接池高级参数和旧请求优雅关闭，这些进入 TODO。

## API Key 安全

API Key 使用 AES-GCM 加密存储。

配置：

```yaml
nexa:
  model:
    secret:
      master-key: ${NEXA_MODEL_SECRET_KEY:}
```

规则：

1. `master-key` 未配置时应用启动失败。
2. 创建或修改模型配置时，如果传入新的 API Key，则加密写入 `api_key_cipher`，同步生成 `api_key_mask`。
3. 修改模型配置时，如果未传 API Key，则保留原密钥。
4. 查询列表和详情时只返回 `apiKeyMask`，不返回明文和密文。
5. Provider 调用前由模型模块内部解密。
6. 日志禁止打印明文、密文和敏感请求头。

## REST API

REST 路径统一使用 `/api/model/...`。

### Provider 推荐值

```text
GET /api/model/providers
```

返回厂商、支持模型类型、推荐 `baseUrl`、推荐模型名、是否需要 API Key。

推荐值只用于前端辅助展示，保存模型配置时仍要求用户确认并写入 DB。

### 模型配置

```text
GET    /api/model/configs
GET    /api/model/configs/{configId}
POST   /api/model/configs
PUT    /api/model/configs/{configId}
DELETE /api/model/configs/{configId}
POST   /api/model/configs/{configId}/test
```

### 模型路由

```text
GET    /api/model/routes
GET    /api/model/routes/{routeId}
POST   /api/model/routes
PUT    /api/model/routes/{routeId}
DELETE /api/model/routes/{routeId}
POST   /api/model/routes/{routeId}/configs
PUT    /api/model/routes/{routeId}/configs/{routeConfigId}
DELETE /api/model/routes/{routeId}/configs/{routeConfigId}
POST   /api/model/routes/{routeId}/test
```

### 模型注册表

```text
GET  /api/model/registry
POST /api/model/registry/refresh
```

`POST /api/model/registry/refresh` 用于手动刷新和排障。

## 连接测试

连接测试分两类：

```text
POST /api/model/configs/{configId}/test
POST /api/model/routes/{routeId}/test
```

`configs/{configId}/test` 直接测试指定模型配置，不经过路由选择。`routes/{routeId}/test` 走模型路由，验证路由是否能选中模型并完成调用。

测试规则：

- `EMBEDDING`：发送短文本，要求返回向量维度大于 0。
- `RERANK`：发送 query 和至少 2 条候选文本，要求返回排序分数或排序结果。
- `CHAT`：本轮返回暂不支持。

测试调用写入 `model_call_log`：

```text
biz_type = MODEL_TEST
request_type = EMBEDDING_TEST / RERANK_TEST / CHAT_TEST
```

测试响应包含：

```text
success
provider
modelType
modelName
baseUrl
durationMs
vectorDimension
rerankCount
errorCode
errorMessage
```

响应不包含 API Key、密文、敏感 headers 或完整敏感请求体。

## 错误处理

模型调用失败时，Provider Adapter 将框架或 HTTP 异常转换为统一业务异常。

错误信息需要满足：

1. 对接口调用方给出可排障原因。
2. 不泄露 API Key、密文和敏感 headers。
3. 写入 `model_call_log` 的失败原因应包含错误码、异常类型、耗时和简要错误信息。

本轮不实现自动 fallback。主备切换和熔断降级归入后续模型治理专项。

## 测试策略

本轮至少覆盖：

1. 模型配置 CRUD 与 API Key 加密、脱敏。
2. 模型路由 CRUD 与 route-config 关系校验。
3. 模型注册表加载和快照替换。
4. MQ/PUB_SUB 刷新消息发布与监听的契约测试。
5. `ModelGateway.embedding` 走完整执行链。
6. `ModelGateway.rerank` 走完整执行链。
7. Provider Dispatcher 按 provider 和 modelType 选择正确适配器。
8. 连接测试写入 `model_call_log` 且使用 `MODEL_TEST` 标记。
9. Chat 暂不支持时返回明确错误。
10. 架构边界测试，确保业务模块只依赖 `ModelGateway`。

真实外部模型调用测试应做成可选集成测试，避免普通单元测试依赖外部网络和真实 API Key。

## TODO 更新项

本设计落地时需要同步更新 `TODO.md`，记录以下暂不实现内容：

- Chat 真实调用。
- Chat 连接测试。
- 客户端池按 config 精确淘汰。
- 客户端空闲过期清理。
- HTTP 连接池参数按模型配置定制。
- 客户端级指标采集。
- 配置刷新时优雅等待旧客户端请求结束。
- API Key 轮换期间双密钥兼容。
- 模型熔断、限流、重试、并发隔离。
- 权重路由、规则路由。
- 调用成本统计与日志归档。
- 前端模型管理页面。
- Provider 推荐值动态配置化。

## 后续接入顺序

1. 完成 `nexa-rag-model` 自闭环，确保 Embedding 与 Rerank 能配置、刷新、测试和真实调用。
2. 接入 `document`：文档入库 Workflow 到切分后调用 `ModelGateway.embedding(...)`。
3. 接入 `retrieval`：查询向量化调用 `ModelGateway.embedding(...)`，候选重排调用 `ModelGateway.rerank(...)`。
4. 阶段七聊天 RAG Workflow 定稿后，再实现并接入 `ModelGateway.chat(...)`。
