# NexaRAG TODO

## 阶段一未实现

- [ ] 接入真实数据库环境后启用 Flyway。
- [ ] 为 `delete_time` 逻辑删除自动填充设计统一实现。
- [ ] 补充 MySQL、Redis、Elasticsearch、Milvus 等 Testcontainers 集成测试。
- [ ] 处理 Mockito 在高版本 JDK 下动态 agent 警告。
- [ ] 根据生产环境补充日志脱敏和 traceId 全链路验证。

## 阶段二未实现

- [ ] 实现真实文件上传和对象存储适配。
- [ ] 实现 MinIO 文件存储适配。
- [ ] 实现 MinerU 解析器，Word/PDF 统一转 Markdown。
- [ ] 实现 Tika 解析器，支持 Excel/PPT。
- [ ] 实现 Markdown、Excel、正则文本等真实切分器。
- [ ] 实现文档重处理前的旧 chunk、向量索引、关键词索引清理。
- [ ] 实现文档删除后的异步资源清理任务。
- [ ] 实现 Redis 队列、限流、排队位置查询和本地执行器。
- [ ] 实现文档入库 Workflow 节点和 Edge 编排。

## 阶段三未实现

- [ ] 实现 OpenAI-compatible 聊天模型真实调用。
- [ ] 实现 OpenAI-compatible Embedding 模型真实调用。
- [ ] 实现 Rerank 模型真实调用。
- [ ] 接入 Resilience4j 熔断、限流、重试、并发隔离和超时控制。
- [ ] 实现主模型失败或熔断后的备用模型 fallback 执行。
- [ ] 实现权重路由。
- [ ] 实现规则路由。
- [ ] 实现 Token 精确统计。
- [ ] 实现模型调用日志归档或聚合统计。
- [ ] 实现 Nacos 动态配置源。
- [ ] 实现 Nacos Prompt 模板覆盖本地模板。
- [ ] 为各业务模块补充正式 Prompt Markdown 模板。

## 后续阶段

- [ ] 阶段四：实现 `nexa-rag-retrieval` 检索地基，包括向量索引、关键词索引、召回结果模型和排序策略。
- [ ] 阶段五：实现真实 infra 适配，包括 storage、parser、messaging、config-center。
- [ ] 阶段六：实现文档入库 Workflow。
- [ ] 阶段七：实现聊天 RAG Workflow 和 WebFlux 流式输出。
- [ ] 阶段八：实现 Sa-Token 登录鉴权。
