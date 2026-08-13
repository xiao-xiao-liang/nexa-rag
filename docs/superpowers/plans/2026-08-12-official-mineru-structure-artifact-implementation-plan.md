# 官方 MinerU 结构制品识别修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让官方 MinerU ZIP 中带原文件名前缀的结构 JSON 被持久化，并进入既有 PDF 层级恢复链路。

**Architecture:** 保持官方客户端和解析编排器不变，仅扩展 ZIP 提取器对官方产物命名的识别，并将外部文件名归一为现有内部制品名。`content_list_v2` 仅作为可追溯制品发布，不改变当前 Markdown 切分逻辑。

**Tech Stack:** Java 21、Spring Boot、JUnit 5、AssertJ、ZIP 流式解压。

---

### Task 1: 覆盖官方 ZIP 结构文件命名

**Files:**
- Modify: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/extract/MinerUZipFileExtractorTest.java`

- [ ] **Step 1: 编写失败测试**

在 `MinerUZipFileExtractorTest` 新增测试，构造包含以下条目的 ZIP：

```java
"result/Java集合_middle.json", "{\"pdf_info\":[]}",
"result/Java集合_content_list.json", "[]",
"result/Java集合_content_list_v2.json", "[]"
```

断言 `structureArtifacts()` 依次包含归一化路径：

```java
"mineru-middle.json",
"mineru-content-list.json",
"mineru-content-list-v2.json"
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
mvn -pl nexa-rag-infra -Dtest=MinerUZipFileExtractorTest test
```

Expected: 新测试失败，原因是当前精确文件名判断忽略了带原文件名前缀的 JSON。

- [ ] **Step 3: 实现最小识别逻辑**

修改 `MinerUZipFileExtractor`：

```java
private boolean isStructureJson(String fileName) {
    String simpleName = simpleName(fileName).toLowerCase(Locale.ROOT);
    return "middle.json".equals(simpleName) || simpleName.endsWith("_middle.json")
            || "content_list.json".equals(simpleName) || simpleName.endsWith("_content_list.json")
            || "content_list_v2.json".equals(simpleName) || simpleName.endsWith("_content_list_v2.json");
}
```

让 `structureFileName` 对三种名称分别映射到 `mineru-middle.json`、`mineru-content-list.json`、`mineru-content-list-v2.json`。

- [ ] **Step 4: 运行测试并确认通过**

Run:

```powershell
mvn -pl nexa-rag-infra -Dtest=MinerUZipFileExtractorTest test
```

Expected: 全部 `MinerUZipFileExtractorTest` 通过。

### Task 2: 发布 V2 制品类型

**Files:**
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/publish/ArtifactPublisher.java`
- Modify: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/publish/ArtifactPublisherTest.java`（如该测试不存在则新建）

- [ ] **Step 1: 编写失败测试**

覆盖 `mineru-content-list-v2.json` 发布后的元数据类型为：

```java
"MINERU_CONTENT_LIST_V2_JSON"
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
mvn -pl nexa-rag-infra -Dtest=ArtifactPublisherTest test
```

Expected: 失败，当前实现会将其错误归类为 `MINERU_CONTENT_LIST_JSON`。

- [ ] **Step 3: 实现最小发布类型映射**

在 `resolveStructureArtifactType` 中精确处理 V2 文件名，其余现有逻辑保持不变。

- [ ] **Step 4: 运行测试并确认通过**

Run:

```powershell
mvn -pl nexa-rag-infra -Dtest=ArtifactPublisherTest,MinerUZipFileExtractorTest test
```

Expected: 两个测试类通过。

### Task 3: 编译与真实链路验收准备

**Files:**
- No production-file changes expected.

- [ ] **Step 1: 执行模块测试与全项目编译**

Run:

```powershell
mvn -pl nexa-rag-infra -Dtest=MinerUZipFileExtractorTest,ArtifactPublisherTest test
mvn -DskipTests compile
git diff --check
```

Expected: 命令全部成功。

- [ ] **Step 2: 重新处理验收文档**

重新上传或重新处理 `Java集合.pdf`，确认 `parsed_metadata_json.structureArtifacts` 中存在 `MINERU_MIDDLE_JSON`，且 `document_section.parent_section_id` 出现真实父子关系。
