# 飞书 DOCX 代码围栏修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 保持飞书 DOCX 流式导出链路，并将导出为单列表格的代码块恢复为带语言标记的 Markdown 围栏代码块。

**Architecture:** 在 Pandoc 生成 `content.md` 后，流式读取 DOCX 的 `word/document.xml`，只识别单行、单单元格、浅灰底的飞书代码表格。处理器逐个恢复原始换行和缩进，并替换 Pandoc 对应的单列表格，避免全量读取飞书 Block 或将整篇 DOCX 常驻内存。

**Tech Stack:** Java 21、StAX、ZIP、Pandoc、JUnit 5、AssertJ。

---

### Task 1: 建立代码表格到 Markdown 围栏的回归测试

**Files:**
- Create: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/pandoc/FeishuDocxCodeBlockMarkdownRewriterTest.java`

- [x] **Step 1: 创建包含浅灰单列表格的最小 DOCX 测试制品**

测试 XML 包含 `w:shd w:fill="f5f6f7"`、首行 `Python`、`w:br` 和 `xml:space="preserve"` 的四空格缩进。

- [x] **Step 2: 断言 Pandoc 表格被替换为保持缩进的 Python 围栏**

```java
assertThat(Files.readString(markdownPath)).isEqualTo("说明\n\n```python\nif enabled:\n    return 42\n```\n");
```

- [x] **Step 3: 运行测试确认失败**

Run: `mvn -pl nexa-rag-infra -Dtest=FeishuDocxCodeBlockMarkdownRewriterTest test`

Expected: 编译失败，因为代码恢复处理器尚不存在。

### Task 2: 实现流式 DOCX 代码表格恢复器

**Files:**
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/pandoc/FeishuDocxCodeBlockMarkdownRewriter.java`

- [x] **Step 1: 用 StAX 逐表读取 `word/document.xml`**

仅接受一行一列、`f5f6f7` 填充的表格；将表格文字与 `w:br` 依序写入临时文件，保留 XML 声明的空格。

- [x] **Step 2: 按顺序匹配 Pandoc 的单列表格输出并写回围栏代码**

将首行语言名映射为 `python`、`java`、`javascript`、`typescript`、`sql`、`json`、`yaml`、`bash`、`text` 等 Markdown 标记；正文不进入 Java 堆的全量集合。

- [x] **Step 3: 运行测试确认通过**

Run: `mvn -pl nexa-rag-infra -Dtest=FeishuDocxCodeBlockMarkdownRewriterTest test`

Expected: `Tests run: 1, Failures: 0, Errors: 0`。

### Task 3: 接入 Pandoc DOCX 转换链路

**Files:**
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/pandoc/PandocDocxConverter.java`

- [x] **Step 1: 在 Pandoc 成功输出并完成文件存在性校验后调用恢复器**

```java
feishuDocxCodeBlockMarkdownRewriter.rewrite(stagedSource, markdownPath);
```

- [x] **Step 2: 扩展现有 Pandoc 转换器测试，验证恢复器被调用路径可构造**

Run: `mvn -pl nexa-rag-infra -Dtest=PandocDocxConverterTest test`

Expected: `Tests run` 无失败。

- [x] **Step 3: 运行模块测试与差异检查**

Run: `mvn -pl nexa-rag-infra test`, `git diff --check`

Expected: 模块测试通过，且无空白错误。
