# Phase 2.5-06 文档入库 Workflow Graph Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现文档入库 Workflow Graph，让 Redis Worker 获取一次文档流水线任务后，通过 Spring AI Alibaba Graph 自动完成解析、切分和索引。

**Architecture:** 本批次采用 `WorkflowService` 策略分发 + `DocumentIngestionWorkflowRunner` + `documentIngestionGraph` 的结构。Graph 节点只组合 `infra/document/retrieval` 已提供的能力，不直接操作 Mapper、MinIO、MinerU、Milvus 或 Elasticsearch；恢复和重试仍以 MySQL 文档稳定状态与 Redis release/ack 为准。

**Tech Stack:** Java 21、Spring Boot 3.5.x、Spring AI Alibaba Graph、Maven 多模块、JUnit 5、AssertJ、Mockito。

---

## 0. 环境与约束

- 当前分支：`master`。
- 默认单元测试不连接真实中间件。
- 真实链路验收使用文件：`D:\下载大模型LoRA微调_学习笔记与面试复习材料.docx`。
- MinerU 与 MinIO 已由用户在 `127.0.0.1` 启动。
- MySQL、Redis、Milvus、Elasticsearch 使用项目现有配置。
- 所有新增类必须有简体中文 JavaDoc。
- 方法关键步骤必须使用编号注释。
- 日志、注释和文档使用简体中文。
- 常量类放在当前模块 `constants` 包下，引用常量时使用静态导入。
- 只修改本计划列出的文件；不要顺手重构无关模块。
- 提交时使用 `git-commit-workflow` skill。

## 1. 文件结构

新增文件：

```text
nexa-rag-workflow/src/main/java/com/nexarag/workflow/constants/DocumentIngestionGraphConstants.java
nexa-rag-workflow/src/main/java/com/nexarag/workflow/constants/DocumentIngestionNodeConstants.java
nexa-rag-workflow/src/main/java/com/nexarag/workflow/constants/DocumentIngestionStateKeys.java
nexa-rag-workflow/src/main/java/com/nexarag/workflow/service/WorkflowService.java
nexa-rag-workflow/src/main/java/com/nexarag/workflow/service/WorkflowGraphRunner.java
nexa-rag-workflow/src/main/java/com/nexarag/workflow/service/impl/WorkflowServiceImpl.java
nexa-rag-workflow/src/main/java/com/nexarag/workflow/service/impl/DocumentIngestionWorkflowRunner.java
nexa-rag-workflow/src/main/java/com/nexarag/workflow/util/NodeBeanUtil.java
nexa-rag-workflow/src/main/java/com/nexarag/workflow/util/DocumentIngestionStateUtil.java
nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/document/DocumentStatusRouterNode.java
nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/document/ParsingNode.java
nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/document/ChunkingNode.java
nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/document/IndexingNode.java
nexa-rag-workflow/src/main/java/com/nexarag/workflow/dispatcher/document/DocumentStatusRouterDispatcher.java
nexa-rag-workflow/src/main/java/com/nexarag/workflow/dispatcher/document/DocumentNodeDispatcher.java
nexa-rag-workflow/src/main/java/com/nexarag/workflow/config/DocumentIngestionWorkflowConfiguration.java
```

新增测试：

```text
nexa-rag-workflow/src/test/java/com/nexarag/workflow/service/impl/WorkflowServiceImplTest.java
nexa-rag-workflow/src/test/java/com/nexarag/workflow/service/impl/DocumentIngestionWorkflowRunnerTest.java
nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/document/DocumentStatusRouterNodeTest.java
nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/document/ParsingNodeTest.java
nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/document/ChunkingNodeTest.java
nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/document/IndexingNodeTest.java
nexa-rag-workflow/src/test/java/com/nexarag/workflow/dispatcher/document/DocumentStatusRouterDispatcherTest.java
nexa-rag-workflow/src/test/java/com/nexarag/workflow/dispatcher/document/DocumentNodeDispatcherTest.java
nexa-rag-workflow/src/test/java/com/nexarag/workflow/config/DocumentIngestionWorkflowConfigurationTest.java
```

修改文件：

```text
nexa-rag-workflow/pom.xml
nexa-rag-boot/pom.xml
nexa-rag-boot/src/main/java/com/nexarag/boot/worker/LocalDocumentPipelineWorker.java
nexa-rag-boot/src/test/java/com/nexarag/boot/worker/LocalDocumentPipelineWorkerTest.java
nexa-rag-boot/src/test/java/com/nexarag/architecture/ModuleDependencyTest.java
```

删除文件：

```text
nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentPipelineExecutor.java
nexa-rag-document/src/main/java/com/nexarag/document/service/impl/LocalDocumentPipelineExecutor.java
nexa-rag-document/src/test/java/com/nexarag/document/service/impl/LocalDocumentPipelineExecutorTest.java
```

## 2. Task 1：建立 Workflow 通用入口与 Runner 策略

**Files:**

- Modify: `nexa-rag-workflow/pom.xml`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/constants/DocumentIngestionGraphConstants.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/constants/DocumentIngestionStateKeys.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/service/WorkflowService.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/service/WorkflowGraphRunner.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/service/impl/WorkflowServiceImpl.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/service/impl/DocumentIngestionWorkflowRunner.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/service/impl/WorkflowServiceImplTest.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/service/impl/DocumentIngestionWorkflowRunnerTest.java`

- [ ] **Step 1: 写 WorkflowServiceImpl 红测**

测试覆盖三件事：

```java
@Test
void runShouldDispatchToMatchedRunner() {
    RecordingRunner runner = new RecordingRunner("document-ingestion");
    WorkflowService service = new WorkflowServiceImpl(List.of(runner));

    Map<String, Object> initialState = Map.of("documentId", 1001L);
    service.run("document-ingestion", initialState);

    assertThat(runner.receivedState()).isEqualTo(initialState);
}

@Test
void runShouldRejectUnknownGraphName() {
    WorkflowService service = new WorkflowServiceImpl(List.of(new RecordingRunner("document-ingestion")));

    assertThatThrownBy(() -> service.run("unknown-graph", Map.of("documentId", 1001L)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("未找到工作流图");
}

@Test
void constructorShouldRejectDuplicatedGraphName() {
    assertThatThrownBy(() -> new WorkflowServiceImpl(List.of(
            new RecordingRunner("document-ingestion"),
            new RecordingRunner("document-ingestion")
    )))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("工作流图名称重复");
}
```

`RecordingRunner` 在测试类内实现 `WorkflowGraphRunner`，只记录最后一次收到的 state。

- [ ] **Step 2: 写 DocumentIngestionWorkflowRunner 红测**

测试覆盖缺失 `documentId` 和最小图可运行：

```java
@Test
void runShouldRejectMissingDocumentId() throws Exception {
    DocumentIngestionWorkflowRunner runner = new DocumentIngestionWorkflowRunner(buildNoopGraph());

    assertThatThrownBy(() -> runner.run(Map.of()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("documentId");
}

@Test
void runShouldAcceptNumericDocumentId() throws Exception {
    DocumentIngestionWorkflowRunner runner = new DocumentIngestionWorkflowRunner(buildNoopGraph());

    assertThatCode(() -> runner.run(Map.of("documentId", 1001L)))
            .doesNotThrowAnyException();
}
```

最小图构造：

```java
private StateGraph buildNoopGraph() throws GraphStateException {
    return new StateGraph("document-ingestion", () -> Map.of())
            .addNode("noop", AsyncNodeAction.node_async(state -> Map.of()))
            .addEdge(START, "noop")
            .addEdge("noop", END);
}
```

- [ ] **Step 3: 运行红测**

```powershell
mvn -pl nexa-rag-workflow -am "-Dtest=WorkflowServiceImplTest,DocumentIngestionWorkflowRunnerTest" test "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 编译失败或测试失败，原因是新增接口和实现尚未存在。

- [ ] **Step 4: 更新 workflow pom 依赖**

在 `nexa-rag-workflow/pom.xml` 中新增 `nexa-rag-infra` 依赖：

```xml
<dependency>
    <groupId>com.nexarag</groupId>
    <artifactId>nexa-rag-infra</artifactId>
    <version>${project.version}</version>
</dependency>
```

- [ ] **Step 5: 新增常量类**

`DocumentIngestionGraphConstants`：

```java
package com.nexarag.workflow.constants;

/**
 * 文档入库 Graph 常量，统一维护图名称和线程标识规则。
 */
public final class DocumentIngestionGraphConstants {

    public static final String DOCUMENT_INGESTION_GRAPH_NAME = "document-ingestion";
    public static final String THREAD_ID_SEPARATOR = ":";

    private DocumentIngestionGraphConstants() {
    }
}
```

`DocumentIngestionStateKeys`：

```java
package com.nexarag.workflow.constants;

/**
 * 文档入库 Graph State Key 常量，避免节点之间使用散落字符串。
 */
public final class DocumentIngestionStateKeys {

    public static final String DOCUMENT_ID = "documentId";
    public static final String CURRENT_STATUS = "currentStatus";
    public static final String ROUTE_TARGET = "routeTarget";
    public static final String CURRENT_STAGE = "currentStage";
    public static final String FAILURE_STAGE = "failureStage";
    public static final String FAILURE_REASON = "failureReason";

    private DocumentIngestionStateKeys() {
    }
}
```

- [ ] **Step 6: 新增接口和实现**

新增 `WorkflowService`：

```java
public interface WorkflowService {

    void run(String graphName, Map<String, Object> initialState);
}
```

新增 `WorkflowGraphRunner`：

```java
public interface WorkflowGraphRunner {

    String graphName();

    void run(Map<String, Object> initialState);
}
```

实现 `WorkflowServiceImpl`：

- 构造函数接收 `List<WorkflowGraphRunner>`。
- 启动时检查重复 `graphName`，重复则抛 `ServiceException`。
- `run` 找不到图时抛 `ServiceException`。
- 找到图时调用对应 Runner。

实现 `DocumentIngestionWorkflowRunner`：

- `@Service`。
- 构造函数注入 `@Qualifier("documentIngestionGraph") StateGraph`。
- 构造时编译为 `CompiledGraph`。
- `graphName()` 返回 `DOCUMENT_INGESTION_GRAPH_NAME`。
- `run` 读取 `DOCUMENT_ID`，支持 `Long`、`Integer`、纯数字 `String`。
- threadId 为 `DOCUMENT_INGESTION_GRAPH_NAME + THREAD_ID_SEPARATOR + documentId`。
- 调用 `compiledGraph.stream(initialState, RunnableConfig.builder().threadId(threadId).build()).blockLast()`。

- [ ] **Step 7: 验证并提交**

```powershell
mvn -pl nexa-rag-workflow -am "-Dtest=WorkflowServiceImplTest,DocumentIngestionWorkflowRunnerTest" test "-Dsurefire.failIfNoSpecifiedTests=false"
git add nexa-rag-workflow/pom.xml nexa-rag-workflow/src/main/java/com/nexarag/workflow nexa-rag-workflow/src/test/java/com/nexarag/workflow/service
git commit -m "feat(workflow): 新增工作流通用入口"
```

Expected: Maven BUILD SUCCESS，提交成功。

## 3. Task 2：实现状态路由节点与 Dispatcher

**Files:**

- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/constants/DocumentIngestionNodeConstants.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/util/DocumentIngestionStateUtil.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/document/DocumentStatusRouterNode.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/dispatcher/document/DocumentStatusRouterDispatcher.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/dispatcher/document/DocumentNodeDispatcher.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/document/DocumentStatusRouterNodeTest.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/dispatcher/document/DocumentStatusRouterDispatcherTest.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/dispatcher/document/DocumentNodeDispatcherTest.java`

- [ ] **Step 1: 写路由节点红测**

`DocumentStatusRouterNodeTest` 覆盖状态映射：

```java
static Stream<Arguments> statusRoutes() {
    return Stream.of(
            arguments(DocumentStatus.QUEUED, PARSING_NODE),
            arguments(DocumentStatus.PARSING, PARSING_NODE),
            arguments(DocumentStatus.PARSED, CHUNKING_NODE),
            arguments(DocumentStatus.CHUNKING, CHUNKING_NODE),
            arguments(DocumentStatus.CHUNKED, INDEXING_NODE),
            arguments(DocumentStatus.INDEXING, INDEXING_NODE),
            arguments(DocumentStatus.INDEXED, END),
            arguments(DocumentStatus.FAILED, END),
            arguments(DocumentStatus.UPLOADED, END)
    );
}
```

核心断言：

```java
Map<String, Object> result = node.apply(new OverAllState(Map.of(DOCUMENT_ID, 1001L)));

assertThat(result).containsEntry(CURRENT_STATUS, status.name());
assertThat(result).containsEntry(ROUTE_TARGET, expectedTarget);
```

同时覆盖缺失 `documentId` 抛 `ServiceException`。

- [ ] **Step 2: 写 Dispatcher 红测**

`DocumentStatusRouterDispatcherTest` 覆盖 `PARSING_NODE`、`CHUNKING_NODE`、`INDEXING_NODE`、`END` 和未知值。

`DocumentNodeDispatcherTest` 覆盖 `CHUNKING_NODE`、`INDEXING_NODE`、`END` 和未知值。

未知值断言：

```java
assertThatThrownBy(() -> dispatcher.apply(new OverAllState(Map.of(ROUTE_TARGET, "bad-node"))))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("未知文档入库路由");
```

- [ ] **Step 3: 运行红测**

```powershell
mvn -pl nexa-rag-workflow -am "-Dtest=DocumentStatusRouterNodeTest,DocumentStatusRouterDispatcherTest,DocumentNodeDispatcherTest" test "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 编译失败或测试失败，原因是节点和 Dispatcher 未实现。

- [ ] **Step 4: 实现节点常量和 State 工具**

`DocumentIngestionNodeConstants`：

```java
public final class DocumentIngestionNodeConstants {

    public static final String STATUS_ROUTER_NODE = "statusRouterNode";
    public static final String PARSING_NODE = "parsingNode";
    public static final String CHUNKING_NODE = "chunkingNode";
    public static final String INDEXING_NODE = "indexingNode";

    private DocumentIngestionNodeConstants() {
    }
}
```

`DocumentIngestionStateUtil`：

- `requiredLong(OverAllState state, String key)`：读取 Long，支持 Number 和数字字符串。
- `requiredString(OverAllState state, String key)`：读取非空字符串。
- 空值、非法数字统一抛 `ServiceException`，错误消息包含 key。

- [ ] **Step 5: 实现 Router Node 和 Dispatcher**

`DocumentStatusRouterNode`：

- `@Component`。
- `implements NodeAction`。
- 依赖 `DocumentService`。
- 只读取文档状态，不修改数据库。
- 返回 `CURRENT_STATUS` 和 `ROUTE_TARGET`。

`DocumentStatusRouterDispatcher`：

- `implements EdgeAction`。
- 允许 `PARSING_NODE`、`CHUNKING_NODE`、`INDEXING_NODE`、`END`。

`DocumentNodeDispatcher`：

- `implements EdgeAction`。
- 允许 `CHUNKING_NODE`、`INDEXING_NODE`、`END`。
- 不允许从普通节点再路由回 `PARSING_NODE`。

- [ ] **Step 6: 验证并提交**

```powershell
mvn -pl nexa-rag-workflow -am "-Dtest=DocumentStatusRouterNodeTest,DocumentStatusRouterDispatcherTest,DocumentNodeDispatcherTest" test "-Dsurefire.failIfNoSpecifiedTests=false"
git add nexa-rag-workflow/src/main/java/com/nexarag/workflow/constants nexa-rag-workflow/src/main/java/com/nexarag/workflow/util nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/document/DocumentStatusRouterNode.java nexa-rag-workflow/src/main/java/com/nexarag/workflow/dispatcher nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/document/DocumentStatusRouterNodeTest.java nexa-rag-workflow/src/test/java/com/nexarag/workflow/dispatcher
git commit -m "feat(workflow): 实现文档状态路由节点"
```

Expected: Maven BUILD SUCCESS，提交成功。

## 4. Task 3：迁移解析编排到 ParsingNode

**Files:**

- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/document/ParsingNode.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/document/ParsingNodeTest.java`

- [ ] **Step 1: 写 ParsingNode 红测**

覆盖四类行为：

- `QUEUED` 文档解析成功后状态变为 `PARSED`，路由到 `CHUNKING_NODE`。
- 解析失败且 `recordProcessFailure` 返回 `QUEUED` 时抛 `ServiceException`。
- 解析失败且 `recordProcessFailure` 返回 `FAILED` 时返回 `END`。
- 文档已是 `PARSED` 时不调用 `DocumentParseService.parse`，直接路由到 `CHUNKING_NODE`。

成功用例核心断言：

```java
assertThat(document.getStatus()).isEqualTo(DocumentStatus.PARSED);
assertThat(document.getParsedObjectName()).isEqualTo("parsed/1001/demo.md");
assertThat(result).containsEntry(ROUTE_TARGET, CHUNKING_NODE);
verify(parseService).parse(argThat(request -> Boolean.TRUE.equals(request.enableOcr())
        && Boolean.FALSE.equals(request.enableImageDescription())));
```

- [ ] **Step 2: 运行红测**

```powershell
mvn -pl nexa-rag-workflow -am "-Dtest=ParsingNodeTest" test "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 编译失败或测试失败，原因是 `ParsingNode` 未实现。

- [ ] **Step 3: 实现 ParsingNode**

实现要求：

- `@Component`。
- `implements NodeAction`。
- 依赖 `DocumentService`、`DocumentParseService`、`ObjectMapper`。
- 从旧 `LocalDocumentPipelineExecutor` 迁移以下逻辑：`markParsing`、`buildParseRequest`、`readParseConfig`、`markParsed`。
- 失败阶段统一为 `PARSING`，失败原因使用 `文档解析失败`。
- `updateById` 返回 false 时抛 `ServiceException`。
- 可重试失败时抛 `ServiceException`。
- 重试耗尽时返回 `ROUTE_TARGET=END`。
- 成功时返回 `ROUTE_TARGET=CHUNKING_NODE`。

- [ ] **Step 4: 验证并提交**

```powershell
mvn -pl nexa-rag-workflow -am "-Dtest=ParsingNodeTest" test "-Dsurefire.failIfNoSpecifiedTests=false"
git add nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/document/ParsingNode.java nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/document/ParsingNodeTest.java
git commit -m "feat(workflow): 实现文档解析节点"
```

Expected: Maven BUILD SUCCESS，提交成功。

## 5. Task 4：实现 ChunkingNode 与 IndexingNode

**Files:**

- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/document/ChunkingNode.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/document/IndexingNode.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/document/ChunkingNodeTest.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/document/IndexingNodeTest.java`

- [ ] **Step 1: 写 ChunkingNode 红测**

覆盖：

- `DocumentChunkingService.chunk(documentId)` 成功，最终文档状态 `CHUNKED`，路由到 `INDEXING_NODE`。
- 切分失败耗尽，最终文档状态 `FAILED`，路由到 `END`。
- `DocumentChunkingService.chunk(documentId)` 抛异常时异常向外传播。

- [ ] **Step 2: 写 IndexingNode 红测**

覆盖：

- `DocumentIndexService.indexDocument(documentId)` 返回 `success=true`，路由到 `END`。
- 返回 `success=false` 且文档状态 `QUEUED`，抛 `ServiceException`，让 Worker release。
- 返回 `success=false` 且文档状态 `FAILED`，路由到 `END`。

如果 `DocumentIndexResult` 构造参数与计划中的示例不一致，以 `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/dto/DocumentIndexResult.java` 为准。

- [ ] **Step 3: 运行红测**

```powershell
mvn -pl nexa-rag-workflow -am "-Dtest=ChunkingNodeTest,IndexingNodeTest" test "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 编译失败或测试失败，原因是节点未实现。

- [ ] **Step 4: 实现 ChunkingNode**

实现要求：

- `@Component`。
- `implements NodeAction`。
- 依赖 `DocumentService`、`DocumentChunkingService`。
- 调用 `documentChunkingService.chunk(documentId)`。
- 调用后查询 `DocumentService.getRequiredDocument(documentId)`。
- 状态为 `CHUNKED` 时路由到 `INDEXING_NODE`。
- 状态为 `FAILED` 时路由到 `END`。
- 其他状态抛 `ServiceException`。
- 不捕获 `chunk` 抛出的运行时异常。

- [ ] **Step 5: 实现 IndexingNode**

实现要求：

- `@Component`。
- `implements NodeAction`。
- 依赖 `DocumentService`、`DocumentIndexService`。
- 调用 `documentIndexService.indexDocument(documentId)`。
- `result.success()` 为 true 时返回 `END`。
- `result.success()` 为 false 时重新查询文档：
  - `QUEUED`：抛 `ServiceException`，让 Worker release。
  - `FAILED`：返回 `END`。
  - `INDEXED`：返回 `END`。
  - 其他状态：抛 `ServiceException`。

- [ ] **Step 6: 验证并提交**

```powershell
mvn -pl nexa-rag-workflow -am "-Dtest=ChunkingNodeTest,IndexingNodeTest" test "-Dsurefire.failIfNoSpecifiedTests=false"
git add nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/document/ChunkingNode.java nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/document/IndexingNode.java nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/document/ChunkingNodeTest.java nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/document/IndexingNodeTest.java
git commit -m "feat(workflow): 实现切分与索引节点"
```

Expected: Maven BUILD SUCCESS，提交成功。

## 6. Task 5：装配 documentIngestionGraph

**Files:**

- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/util/NodeBeanUtil.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/config/DocumentIngestionWorkflowConfiguration.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/config/DocumentIngestionWorkflowConfigurationTest.java`

- [ ] **Step 1: 写 Graph 配置红测**

测试目标：`documentIngestionGraph` 可以 compile。

```java
@Test
void documentIngestionGraphShouldCompile() throws Exception {
    NodeBeanUtil nodeBeanUtil = mock(NodeBeanUtil.class);
    when(nodeBeanUtil.toAsyncNode(DocumentStatusRouterNode.class)).thenReturn(AsyncNodeAction.node_async(state -> Map.of()));
    when(nodeBeanUtil.toAsyncNode(ParsingNode.class)).thenReturn(AsyncNodeAction.node_async(state -> Map.of()));
    when(nodeBeanUtil.toAsyncNode(ChunkingNode.class)).thenReturn(AsyncNodeAction.node_async(state -> Map.of()));
    when(nodeBeanUtil.toAsyncNode(IndexingNode.class)).thenReturn(AsyncNodeAction.node_async(state -> Map.of()));
    when(nodeBeanUtil.toAsyncEdge(DocumentStatusRouterDispatcher.class)).thenReturn(AsyncEdgeAction.edge_async(state -> END));
    when(nodeBeanUtil.toAsyncEdge(DocumentNodeDispatcher.class)).thenReturn(AsyncEdgeAction.edge_async(state -> END));

    StateGraph graph = new DocumentIngestionWorkflowConfiguration().documentIngestionGraph(nodeBeanUtil);

    assertThatCode(graph::compile).doesNotThrowAnyException();
}
```

- [ ] **Step 2: 运行红测**

```powershell
mvn -pl nexa-rag-workflow -am "-Dtest=DocumentIngestionWorkflowConfigurationTest" test "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 编译失败或测试失败，原因是配置类不存在。

- [ ] **Step 3: 实现 NodeBeanUtil**

参考 `liang-data-agent`：

- 从 Spring 容器获取 `NodeAction` Bean。
- 包装为 `AsyncNodeAction.node_async(...)`。
- 从 Spring 容器获取 `EdgeAction` Bean。
- 包装为 `AsyncEdgeAction.edge_async(...)`。

- [ ] **Step 4: 实现 DocumentIngestionWorkflowConfiguration**

实现要求：

- `@Configuration`。
- `@Bean("documentIngestionGraph")`。
- 使用 `KeyStrategy.REPLACE` 注册 `DOCUMENT_ID`、`CURRENT_STATUS`、`ROUTE_TARGET`、`CURRENT_STAGE`、`FAILURE_STAGE`、`FAILURE_REASON`。
- 添加节点：`STATUS_ROUTER_NODE`、`PARSING_NODE`、`CHUNKING_NODE`、`INDEXING_NODE`。
- 添加边：
  - `START -> STATUS_ROUTER_NODE`
  - `STATUS_ROUTER_NODE` 条件边到 `PARSING_NODE/CHUNKING_NODE/INDEXING_NODE/END`
  - `PARSING_NODE` 条件边到 `CHUNKING_NODE/END`
  - `CHUNKING_NODE` 条件边到 `INDEXING_NODE/END`
  - `INDEXING_NODE -> END`
- 打印 PlantUML 时日志为中文，不输出文档正文。

- [ ] **Step 5: 验证并提交**

```powershell
mvn -pl nexa-rag-workflow -am "-Dtest=DocumentIngestionWorkflowConfigurationTest" test "-Dsurefire.failIfNoSpecifiedTests=false"
mvn -pl nexa-rag-workflow -am test
git add nexa-rag-workflow/src/main/java/com/nexarag/workflow/util/NodeBeanUtil.java nexa-rag-workflow/src/main/java/com/nexarag/workflow/config/DocumentIngestionWorkflowConfiguration.java nexa-rag-workflow/src/test/java/com/nexarag/workflow/config/DocumentIngestionWorkflowConfigurationTest.java
git commit -m "feat(workflow): 装配文档入库Graph"
```

Expected: Maven BUILD SUCCESS，提交成功。

## 7. Task 6：迁移 Worker 并删除旧 PipelineExecutor

**Files:**

- Modify: `nexa-rag-boot/pom.xml`
- Modify: `nexa-rag-boot/src/main/java/com/nexarag/boot/worker/LocalDocumentPipelineWorker.java`
- Modify: `nexa-rag-boot/src/test/java/com/nexarag/boot/worker/LocalDocumentPipelineWorkerTest.java`
- Modify: `nexa-rag-boot/src/test/java/com/nexarag/architecture/ModuleDependencyTest.java`
- Delete: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentPipelineExecutor.java`
- Delete: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/LocalDocumentPipelineExecutor.java`
- Delete: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/LocalDocumentPipelineExecutorTest.java`

- [ ] **Step 1: 改造 Worker 测试红测**

修改 `LocalDocumentPipelineWorkerTest`：

- 移除 `DocumentPipelineExecutor` 测试替身。
- 新增 `RecordingWorkflowService implements WorkflowService`。
- 成功用例断言 graphName 为 `document-ingestion`，state 包含 `documentId`，成功后 queue ack。
- 失败用例断言 Workflow 抛异常后 Worker release，失败任务回到队尾。

关键断言：

```java
assertThat(workflowService.graphNames()).containsExactly("document-ingestion", "document-ingestion");
assertThat(workflowService.documentIds()).containsExactly(10L, 20L);
```

- [ ] **Step 2: 运行 Worker 红测**

```powershell
mvn -pl nexa-rag-boot -am "-Dtest=LocalDocumentPipelineWorkerTest" test "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 编译失败或测试失败，原因是 Worker 仍依赖 `DocumentPipelineExecutor`。

- [ ] **Step 3: 更新 boot pom**

确保 `nexa-rag-boot/pom.xml` 依赖 `nexa-rag-workflow`。如果已有依赖则不重复添加。

- [ ] **Step 4: 改造 LocalDocumentPipelineWorker**

修改要点：

- 构造函数依赖从 `DocumentPipelineExecutor` 改为 `WorkflowService`。
- `runOnce` 中构造：

```java
Map<String, Object> initialState = Map.of(DOCUMENT_ID, task.documentId());
workflowService.run(DOCUMENT_INGESTION_GRAPH_NAME, initialState);
```

- 使用静态导入：

```java
import static com.nexarag.workflow.constants.DocumentIngestionGraphConstants.DOCUMENT_INGESTION_GRAPH_NAME;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.DOCUMENT_ID;
```

- 保留 Redis ack/release 行为不变。

- [ ] **Step 5: 删除旧执行器**

```powershell
git rm nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentPipelineExecutor.java
git rm nexa-rag-document/src/main/java/com/nexarag/document/service/impl/LocalDocumentPipelineExecutor.java
git rm nexa-rag-document/src/test/java/com/nexarag/document/service/impl/LocalDocumentPipelineExecutorTest.java
```

- [ ] **Step 6: 更新架构边界测试**

如果 `ModuleDependencyTest` 限制 `workflow` 不依赖 `infra`，改为允许 `workflow -> infra`。

必须仍然保持：

- `document` 不依赖 `workflow`。
- `retrieval` 不依赖 `workflow`。
- `workflow` 不依赖任何 `mapper` 包。
- `workflow` 不依赖 `document.service.impl`。

- [ ] **Step 7: 验证并提交**

```powershell
mvn -pl nexa-rag-boot -am "-Dtest=LocalDocumentPipelineWorkerTest" test "-Dsurefire.failIfNoSpecifiedTests=false"
mvn -pl nexa-rag-boot -am "-Dtest=ModuleDependencyTest" test "-Dsurefire.failIfNoSpecifiedTests=false"
git add nexa-rag-boot/pom.xml nexa-rag-boot/src/main/java/com/nexarag/boot/worker/LocalDocumentPipelineWorker.java nexa-rag-boot/src/test/java/com/nexarag/boot/worker/LocalDocumentPipelineWorkerTest.java nexa-rag-boot/src/test/java/com/nexarag/architecture/ModuleDependencyTest.java
git commit -m "refactor(workflow): 迁移Worker启动文档入库Graph"
```

Expected: Maven BUILD SUCCESS，提交成功。

## 8. Task 7：最终验证与真实链路验收

**Files:**

- No mandatory source changes.
- If integration test is added, create under `nexa-rag-boot/src/test/java/com/nexarag/integration/` and require explicit system property.

- [ ] **Step 1: 跑 workflow 模块测试**

```powershell
mvn -pl nexa-rag-workflow -am test
```

Expected: BUILD SUCCESS。

- [ ] **Step 2: 跑 document/retrieval/boot 相关测试**

```powershell
mvn -pl nexa-rag-document,nexa-rag-retrieval,nexa-rag-boot -am test
```

Expected: BUILD SUCCESS。

- [ ] **Step 3: 检查空白和无关变化**

```powershell
git diff --check
git status --short
```

Expected: `git diff --check` no output；`git status` 只包含本批次相关文件和 `.superpowers/`。

- [ ] **Step 4: 执行真实链路验收**

前置确认：

```powershell
Test-Path "D:\下载大模型LoRA微调_学习笔记与面试复习材料.docx"
```

Expected: `True`。

启动应用后上传该文件，观察状态流转：

```text
UPLOAD -> QUEUED -> PARSING -> PARSED -> CHUNKING -> CHUNKED -> INDEXING -> INDEXED
```

验收点：

- MySQL `document.status` 最终为 `INDEXED`。
- `document.parsed_file_url` 或 `parsed_object_name` 已写入。
- `document_chunk` 存在该文档片段。
- Milvus 可查到该文档 chunk 的向量数据。
- Elasticsearch 可通过关键词检索到该文档片段。
- Redis waiting/running/lease 不残留该 documentId。

如果需要用 Maven 显式集成测试，命令格式建议为：

```powershell
mvn -pl nexa-rag-boot -am "-Dtest=DocumentIngestionWorkflowIntegrationTest" "-Dnexa.integration.enabled=true" "-Dnexa.integration.file-path=D:\下载大模型LoRA微调_学习笔记与面试复习材料.docx" test "-Dsurefire.failIfNoSpecifiedTests=false"
```

- [ ] **Step 5: 最终提交**

如果 Task 7 只包含验证结果，不需要额外提交。

如果新增了显式集成测试：

```powershell
git add nexa-rag-boot/src/test/java/com/nexarag/integration/DocumentIngestionWorkflowIntegrationTest.java
git commit -m "test(workflow): 增加文档入库Graph集成验收"
```

## 9. 自审清单

- [ ] `WorkflowService` 是通用入口，不命名为 `DocumentIngestionWorkflowService`。
- [ ] Worker 调用 `WorkflowService.run("document-ingestion", initialState)`。
- [ ] 文档入库 threadId 为 `document-ingestion:{documentId}`。
- [ ] `ParsingNode` 调用 `DocumentParseService`，不直接调用 MinerU/Tika/MinIO。
- [ ] `ChunkingNode` 调用 `DocumentChunkingService`，不直接操作 chunk mapper。
- [ ] `IndexingNode` 调用 `DocumentIndexService`，不直接调用 Milvus/Elasticsearch/ModelGateway。
- [ ] `LocalDocumentPipelineExecutor` 与 `DocumentPipelineExecutor` 已删除。
- [ ] Graph State 不包含文档正文、完整 Markdown、chunk 列表和向量。
- [ ] 常量引用使用静态导入。
- [ ] 新增 Java 类均有简体中文 JavaDoc。
- [ ] 方法关键步骤均有编号注释。
- [ ] 默认单元测试不依赖真实中间件。
- [ ] 真实链路验收使用 `D:\下载大模型LoRA微调_学习笔记与面试复习材料.docx`。
