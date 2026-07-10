# NexaRAG Phase 2.5-06 文档入库 Workflow Graph 设计

## 1. 背景

Phase 2.5 已完成上传与 MinIO、Redis 本地队列、解析适配器、文档切分器、检索索引接口等基础能力。当前需要把这些能力通过 Spring AI Alibaba Graph 串成真实文档入库工作流，让 Worker 从 Redis 获取一次“整条文档流水线任务”后，自动完成解析、切分和索引。

旧实施计划对类设计、Graph 启动方式、节点职责、失败重试和测试验收描述不够具体。本设计用于重新对齐 Phase 2.5-06 的实现边界，避免 workflow 模块下沉业务能力。

## 2. 目标

- 使用 Spring AI Alibaba Graph 编排文档入库流程。
- Redis 初版仍按整条文档流水线只排一次。
- Workflow 只组合能力，不直接实现解析、切分、索引细节。
- 删除旧的 `LocalDocumentPipelineExecutor`，避免一个类同时承担解析和切分。
- Worker 直接调用通用 `WorkflowService` 启动图。
- 支持根据文档稳定状态恢复执行，避免 Worker 崩溃后重复走已完成阶段。
- 保留未来检索 Workflow 复用的通用服务结构。
- 使用真实文档完成集成验收：`D:\下载大模型LoRA微调_学习笔记与面试复习材料.docx`。

## 3. 非目标

- 不实现聊天检索 Workflow。
- 不实现阶段级 Redis 队列，阶段级队列仅保留设计扩展点。
- 不引入 Workflow 运行历史表或观测表。
- 不实现 Graph 中断、人工审批、人工恢复点。
- 不在 workflow 模块直接操作 Mapper、MinIO、MinerU、Tika、Milvus、Elasticsearch。
- 不改造上传接口和已有 Redis 队列协议，除非 Worker 调用入口必须调整。

## 4. 总体架构

本批次采用“Redis Worker + 通用 WorkflowService + 业务 Graph Runner + Spring AI Alibaba Graph 节点”的结构。

```text
DocumentController / DocumentService
  -> DocumentProcessTaskDispatcher
  -> Redis pipeline queue
  -> LocalDocumentPipelineWorker
  -> WorkflowService.run("document-ingestion", initialState)
  -> DocumentIngestionWorkflowRunner
  -> documentIngestionGraph
  -> StatusRouterNode
  -> ParsingNode
  -> ChunkingNode
  -> IndexingNode
  -> END
```

文档稳定状态仍以 MySQL `document.status` 为准：

```text
UPLOADED -> QUEUED -> PARSING -> PARSED -> CHUNKING -> CHUNKED -> INDEXING -> INDEXED
```

失败时继续复用已有 `DocumentService.recordProcessFailure(...)` 语义：

- 未达到最大重试次数：文档回到 `QUEUED`，节点抛异常，Worker 释放任务并等待下一轮重试。
- 达到最大重试次数：文档进入 `FAILED`，节点不再抛出重试异常，Graph 正常结束，Worker ack 当前任务。

## 5. 模块边界

### 5.1 nexa-rag-workflow

职责：

- 定义通用 `WorkflowService`。
- 定义图运行策略接口 `WorkflowGraphRunner`。
- 实现 `DocumentIngestionWorkflowRunner`。
- 注册 `documentIngestionGraph` Bean。
- 实现文档入库相关 Node 和 Dispatcher。
- 通过 `DocumentService`、`DocumentParseService`、`DocumentChunkingService`、`DocumentIndexService` 组合能力。

禁止：

- 直接读写 Mapper。
- 直接调用 MinIO SDK。
- 直接调用 MinerU/Tika 客户端。
- 直接调用 Milvus/Elasticsearch 客户端。
- 在 Graph State 中传递完整文档内容。

### 5.2 nexa-rag-document

职责：

- 维护文档状态、失败信息、重试次数和处理配置快照。
- 提供 `DocumentChunkingService.chunk(Long documentId)`。
- 提供 Worker 可复用的文档状态服务能力。

调整：

- 删除 `DocumentPipelineExecutor`。
- 删除 `LocalDocumentPipelineExecutor`。
- 不再由 document 模块拥有“整条入库流水线执行器”。

### 5.3 nexa-rag-infra

职责：

- 继续提供 `DocumentParseService.parse(DocumentParseRequest)`。
- 解析能力仍由 infra 负责选择 MinerU、Tika 或其他解析器。

说明：

- `ParsingNode` 可以调用 `DocumentParseService`，但不能绕过该接口直接调用具体中间件。
- 因此 `nexa-rag-workflow` 需要依赖 `nexa-rag-infra`。

### 5.4 nexa-rag-retrieval

职责：

- 继续提供 `DocumentIndexService.indexDocument(Long documentId)`。
- 内部完成 embedding、Milvus 向量索引、Elasticsearch 关键词索引。

### 5.5 nexa-rag-boot

职责：

- Worker 注入 `WorkflowService`。
- Worker 负责 Redis 租约、ack、release。
- Boot 负责最终应用装配和集成测试入口。

## 6. Graph 设计

### 6.1 图名称

文档入库图名称固定为：

```text
document-ingestion
```

常量放在 workflow 模块 `constants` 包下，业务代码使用静态导入。

### 6.2 Graph State

Graph State 只保存轻量字段：

| Key | 说明 |
| --- | --- |
| `documentId` | 文档ID |
| `currentStatus` | 当前文档状态快照 |
| `routeTarget` | 下一个节点名称或 END |
| `currentStage` | 当前执行阶段 |
| `failureStage` | 失败阶段 |
| `failureReason` | 失败原因摘要 |

不允许放入完整文档正文、完整 Markdown、完整 chunk 列表或 embedding 向量。

### 6.3 图结构

```text
START
  -> StatusRouterNode
  -> ParsingNode / ChunkingNode / IndexingNode / END
  -> ChunkingNode / IndexingNode / END
  -> IndexingNode / END
  -> END
```

更明确的边如下：

```text
START -> STATUS_ROUTER_NODE
STATUS_ROUTER_NODE -- DocumentStatusRouterDispatcher --> PARSING_NODE / CHUNKING_NODE / INDEXING_NODE / END
PARSING_NODE -- DocumentNodeDispatcher --> CHUNKING_NODE / END
CHUNKING_NODE -- DocumentNodeDispatcher --> INDEXING_NODE / END
INDEXING_NODE -> END
```

设计原因：

- 不使用固定边 `ParsingNode -> ChunkingNode -> IndexingNode`，因为节点失败且重试耗尽时应直接 END。
- 通过 Router 支持从 `PARSING/CHUNKING/INDEXING` 这类运行中状态恢复。
- 已完成阶段不重复执行，例如 `CHUNKED` 直接进入索引。

## 7. WorkflowService 设计

### 7.1 接口

```java
public interface WorkflowService {

    void run(String graphName, Map<String, Object> initialState);
}
```

说明：

- 返回值为 `void`。
- 调用方不关心 Graph Result。
- 最终结果通过数据库状态和前端状态接口体现。
- Graph 中间失败通过异常向上传递，由 Worker 释放 Redis 任务。

### 7.2 策略接口

```java
public interface WorkflowGraphRunner {

    String graphName();

    void run(Map<String, Object> initialState);
}
```

### 7.3 策略分发实现

`WorkflowServiceImpl` 注入所有 `WorkflowGraphRunner`，按 `graphName` 建立映射。

行为：

- 找到匹配 Runner 时调用对应 Runner。
- 未找到图名称时抛出业务异常。
- 启动时发现重复 `graphName` 应快速失败。

这样后续增加在线检索图时，只需新增一个 Runner，不需要修改 Worker 或 Controller 的通用入口。

## 8. DocumentIngestionWorkflowRunner 设计

职责：

- 注入 `@Qualifier("documentIngestionGraph") StateGraph`。
- 在构造阶段编译为 `CompiledGraph`。
- 校验 `initialState.documentId`。
- 使用 `graphName + ":" + documentId` 作为 threadId。
- 调用 `compiledGraph.stream(initialState, runnableConfig).blockLast()`。
- 不吞异常，让 Worker 决定 ack 或 release。

threadId 示例：

```text
document-ingestion:2026070504001
```

说明：

- 文档入库 threadId 固定采用 `graphName + ":" + documentId`，便于排查。
- 检索图 threadId 后续另行设计。
- 本批次不依赖 Graph Checkpoint 做恢复，恢复依据仍是 MySQL 文档状态。

## 9. Node 与 Dispatcher 设计

### 9.1 NodeBeanUtil

参考 `liang-data-agent` 的实现，在 workflow 模块提供 `NodeBeanUtil`：

- 从 Spring 容器获取 `NodeAction` Bean。
- 包装为 `AsyncNodeAction.node_async(...)`。
- 从 Spring 容器获取 `EdgeAction` Bean。
- 包装为 `AsyncEdgeAction.edge_async(...)`。

### 9.2 DocumentStatusRouterNode

职责：

1. 读取并校验 `documentId`。
2. 调用 `DocumentService.getRequiredDocument(documentId)` 查询文档。
3. 读取当前 `DocumentStatus`。
4. 根据状态写入 `currentStatus` 和 `routeTarget`。
5. 不修改数据库状态。

路由规则：

| 当前状态 | routeTarget |
| --- | --- |
| `QUEUED` | `PARSING_NODE` |
| `PARSING` | `PARSING_NODE` |
| `PARSED` | `CHUNKING_NODE` |
| `CHUNKING` | `CHUNKING_NODE` |
| `CHUNKED` | `INDEXING_NODE` |
| `INDEXING` | `INDEXING_NODE` |
| `INDEXED` | `END` |
| `FAILED` | `END` |
| `UPLOADED` | `END` |

`UPLOADED` 代表尚未入队或状态异常回放，不由 Graph 自动提交入队，避免绕过上传/入队语义。

### 9.3 DocumentStatusRouterDispatcher

职责：

- 从 State 读取 `routeTarget`。
- 返回对应节点名称或 `StateGraph.END`。
- 遇到未知 `routeTarget` 抛异常，暴露内部状态污染问题。

### 9.4 ParsingNode

职责：

1. 读取 `documentId` 并查询文档。
2. 如果文档已处于 `PARSED/CHUNKED/INDEXED` 等后续状态，则按状态短路到后续节点或 END。
3. 校验当前状态为 `QUEUED` 或 `PARSING`。
4. 推进状态为 `PARSING`。
5. 从 `processConfigJson` 读取 `ProcessDocumentRequest.parseConfig`。
6. 组装 `DocumentParseRequest`。
7. 调用 `DocumentParseService.parse(...)`。
8. 写入解析产物字段，例如 `parsedObjectName`、`parsedFileUrl`、`parsedContentType`。
9. 清理失败字段并推进状态为 `PARSED`。
10. 写入 `routeTarget=CHUNKING_NODE`。

失败处理：

- 捕获解析异常后调用 `DocumentService.recordProcessFailure(documentId, "PARSING", "文档解析失败", detail)`。
- 如果返回状态为 `QUEUED`，抛出业务异常，Worker release。
- 如果返回状态为 `FAILED`，写入 `routeTarget=END`，Graph 正常结束。

### 9.5 ChunkingNode

职责：

1. 读取 `documentId`。
2. 调用 `DocumentChunkingService.chunk(documentId)`。
3. 查询文档最终状态。
4. 成功进入 `CHUNKED` 时写入 `routeTarget=INDEXING_NODE`。
5. 失败耗尽进入 `FAILED` 时写入 `routeTarget=END`。

说明：

- 切分细节、chunk 删除、chunk 落库、状态推进仍由 `DocumentChunkingService` 负责。
- 如果 `DocumentChunkingService` 因自动重试抛异常，Node 不吞异常，Worker release。

### 9.6 IndexingNode

职责：

1. 读取 `documentId`。
2. 调用 `DocumentIndexService.indexDocument(documentId)`。
3. 成功时写入 `routeTarget=END`。
4. 失败时查询文档状态。
5. 若状态为 `QUEUED`，抛异常触发 Worker release。
6. 若状态为 `FAILED`，写入 `routeTarget=END`。

说明：

- embedding、Milvus、Elasticsearch 都归 `DocumentIndexService` 及 retrieval 内部适配器处理。
- Node 不直接生成 embedding，不直接写索引。

### 9.7 DocumentNodeDispatcher

职责：

- 供 `ParsingNode` 和 `ChunkingNode` 后续路由复用。
- 从 State 读取 `routeTarget`。
- 支持返回 `CHUNKING_NODE`、`INDEXING_NODE`、`StateGraph.END`。
- 遇到未知值抛异常。

## 10. Graph Configuration 设计

新增 `DocumentIngestionWorkflowConfiguration`：

- 创建 `KeyStrategyFactory`，State Key 默认使用 `KeyStrategy.REPLACE`。
- 创建 `StateGraph(DOCUMENT_INGESTION_GRAPH_NAME, keyStrategyFactory)`。
- 注册节点：
  - `STATUS_ROUTER_NODE`
  - `PARSING_NODE`
  - `CHUNKING_NODE`
  - `INDEXING_NODE`
- 注册边：
  - `START -> STATUS_ROUTER_NODE`
  - `STATUS_ROUTER_NODE` 条件边到解析、切分、索引或 END。
  - `PARSING_NODE` 条件边到切分或 END。
  - `CHUNKING_NODE` 条件边到索引或 END。
  - `INDEXING_NODE -> END`。
- 启动时输出 PlantUML 调试日志，但日志使用简体中文，不输出文档内容。

## 11. Worker 改造设计

`LocalDocumentPipelineWorker` 不再依赖 `DocumentPipelineExecutor`，改为依赖 `WorkflowService`。

执行逻辑：

1. 从 Redis 获取 pipeline task 和 lease。
2. 构造 `initialState`：`documentId`。
3. 调用 `workflowService.run(DOCUMENT_INGESTION_GRAPH_NAME, initialState)`。
4. 正常返回时 ack Redis 任务。
5. 抛出异常时 release Redis 任务。

Worker 仍负责 Redis 调度语义，Graph 不直接操作 Redis 队列。

## 12. 删除与迁移

删除：

- `DocumentPipelineExecutor`
- `LocalDocumentPipelineExecutor`
- `LocalDocumentPipelineExecutorTest`

改造：

- `LocalDocumentPipelineWorkerTest` 改为 mock `WorkflowService`。
- 如果存在模块依赖测试，需要允许 `nexa-rag-workflow` 依赖 `nexa-rag-infra`，但仍禁止 document/retrieval 反向依赖 workflow。

## 13. 失败恢复与幂等

### 13.1 状态驱动恢复

Graph 每次启动先经过 `DocumentStatusRouterNode`：

- `QUEUED/PARSING` 从解析开始。
- `PARSED/CHUNKING` 从切分开始。
- `CHUNKED/INDEXING` 从索引开始。
- `INDEXED/FAILED/UPLOADED` 直接结束。

这样即使 Worker 在租约期间崩溃，补偿任务或下一次租约恢复也能根据数据库稳定状态继续执行。

### 13.2 节点幂等要求

- Node 必须先读状态再执行。
- Node 不应假设自己一定从前置节点进入。
- Node 遇到后续状态时应短路，不重复执行已完成阶段。
- 真正的数据清理和重处理由已有重处理入口负责，不由 Graph 自动删除历史结果。

### 13.3 异常传播规则

- 可重试失败：抛异常，Worker release。
- 不可重试或重试耗尽：Graph END，Worker ack。
- 未知内部错误：抛异常，优先暴露问题。

## 14. 测试设计

### 14.1 单元测试

新增或改造以下测试：

- `WorkflowServiceImplTest`：验证按 `graphName` 分发到对应 Runner，未知图名称抛异常，重复图名称快速失败。
- `DocumentIngestionWorkflowRunnerTest`：验证 `documentId` 校验、threadId 规则和异常传播。
- `DocumentStatusRouterNodeTest`：覆盖所有文档状态到 routeTarget 的映射。
- `DocumentStatusRouterDispatcherTest`：验证 routeTarget 到节点或 END 的转换。
- `DocumentNodeDispatcherTest`：验证节点后续条件边转换。
- `ParsingNodeTest`：覆盖解析成功、可重试失败、重试耗尽失败、后续状态短路。
- `ChunkingNodeTest`：覆盖切分成功、服务抛异常、失败耗尽 END。
- `IndexingNodeTest`：覆盖索引成功、失败回到 QUEUED、失败进入 FAILED。
- `DocumentIngestionWorkflowConfigurationTest`：验证 Graph 可以编译，关键节点和边存在。
- `LocalDocumentPipelineWorkerTest`：验证 Worker 调用 `WorkflowService.run(...)` 后 ack 或 release。

### 14.2 模块测试

默认测试不访问真实中间件：

```powershell
mvn -pl nexa-rag-workflow -am test
mvn -pl nexa-rag-boot -am "-Dtest=LocalDocumentPipelineWorkerTest" test "-Dsurefire.failIfNoSpecifiedTests=false"
```

如果改动了模块依赖边界，需要运行：

```powershell
mvn -pl nexa-rag-boot -am "-Dtest=ModuleDependencyTest" test "-Dsurefire.failIfNoSpecifiedTests=false"
```

### 14.3 真实链路集成测试

使用真实文件：

```text
D:\下载大模型LoRA微调_学习笔记与面试复习材料.docx
```

前置条件：

- MinerU 已启动。
- MinIO 已启动。
- MySQL、Redis、Milvus、Elasticsearch 使用现有配置。
- Milvus 和 Elasticsearch 地址仍按项目配置读取。

测试路径：

1. 上传该 docx 文件。
2. 文档进入 Redis pipeline 队列。
3. Worker 获取任务并启动 `document-ingestion` Graph。
4. Graph 自动完成解析、切分和索引。
5. Worker ack Redis 任务。

验收点：

- `document.status=INDEXED`。
- `document.parsedFileUrl` 或对应解析产物字段已写入。
- `document_chunk` 存在该文档的切分数据。
- Milvus 中可以按该文档 chunk 查到向量数据。
- Elasticsearch 中可以通过关键词检索到该文档片段。
- Redis waiting/running/lease 中不残留该任务。

## 15. 常量与编码规范

- 常量类放在当前模块 `constants` 包下。
- 可复用业务常量、State Key、Node Name、Graph Name 统一使用常量类。
- 使用常量时采用静态导入，例如：

```java
import static com.nexarag.workflow.constants.DocumentIngestionGraphConstants.DOCUMENT_INGESTION_GRAPH_NAME;
```

- 新增类必须有简体中文 JavaDoc，说明该类作用。
- 方法关键步骤使用编号注释。
- 日志和注释使用简体中文。
- 不修改当前任务无关文件、无关注释和无关中文文案。

## 16. 实施顺序建议

1. 新增 Workflow 常量、State 读取工具、`WorkflowService` 和 `WorkflowGraphRunner` 测试。
2. 实现 `WorkflowServiceImpl` 与 `DocumentIngestionWorkflowRunner`。
3. 新增 Router Node、Dispatcher 和对应测试。
4. 新增 `ParsingNode`，迁移旧 `LocalDocumentPipelineExecutor` 中必要解析编排逻辑。
5. 新增 `ChunkingNode` 和 `IndexingNode`。
6. 新增 `DocumentIngestionWorkflowConfiguration` 并验证 Graph 编译。
7. 改造 `LocalDocumentPipelineWorker` 调用 `WorkflowService`。
8. 删除旧 `DocumentPipelineExecutor` 和 `LocalDocumentPipelineExecutor`。
9. 执行单元测试、模块测试和真实链路集成测试。

## 17. 决策结论

- 采用 Spring AI Alibaba Graph，而不是手写本地 pipeline executor。
- 删除 `LocalDocumentPipelineExecutor`，不再保留一个同时做解析和切分的执行器。
- `ParsingNode` 直接组合 infra 暴露的 `DocumentParseService`，不新增 `DocumentParsingService`。
- `WorkflowService` 保持通用入口，按图名称分发到各 Graph Runner。
- Worker 仍是异步执行入口，Controller 不同步执行重型 Graph。
- Graph State 只传轻量状态，不传文档正文和大对象。
- 失败重试继续复用 MySQL 稳定状态与 Redis release 机制。
- 文档入库 threadId 固定为 `graphName + ":" + documentId`。
