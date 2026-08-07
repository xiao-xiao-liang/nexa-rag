# NexaRAG 阶段二收尾 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成阶段二文档领域基础能力收尾，补齐缺失测试，修正 `TODO.md` 阶段归属，并用 Maven 与 ArchUnit 验证边界。

**Architecture:** 本计划只处理 `nexa-rag-document`、`nexa-rag-infra`、`nexa-rag-boot` 的阶段二基础闭环，不实现 MinIO、MinerU、Tika、Redis 队列、真实切分器、检索索引或 Workflow Graph。真实文档入库流水线进入 Phase 2.5 专项设计。

**Tech Stack:** Java 21、Spring Boot 3.5.x、Maven 多模块、MyBatis-Plus、JUnit 5、AssertJ、ArchUnit。

---

## Scope Check

本计划对应 `docs/superpowers/specs/2026-07-04-nexarag-phase2-scope-alignment-design.md` 的“阶段二收尾”部分。

包含：

- 检查当前阶段二基础文件是否完整。
- 补齐 `FileType` 文件类型解析测试。
- 补齐 `DocumentSplitterFactory` 工厂行为测试。
- 修正 `TODO.md` 中阶段二与后续阶段归属。
- 运行阶段二相关测试和架构边界验证。

不包含：

- 真实文件上传。
- MinIO、MinerU、Tika、Redis 或 RocketMQ 适配。
- 真实切分算法。
- Spring AI Alibaba Graph Node/Edge 编排。
- 向量索引或关键词索引实现。

## File Structure

将创建或修改以下文件：

```text
E:\Code\Projects\MyProject\AI\nexa-rag
├── TODO.md
├── nexa-rag-document
│   └── src/test/java/com/nexarag/document
│       ├── enums/FileTypeTest.java
│       └── splitter/DocumentSplitterFactoryTest.java
└── docs/superpowers/plans/2026-07-04-nexarag-phase2-closeout-plan.md
```

文件职责：

- `FileTypeTest.java`：覆盖文件名到 `FileType` 的解析规则，防止后续上传入口使用错误类型。
- `DocumentSplitterFactoryTest.java`：覆盖切分器策略选择与缺失策略异常，保证后续 Workflow 调用抽象时行为稳定。
- `TODO.md`：修正阶段归属，避免真实适配继续误挂在阶段二。
- 本计划文档：记录阶段二收尾执行步骤。

## Task 1: 基线检查

**Files:**
- Read: `E:\Code\Projects\MyProject\AI\nexa-rag\docs\superpowers\specs\2026-07-04-nexarag-phase2-scope-alignment-design.md`
- Read: `E:\Code\Projects\MyProject\AI\nexa-rag\TODO.md`
- Read: `E:\Code\Projects\MyProject\AI\nexa-rag\nexa-rag-document\src\main\java\com\nexarag\document\splitter\DocumentSplitterFactory.java`
- Read: `E:\Code\Projects\MyProject\AI\nexa-rag\nexa-rag-document\src\main\java\com\nexarag\document\enums\FileType.java`

- [ ] **Step 1: 确认工作区干净**

Run:

```powershell
git status --short --branch
```

Expected:

```text
## master
```

- [ ] **Step 2: 运行当前阶段二相关测试作为基线**

Run:

```powershell
mvn -pl nexa-rag-document,nexa-rag-infra -am test
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 3: 运行当前架构边界测试作为基线**

Run:

```powershell
mvn -pl nexa-rag-boot -am test -Dtest=ModuleDependencyTest
```

Expected: `BUILD SUCCESS`。

## Task 2: 补齐 FileType 解析测试

**Files:**
- Create: `E:\Code\Projects\MyProject\AI\nexa-rag\nexa-rag-document\src\test\java\com\nexarag\document\enums\FileTypeTest.java`
- Test: `E:\Code\Projects\MyProject\AI\nexa-rag\nexa-rag-document\src\test\java\com\nexarag\document\enums\FileTypeTest.java`

- [ ] **Step 1: 创建 FileType 解析测试**

Create `FileTypeTest.java`:

```java
package com.nexarag.document.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档文件类型解析测试。
 */
class FileTypeTest {

    @Test
    void shouldResolveSupportedFileTypesFromFileName() {
        assertThat(FileType.fromFileName("demo.pdf")).isEqualTo(FileType.PDF);
        assertThat(FileType.fromFileName("demo.docx")).isEqualTo(FileType.WORD);
        assertThat(FileType.fromFileName("demo.xlsx")).isEqualTo(FileType.EXCEL);
        assertThat(FileType.fromFileName("demo.csv")).isEqualTo(FileType.EXCEL);
        assertThat(FileType.fromFileName("demo.pptx")).isEqualTo(FileType.PPT);
        assertThat(FileType.fromFileName("demo.md")).isEqualTo(FileType.MARKDOWN);
        assertThat(FileType.fromFileName("demo.txt")).isEqualTo(FileType.TEXT);
    }

    @Test
    void shouldReturnUnknownWhenFileNameUnsupported() {
        assertThat(FileType.fromFileName(null)).isEqualTo(FileType.UNKNOWN);
        assertThat(FileType.fromFileName("README")).isEqualTo(FileType.UNKNOWN);
        assertThat(FileType.fromFileName("demo.zip")).isEqualTo(FileType.UNKNOWN);
    }

    @Test
    void shouldResolveUpperCaseExtension() {
        assertThat(FileType.fromFileName("DEMO.PDF")).isEqualTo(FileType.PDF);
    }
}
```

- [ ] **Step 2: 运行 FileType 测试**

Run:

```powershell
mvn -pl nexa-rag-document -am test -Dtest=FileTypeTest
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 3: 暂不提交**

不要在本任务单独提交，等 Task 5 全部验证完成后使用 `git-commit-workflow` 统一提交阶段二收尾改动。

## Task 3: 补齐 DocumentSplitterFactory 行为测试

**Files:**
- Create: `E:\Code\Projects\MyProject\AI\nexa-rag\nexa-rag-document\src\test\java\com\nexarag\document\splitter\DocumentSplitterFactoryTest.java`
- Test: `E:\Code\Projects\MyProject\AI\nexa-rag\nexa-rag-document\src\test\java\com\nexarag\document\splitter\DocumentSplitterFactoryTest.java`

- [ ] **Step 1: 创建切分器工厂测试**

Create `DocumentSplitterFactoryTest.java`:

```java
package com.nexarag.document.splitter;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.dto.SplitConfigRequest;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.document.enums.DocumentErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文档切分器工厂测试。
 */
class DocumentSplitterFactoryTest {

    @Test
    void shouldReturnSplitterByStrategy() {
        DocumentSplitter splitter = new TestDocumentSplitter(SplitStrategy.PARENT_MARKDOWN);
        DocumentSplitterFactory factory = new DocumentSplitterFactory(List.of(splitter));

        DocumentSplitter result = factory.getRequired(SplitStrategy.PARENT_MARKDOWN);

        assertThat(result).isSameAs(splitter);
    }

    @Test
    void shouldThrowServiceExceptionWhenSplitterMissing() {
        DocumentSplitterFactory factory = new DocumentSplitterFactory(List.of());

        assertThatThrownBy(() -> factory.getRequired(SplitStrategy.EXCEL))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID.code());
    }

    private record TestDocumentSplitter(SplitStrategy strategy) implements DocumentSplitter {

        @Override
        public List<ChunkDraft> split(String content, SplitConfigRequest config) {
            return List.of(new ChunkDraft(content, java.util.Map.of(), false));
        }
    }
}
```

- [ ] **Step 2: 运行切分器工厂测试**

Run:

```powershell
mvn -pl nexa-rag-document -am test -Dtest=DocumentSplitterFactoryTest
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 3: 暂不提交**

不要在本任务单独提交，等 Task 5 全部验证完成后使用 `git-commit-workflow` 统一提交阶段二收尾改动。

## Task 4: 修正 TODO 阶段归属

**Files:**
- Modify: `E:\Code\Projects\MyProject\AI\nexa-rag\TODO.md`

- [ ] **Step 1: 将 TODO.md 替换为阶段归属清晰的中文内容**

Replace `TODO.md` content with:

```markdown
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
```

- [ ] **Step 2: 检查 TODO.md 只包含中文阶段归属调整**

Run:

```powershell
git diff -- TODO.md
```

Expected: diff 只包含阶段归属和中文文案调整，不包含业务代码改动。

## Task 5: 阶段二收尾验证

**Files:**
- Test: `E:\Code\Projects\MyProject\AI\nexa-rag\nexa-rag-document\src\test\java\com\nexarag\document\enums\FileTypeTest.java`
- Test: `E:\Code\Projects\MyProject\AI\nexa-rag\nexa-rag-document\src\test\java\com\nexarag\document\splitter\DocumentSplitterFactoryTest.java`
- Modify: `E:\Code\Projects\MyProject\AI\nexa-rag\TODO.md`

- [ ] **Step 1: 运行文档和基础设施模块测试**

Run:

```powershell
mvn -pl nexa-rag-document,nexa-rag-infra -am test
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 2: 运行架构边界测试**

Run:

```powershell
mvn -pl nexa-rag-boot -am test -Dtest=ModuleDependencyTest
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 3: 检查文档模块不依赖 workflow**

Run:

```powershell
mvn -pl nexa-rag-document dependency:tree | Select-String "nexa-rag-workflow"
```

Expected: no output。

- [ ] **Step 4: 检查 diff 空白问题**

Run:

```powershell
git diff --check
```

Expected: no output。

- [ ] **Step 5: 检查工作区改动范围**

Run:

```powershell
git status --short
```

Expected:

```text
 M TODO.md
?? nexa-rag-document/src/test/java/com/nexarag/document/enums/FileTypeTest.java
?? nexa-rag-document/src/test/java/com/nexarag/document/splitter/DocumentSplitterFactoryTest.java
```

## Task 6: 使用 git 提交 workflow 提交阶段二收尾改动

**Files:**
- Stage: `E:\Code\Projects\MyProject\AI\nexa-rag\TODO.md`
- Stage: `E:\Code\Projects\MyProject\AI\nexa-rag\nexa-rag-document\src\test\java\com\nexarag\document\enums\FileTypeTest.java`
- Stage: `E:\Code\Projects\MyProject\AI\nexa-rag\nexa-rag-document\src\test\java\com\nexarag\document\splitter\DocumentSplitterFactoryTest.java`

- [ ] **Step 1: 使用 git-commit-workflow skill 进入提交模式**

在真正提交前，必须使用 `git-commit-workflow` skill。

- [ ] **Step 2: 暂存阶段二收尾改动**

Run:

```powershell
git add TODO.md nexa-rag-document/src/test/java/com/nexarag/document/enums/FileTypeTest.java nexa-rag-document/src/test/java/com/nexarag/document/splitter/DocumentSplitterFactoryTest.java
```

Expected: staging succeeds。

- [ ] **Step 3: 提交阶段二收尾改动**

Run:

```powershell
git commit -m "test(document): 补齐阶段二收尾验证"
```

Expected: commit succeeds。

- [ ] **Step 4: 确认提交后工作区干净**

Run:

```powershell
git status --short --branch
git log --oneline -1
```

Expected: working tree clean，最近提交为 `test(document): 补齐阶段二收尾验证`。

## Self-Review

- Spec coverage:
  - 阶段二收尾测试缺口由 Task 2 和 Task 3 覆盖。
  - 阶段归属冲突由 Task 4 覆盖。
  - Maven 与 ArchUnit 验证由 Task 5 覆盖。
  - 提交流程遵循用户要求，由 Task 6 覆盖。
- Placeholder scan:
  - 本计划不包含待定项。
  - 本计划不要求实现真实 infra、真实切分、真实检索或 Workflow Graph。
- Type consistency:
  - 测试类使用现有 `FileType`、`SplitStrategy`、`DocumentSplitterFactory`、`ServiceException`、`DocumentErrorCode`。
  - 所有路径均位于 `E:\Code\Projects\MyProject\AI\nexa-rag`。
  - 计划中的 Maven 命令与当前多模块结构一致。