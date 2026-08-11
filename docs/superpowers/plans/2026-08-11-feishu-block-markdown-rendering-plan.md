# 飞书 Block Markdown 渲染 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将飞书 Docx Block 树正确渲染为可切分、可检索的 Markdown，并支持高亮块和表格。

**Architecture:** 在转换器内先建立 Block ID 索引，再从根 Block 的 `children` 深度优先渲染。容器节点递归渲染子节点，表格节点使用 `table.cells` 和行列属性组装 Markdown 表格，普通文本节点保留现有 Markdown 映射。

**Tech Stack:** Java 21、Spring Framework、Jackson `JsonNode`。

---

### Task 1: 重建飞书 Block 树并渲染 Markdown

**Files:**

- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/source/feishu/FeishuBlockMarkdownConverter.java`
- Modify: `docs/superpowers/specs/2026-08-11-feishu-block-markdown-rendering-design.md`

- [ ] **Step 1: 建立 Block 索引并从根节点遍历**

将 `convert(List<JsonNode>)` 改为按 `block_id` 建立 `Map<String, JsonNode>`，找到类型为 `1` 的页面根 Block 并以其 `children` 顺序渲染。使用已访问集合防止重复与循环；没有根 Block 时以输入顺序渲染顶层 Block。

- [ ] **Step 2: 分离普通块、容器块和非文本块的渲染职责**

普通文本块覆盖 2 至 17 的文本、标题、列表、代码、引用与待办；19、24、25、31、32、33、34 等容器依据子节点递归渲染；22 输出 `---`。图片、文件、电子表格、流程图、嵌入内容等输出稳定、可读的类型名称占位。

- [ ] **Step 3: 按表格单元格顺序输出 Markdown 表格**

读取表格 `property.row_size`、`property.column_size` 与 `cells`，每个单元格递归提取子文本后转义 `|` 和换行。第一行作为 Markdown 表头并补充分隔行；对于不完整的行列关系，保留已读单元格而不抛出异常。

- [ ] **Step 4: 以实际飞书 Wiki 文档回归验证**

重新导入 `https://my.feishu.cn/wiki/WffjwsOjaiHLWDkupDucmv7fndb`，确认解析产物不出现数字 Block 占位，表格内容存在且文档可推进至 `INDEXED`。按用户要求，不执行 Maven 编译或单元测试。

### Task 2: 文档自检

**Files:**

- Modify: `docs/superpowers/specs/2026-08-11-feishu-block-markdown-rendering-design.md`

- [ ] **Step 1: 核对实现与设计一致性**

确认标题映射、容器遍历、表格渲染、非文本占位与异常容错均与设计一致；删除不准确的描述。执行 `git diff --check`，不创建提交。
