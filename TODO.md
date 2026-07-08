# NexaRAG TODO

## 阶段一已完成

- [x] 接入真实数据库环境后启用 Flyway。
- [x] 为 `delete_time` 逻辑删除自动填充设计统一实现。
- [x] 补充 MySQL、Redis、Elasticsearch、Milvus 等集成冒烟测试。
- [x] 处理 Mockito 在高版本 JDK 下动态 agent 警告。
- [x] 根据生产环境补充日志脱敏和 traceId 全链路验证。

## 阶段二收尾

- [ ] 校验文档领域基础实现是否覆盖阶段二计划。
- [ ] 补齐 FileType 文件类型解析测试。
- [ ] 补齐 DocumentSplitterFactory 切分器工厂测试。
- [ ] 运行文档与基础设施模块测试。
- [ ] 运行架构边界测试。

## Phase 2.5 真实文档入库流水线专项设计

- [ ] 设计真实文件上传和对象存储适配。
- [ ] 设计 MinIO 文件存储适配。
- [ ] 设计 MinerU 解析器，Word/PDF 统一转 Markdown。
- [ ] 设计 Tika 解析器，支持 Excel/PPT。
- [ ] 设计 Markdown、Excel、正则文本等真实切分器。
- [ ] 设计文档重处理前的旧 chunk、向量索引、关键词索引清理。
- [ ] 设计文档删除后的异步资源清理任务。
- [ ] 设计 Redis 队列、限流、排队位置查询和本地执行器。
- [ ] 设计文档入库 Workflow 节点和 Edge 编排。

## Phase 2.5 暂不实现但需预留

- [ ] 多租户权限模型。
- [ ] 文档版本管理。
- [ ] 分布式多实例 Worker 的完整一致性协议。
- [ ] 精细化阶段级队列完整实现。
- [ ] 大文件分片上传。
- [ ] 前端上传页和进度页。
- [ ] 聊天 RAG Workflow。
- [ ] Sa-Token 登录鉴权。

## 阶段三未实现

- [ ] 接入模型注册表刷新消息的真实 MQ 客户端。
- [ ] 实现规则路由。
- [ ] 实现动态权重路由。
- [ ] 实现 OpenAI-compatible 通用 Token 精确统计。
- [ ] 实现不返回 usage 的流式 Chat 厂商 Token 适配或近似估算。
- [ ] 实现模型调用日志归档或聚合统计。
- [ ] 设计 `model_call_trace` 聚合表，用于表达一次业务模型调用的整体结果，包括 fallback 成功、最终状态、attempt_count、final_call_id、总耗时等。
- [ ] 实现模型注册表刷新失败自动重试和告警。
- [ ] 实现多实例模型注册表刷新状态观测。
- [ ] 调研并实现 OpenAI、DeepSeek、智谱、火山、百度、腾讯等厂商的 Token 用量统计适配器。
- [ ] 实现 Nacos 动态配置源。
- [ ] 实现 Nacos Prompt 模板覆盖本地模板。
- [ ] 为各业务模块补充正式 Prompt Markdown 模板。
- [ ] 实现正式 Vue/React 模型管理页面。
- [ ] 接入 document 和 retrieval 业务链路。

## 阶段三已完成

- [x] 新增模型注册表、模型配置、模型路由和路由配置基础表结构。
- [x] 新增模型密钥加密器。
- [x] 新增模型注册表快照和刷新消息抽象。
- [x] 将 `ModelRouter` 切换为数据库注册表快照优先，保留本地配置兜底。
- [x] 新增模型厂商推荐值 REST 接口。
- [x] 统一 `ModelGateway`，提供 Chat、Embedding、Rerank 三类入口。
- [x] 接入 OpenAI-compatible Embedding 模型真实调用初版。
- [x] 接入 DashScope `qwen3-rerank` 重排序模型真实调用初版。
- [x] 新增 Embedding、Rerank 模型连接测试初版。
- [x] 实现 OpenAI-compatible 聊天模型真实调用。
- [x] 实现 Chat 模型连接测试。
- [x] 新增裸 Chat 同步和流式调用 REST 接口。
- [x] 新增模型治理配置表、服务和 REST 接口。
- [x] 接入 Resilience4j 熔断、限流、重试和并发隔离。
- [x] 实现主模型失败后的备用模型 fallback 执行。
- [x] 实现静态权重路由候选排序能力。
- [x] 增强模型调用日志，记录尝试次数和 fallback 来源。
- [x] 新增临时 HTML 模型管理页面。
- [x] 将 `ModelClientFactory` 缓存 Key 从 Profile 维度切换为 `config_id + version`，并在注册表刷新后清理客户端缓存。
- [x] 将 `ModelGovernanceResolver` 接入 `model_governance_config` 表，并支持 CONFIG、ROUTE 两种治理绑定模式。
- [x] 新增默认模型治理配置工厂，创建模型配置或路由时自动写入默认治理配置。
- [x] 补齐模型配置、模型路由、路由配置的 REST 管理接口，并增加治理配置重置默认值和注册表快照接口。
- [x] 扩展模型厂商目录，返回 OpenAI 兼容性、默认 endpoint、推荐模型和默认治理说明。
- [x] 接入模型注册表刷新消息的 LOCAL 和 Redis Pub/Sub 通道，并验证 Redis Pub/Sub 客户端和订阅处理逻辑。
- [x] 接入 Resilience4j TimeLimiter，并补充流式首包超时、最大时长、客户端取消状态处理。
- [x] 新增 `CANCELED`、`TIMEOUT` 模型调用状态。
- [x] 实现 DashScope Token 用量统计，其他厂商适配继续保留在后续 TODO。
- [x] 实现 OpenAI 兼容流式 Chat 的 `stream_options.include_usage` 用量贯通。
- [x] 补齐模型治理运行时表结构、完整 schema SQL 和迁移脚本。

## 后续阶段

- [ ] 阶段四：实现 `nexa-rag-retrieval` 检索地基，包括向量索引、关键词索引、召回结果模型和排序策略。
- [ ] 阶段五：实现真实 infra 适配，包括 storage、parser、messaging、config-center。
- [ ] 阶段六：实现文档入库 Workflow。
- [ ] 阶段七：实现聊天 RAG Workflow 和 WebFlux 流式输出。
- [ ] 阶段八：实现 Sa-Token 登录鉴权。
