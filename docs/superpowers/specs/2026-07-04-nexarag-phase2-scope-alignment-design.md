# NexaRAG 阶段二范围对齐设计

## 1. 背景

阶段二文档领域计划与项目 `TODO.md` 中的“阶段二未实现”存在范围口径差异。

阶段二计划文档的目标是建立文档领域基础能力，使后续 Workflow 节点可以通过稳定的服务接口完成校验、解析、切分、索引、重试和清理。该计划明确不实现真实文件上传、MinIO、MinerU、Tika、真实切分算法、Workflow Graph、向量/关键词索引写入、Redis 队列和资源调度。

多模块设计文档则进一步要求：

- `parser`、`storage`、`messaging` 归入 `nexa-rag-infra`，作为可替换技术适配能力。
- 文档上传、状态更新、切分、片段保存等业务能力封装在 `nexa-rag-document`。
- 检索、索引、召回、重排序能力封装在 `nexa-rag-retrieval`。
- Graph 编排定义放在 `nexa-rag-workflow`，Workflow 只做状态读取、服务调用、状态写入和流程分派。
- 文档入库 Workflow 的具体 Node 命名和 Edge 编排属于后续详细设计问题。

因此，当前需要先收敛阶段二范围，再为真实文档入库流水线单独设计后续阶段。

## 2. 目标

本设计用于统一阶段二剩余工作的边界，避免把基础领域模型、真实基础设施适配、检索索引和 Workflow 编排混在同一批实现中。

目标如下：

- 阶段二只完成文档领域基础闭环。
- 将真实文档入库能力拆为 Phase 2.5 专项设计。
- 保持多模块依赖方向清晰，不让业务模块反向依赖 `workflow`。
- 保持 `infra` 只提供可替换技术适配能力，不依赖业务模块。
- 保持接口、状态机、DTO/VO、Service 边界稳定，为后续 Graph Node 调用做准备。

## 3. 阶段二收尾范围

阶段二收尾只覆盖已经批准的文档领域基础计划。

### 3.1 包含内容

- `document` 和 `document_chunk` 表结构。
- 文档状态机：`UPLOADED`、`QUEUED`、`PARSING`、`PARSED`、`CHUNKING`、`CHUNKED`、`INDEXING`、`INDEXED`、`FAILED`。
- 文档片段状态：`PENDING_INDEX`、`INDEXED`、`SKIP_INDEX`、`FAILED`。
- 文档错误码。
- 文档和片段实体，使用业务主键。
- Mapper 和 Service 接口，遵循 MyBatis-Plus `IService` 规范。
- Service 实现，继承 `ServiceImpl<Mapper, Entity>`。
- Request DTO、Response VO 和 Converter。
- 文档 Controller 骨架接口：
  - `POST /api/documents`
  - `GET /api/documents`
  - `GET /api/documents/{documentId}`
  - `DELETE /api/documents/{documentId}`
  - `POST /api/documents/{documentId}/process`
  - `POST /api/documents/{documentId}/retry`
  - `GET /api/documents/{documentId}/process-status`
  - `GET /api/documents/{documentId}/chunks`
- `nexa-rag-infra` 中的 parser/storage 抽象接口。
- 切分器抽象、切分策略枚举和工厂，不包含真实切分算法。
- 单元测试和架构边界验证。

### 3.2 不包含内容

- 真实文件上传和对象存储适配。
- MinIO 文件存储实现。
- MinerU 解析器实现。
- Tika 解析器实现。
- Markdown、Excel、正则文本等真实切分器实现。
- 文档重处理前的旧 chunk、向量索引、关键词索引清理实现。
- 文档删除后的异步资源清理任务。
- Redis 队列、限流、排队位置查询和本地执行器。
- Spring AI Alibaba Graph 的文档入库 Node、Edge、Dispatcher 编排。
- 向量索引和关键词索引写入。

## 4. Phase 2.5 拆分建议

Phase 2.5 建议命名为“真实文档入库流水线专项设计”。它承接 `TODO.md` 当前误挂在阶段二下的真实能力，并按多模块设计拆分。

### 4.1 Infra 适配能力

归属模块：`nexa-rag-infra`。

职责：

- 实现 MinIO 文件存储适配。
- 实现 MinerU 解析器适配，负责 Word/PDF 转 Markdown。
- 实现 Tika 解析器适配，负责 Excel/PPT 转标准文本或结构化内容。
- 实现 messaging 抽象与适配，为文档处理任务、资源清理任务提供发布能力。

边界：

- `infra` 不依赖 `document`、`workflow`、`retrieval` 等业务模块。
- 适配器只暴露抽象接口，不把具体中间件 SDK 泄漏给业务模块。

### 4.2 Document 领域能力

归属模块：`nexa-rag-document`。

职责：

- 实现真实切分器：Markdown、Excel、正则文本等。
- 实现文档重处理前旧 chunk 清理。
- 实现文档删除后的资源清理任务发布入口。
- 维护文档状态、处理配置快照、失败原因、重试次数等领域数据。

边界：

- `document` 可以依赖 `infra` 抽象。
- `document` 不依赖 `workflow`。
- `document` 不直接实现向量库和关键词索引清理，索引相关能力由 `retrieval` 封装。

### 4.3 Retrieval 索引能力

归属模块：`nexa-rag-retrieval`。

职责：

- 定义并实现向量索引写入接口。
- 定义并实现关键词索引写入接口。
- 定义并实现索引清理接口。
- 处理索引写入失败，并向上暴露明确失败阶段。

边界：

- `retrieval` 可以依赖 `document` 的 service 接口和 entity。
- `retrieval` 禁止依赖 `document.mapper` 和 `document.service.impl`。
- Elasticsearch、Milvus 等具体技术适配应保持在 `retrieval` 内部或其技术适配子包内，不反向污染 `document`。

### 4.4 Workflow 编排能力

归属模块：`nexa-rag-workflow`。

职责：

- 使用 Spring AI Alibaba Graph 定义文档入库 Workflow。
- 定义独立的文档入库 StateKeys。
- 提供类型安全的状态读写工具。
- 定义 Node、Edge、Dispatcher。
- Node 只负责状态读取、服务调用、状态写入和流程分派。
- 实现 Workflow 观测记录：`workflow_run`、`workflow_node_run`。

边界：

- `workflow` 可以依赖 `document`、`retrieval`、`model`、`infra`、`common`。
- 业务实现不得下沉到 Node 中。
- Node 日志只记录节点进入、退出和关键状态，不输出完整文档内容。

### 4.5 Boot 装配能力

归属模块：`nexa-rag-boot`。

职责：

- 注册配置属性。
- 放置 Flyway 迁移脚本。
- 补充整体架构测试。
- 作为最终运行入口装配各模块 Bean。

## 5. TODO 调整建议

建议把 `TODO.md` 中当前“阶段二未实现”的真实能力移出阶段二，调整为 Phase 2.5 或后续阶段。

建议分类：

- 阶段二收尾：仅保留文档领域基础验证、TODO 状态修正、测试补齐。
- Phase 2.5：真实文件上传、MinIO、MinerU、Tika、真实切分器、重处理清理、删除资源清理任务、Redis/messaging 队列。
- 阶段四：检索地基，包含向量索引、关键词索引、召回结果模型和排序策略。
- 阶段六：文档入库 Workflow，包含 Spring AI Alibaba Graph 的 Node、Edge、Dispatcher 和观测记录。

## 6. 推荐执行路径

推荐按以下顺序执行：

1. 阶段二收尾。
   - 校验当前文档领域基础实现是否覆盖阶段二计划。
   - 补齐缺失的单元测试和架构测试。
   - 修正 `TODO.md` 阶段归属。
   - 运行相关 Maven 验证。

2. Phase 2.5 专项设计。
   - 先写真实文档入库流水线设计文档。
   - 明确 MinIO、MinerU、Tika、messaging、切分器、索引清理、Graph Workflow 的模块边界。
   - 明确接口、状态流转、失败处理、重试策略和验证方案。

3. Phase 2.5 实施计划。
   - 根据专项设计写实施计划。
   - 按 TDD 和模块边界逐步实现。
   - 每个模块完成后运行对应测试和架构验证。

## 7. 验证策略

阶段二收尾验证：

- 运行 `mvn -pl nexa-rag-document,nexa-rag-infra -am test`。
- 运行 `mvn -pl nexa-rag-boot -am test -Dtest=ModuleDependencyTest`。
- 必要时运行 `mvn clean test` 做全量验证。

Phase 2.5 后续验证：

- Infra 适配使用单元测试和可选集成测试。
- Document 切分器使用真实文本样例测试。
- Retrieval 索引接口使用 mock 网关和可选集成测试。
- Workflow 使用 Node 级测试和 Graph 编排测试。
- 架构测试必须确保业务模块不依赖 `workflow`，跨模块不依赖 mapper 和 service.impl。

## 8. 决策结论

阶段二不继续实现真实基础设施、真实切分器、Redis 队列或 Workflow Graph。

当前正确路线是：

1. 先完成阶段二文档领域基础闭环。
2. 再为真实文档入库流水线写 Phase 2.5 专项设计。
3. 专项设计确认后，再进入实施计划和代码开发。