# 安全窗口切分与父上下文扩展实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 避免 Markdown 表格、代码围栏和标题被字符窗口截断，并在子片段命中后按预算提供父级上下文。

**Architecture:** 文档模块在生成超长章节的索引子片段时，使用面向 Markdown 块的窗口器：HTML 表格按完整表格或 `tr` 行分块，代码围栏保持完整，普通文本仍按段落/换行和有限重叠切分。检索模块在 Rerank 后读取已命中子片段的父片段与相邻窗口：小父片段或同父多命中时以父片段替换命中，否则保留命中并追加相邻窗口；随后仍由既有证据预算器裁剪。

**Tech Stack:** Java 21、Spring Boot、MyBatis-Plus、JUnit 5、AssertJ、Mockito。

---

### Task 1: 安全 Markdown 窗口器

**Files:**
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/markdown/MarkdownSafeWindowSplitter.java`
- Create: `nexa-rag-document/src/test/java/com/nexarag/document/splitter/markdown/MarkdownSafeWindowSplitterTest.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/markdown/MarkdownSectionStructureBuilder.java`

- [ ] **Step 1: 编写失败测试**

验证单行 HTML 表格不会在标签中间截断；超过窗口大小时每个片段包含完整 `<table>` 与 `</table>`，并以 `</tr>` 为分界。验证 fenced code block 不被拆开，标题行不被拆开。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl nexa-rag-document -am "-Dtest=MarkdownSafeWindowSplitterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: FAIL，因为安全窗口器尚不存在。

- [ ] **Step 3: 实现最小安全窗口器**

解析 HTML 表格、代码围栏和普通文本块。表格超过窗口时按 `tr` 行重建为独立、闭合的表格片段；不可再分割的代码或单行块允许单块超过窗口上限，禁止破坏语法。普通文本保持现有的自然边界与重叠语义。

- [ ] **Step 4: 接入 Markdown 章节构建器**

超长章节改由安全窗口器产生子片段；父片段仍保存完整 section，子片段仍保留 `parentChunkId` 与现有索引内容规则。

- [ ] **Step 5: 运行定向测试**

Run: `mvn -pl nexa-rag-document -am "-Dtest=MarkdownSafeWindowSplitterTest,MarkdownParentDocumentSplitterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: PASS。

### Task 2: 父片段与相邻窗口读取能力

**Files:**
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentChunkService.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentChunkServiceImpl.java`
- Modify: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentChunkServiceImplTest.java`

- [ ] **Step 1: 编写失败测试**

验证可按多个父片段 ID 一次读取子窗口，并保持 `chunkOrder` 顺序。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl nexa-rag-document -am "-Dtest=DocumentChunkServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: FAIL，因为批量父子查询方法尚不存在。

- [ ] **Step 3: 实现批量查询接口**

新增只读的 `listByParentChunkIds`，对空输入返回空集合，查询条件限定父片段 ID 集合并按 `chunkOrder` 升序。

- [ ] **Step 4: 运行定向测试**

Run: `mvn -pl nexa-rag-document -am "-Dtest=DocumentChunkServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: PASS。

### Task 3: Rerank 后父上下文扩展

**Files:**
- Create: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/retriever/ParentContextExpansionRetriever.java`
- Create: `nexa-rag-retrieval/src/test/java/com/nexarag/retrieval/retriever/ParentContextExpansionRetrieverTest.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/config/RetrievalProperties.java`
- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/ParentContextExpansionNode.java`
- Create: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/chat/ParentContextExpansionNodeTest.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/config/ChatWorkflowConfiguration.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/constants/ChatWorkflowNodeConstants.java`

- [ ] **Step 1: 编写失败测试**

验证单一命中保留自身并补充前后一个同父窗口；验证小父片段或同父多命中时以完整父片段替代这些子窗口；验证空父 ID 保持原结果。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl nexa-rag-workflow -am "-Dtest=ParentContextExpansionRetrieverTest,ParentContextExpansionNodeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: FAIL，因为扩展器和节点尚不存在。

- [ ] **Step 3: 实现预算受限扩展器**

使用配置控制开关、完整父片段最大 Token、触发完整父片段的同父命中数和相邻窗口数量。只读取 Rerank 已保留的父片段；父片段不满足完整替换条件时，只补充同父、顺序相邻的窗口。结果按原 Rerank 顺序去重。

- [ ] **Step 4: 接入工作流**

将节点置于 `RERANK_NODE` 与 `EVIDENCE_QUALITY_NODE` 之间，最终 Token 预算仍由既有 `EvidenceQualityEvaluator` 统一执行。

- [ ] **Step 5: 运行回归与编译检查**

Run: `mvn -pl nexa-rag-workflow -am "-Dtest=ParentContextExpansionRetrieverTest,ParentContextExpansionNodeTest,RerankNodeTest,EvidenceQualityEvaluatorTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: PASS。

### Task 4: 全链路验证

**Files:**
- Modify: `nexa-rag-boot/src/main/resources/application.yml`

- [ ] **Step 1: 配置默认值**

在 `nexa.retrieval.candidate` 下配置父上下文扩展开关、完整父片段 Token 上限、同父命中阈值和相邻窗口数，默认启用且不超过既有 1800 Token 证据预算。

- [ ] **Step 2: 编译与静态检查**

Run: `mvn -DskipTests compile`

Expected: BUILD SUCCESS。

Run: `git diff --check`

Expected: 无输出。
