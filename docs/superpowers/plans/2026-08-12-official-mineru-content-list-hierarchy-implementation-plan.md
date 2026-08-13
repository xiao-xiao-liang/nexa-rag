# 官方 MinerU Content List 层级恢复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使用官方 MinerU 的 `content_list_v2.json` 标题与边界框数据恢复 PDF 标题层级，使其进入既有 Markdown 章节树。

**Architecture:** 新增独立的 Content List 标题提取器，流式读取 V2 并兼容旧版 Content List，按同文档标题框高度分组推断相对层级。结构解析器优先读取 V2 制品，且 PDF 版式证据优先于编号证据；本地 MinerU 的 middle JSON 路径保持不变。

**Tech Stack:** Java 21、Jackson 流式 API、Spring Boot、JUnit 5、AssertJ。

---

### Task 1: 覆盖官方 V2 标题提取与层级推断

**Files:**
- Create: `nexa-rag-document/src/test/java/com/nexarag/document/splitter/structure/MinerUContentListHeadingEvidenceExtractorTest.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/structure/MinerUContentListHeadingEvidenceExtractor.java`

- [ ] **Step 1: 编写失败测试**

构造 V2 页面数组，包含嵌套 `content.title_content[].content`、统一的官方 `level: 2` 和不同高度的 `bbox`；断言提取器忽略统一 level，依据高度输出 `PDF_LAYOUT` 层级，并排除以 `•` 开头的项目符号正文。

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
mvn -pl nexa-rag-document -am -Dtest=MinerUContentListHeadingEvidenceExtractorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 编译失败，因为提取器尚不存在。

- [ ] **Step 3: 实现流式 V2 提取器与 V1 兜底**

使用 Jackson `JsonParser` 按块读取；只保留 V2 `type=title` 和 V1 具备 `text_level` 的文本块。按 bbox 高度降序分带（同带最大跨度 2 像素）映射到 1–6 级，且不读取完整 JSON 树。

- [ ] **Step 4: 运行测试并确认通过**

Run:

```powershell
mvn -pl nexa-rag-document -am -Dtest=MinerUContentListHeadingEvidenceExtractorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 测试通过。

### Task 2: 接入 PDF 结构解析编排

**Files:**
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/structure/DocumentStructureResolver.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/structure/HeadingHierarchyResolver.java`
- Modify: `nexa-rag-document/src/test/java/com/nexarag/document/splitter/structure/DocumentStructureResolverTest.java`

- [ ] **Step 1: 编写失败集成测试**

向 `DocumentSplitContext` 注入 `MINERU_CONTENT_LIST_V2_JSON`，并为全为 `##` 的 Markdown 提供 V2 bbox 标题。断言结果来自 `PDF_LAYOUT`，且层级为父子关系，而不是同级 Markdown 标题。

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
mvn -pl nexa-rag-document -am -Dtest=DocumentStructureResolverTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 失败，因为当前解析器忽略 V2 制品。

- [ ] **Step 3: 接入并调整证据优先级**

在 PDF 分支优先加载 `MINERU_CONTENT_LIST_V2_JSON`，无 V2 时回退 `MINERU_CONTENT_LIST_JSON`；将 `PDF_LAYOUT` 排在 `PDF_NUMBERING` 前，避免编号规则把版式明确的子标题提升为一级。

- [ ] **Step 4: 运行测试并确认通过**

Run:

```powershell
mvn -pl nexa-rag-document -am -Dtest=DocumentStructureResolverTest,MinerUContentListHeadingEvidenceExtractorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 两个测试类通过。

### Task 3: 编译与实际验收

**Files:**
- No production-file changes expected.

- [ ] **Step 1: 执行全项目编译**

Run:

```powershell
mvn -DskipTests compile
git diff --check
```

Expected: 命令全部成功。

- [ ] **Step 2: 重新解析验收 PDF**

重新处理 `Java集合.pdf`，确认 `document_section.parent_section_id` 出现父子关系，且 `heading_path_json` 包含完整路径。
