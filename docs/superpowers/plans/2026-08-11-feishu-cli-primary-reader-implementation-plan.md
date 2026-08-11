# 飞书 CLI 优先文档读取 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 飞书 Docx/Wiki 文档优先通过应用身份飞书 CLI 导出 Markdown，并在 CLI 失败时自动降级既有 OpenAPI Block 读取链路。

**Architecture:** `FeishuDocxSourceReader` 保持外部来源 Reader 边界；新增 `FeishuCliDocumentExporter` 封装外部进程调用和 CLI 响应解析。Reader 先调用 Exporter，失败后使用当前 OpenAPI 与 `FeishuBlockMarkdownConverter`，因此 MinIO 制品、工作流切分和索引接口均无需改动。

**Tech Stack:** Java 21、Spring Boot ConfigurationProperties、Jackson、ProcessBuilder、飞书 CLI `@larksuite/cli`。

> 本计划遵循当前要求：不新增、不执行自动化测试或 Maven 编译；实现后仅执行 `git diff --check` 和人工 CLI/导入验收。

---

## 文件结构

- 修改 `nexa-rag-infra/src/main/java/com/nexarag/infra/config/FeishuSourceProperties.java`：重命名为 `FeishuProperties`，承载应用凭据、OpenAPI 基础地址与 CLI 子配置。
- 修改 `nexa-rag-infra/src/main/java/com/nexarag/infra/config/YuqueSourceProperties.java`：重命名为 `YuqueProperties`。
- 新建 `nexa-rag-infra/src/main/java/com/nexarag/infra/source/feishu/FeishuCliDocumentExporter.java`：封装 CLI 命令构造、进程超时、输出采集、JSON 解析和错误分类。
- 修改 `nexa-rag-infra/src/main/java/com/nexarag/infra/source/feishu/FeishuDocxSourceReader.java`：CLI 主路径与 OpenAPI 降级。
- 修改 `nexa-rag-infra/src/main/java/com/nexarag/infra/source/yuque/YuqueSourceReader.java`：更新配置类导入和字段类型。
- 修改 `nexa-rag-boot/src/main/resources/application.yml`：增加 `nexa.source.feishu.cli`。
- 修改 `docs/operations/feishu-cli-document-import.md`：同步最终配置名和手工验收结果。

### Task 1: 归一化来源配置命名并增加 CLI 子配置

**Files:**
- Move: `nexa-rag-infra/src/main/java/com/nexarag/infra/config/FeishuSourceProperties.java` → `nexa-rag-infra/src/main/java/com/nexarag/infra/config/FeishuProperties.java`
- Move: `nexa-rag-infra/src/main/java/com/nexarag/infra/config/YuqueSourceProperties.java` → `nexa-rag-infra/src/main/java/com/nexarag/infra/config/YuqueProperties.java`
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/source/yuque/YuqueSourceReader.java`
- Modify: `nexa-rag-boot/src/main/resources/application.yml`

- [ ] **Step 1: 重命名配置类型并保持现有绑定前缀**

将飞书类型替换为以下形态，保留 `nexa.source.feishu`，避免部署环境变量失效：

```java
/** 飞书外部来源读取配置。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "nexa.source.feishu")
public class FeishuProperties {
    private String appId;
    private String appSecret;
    private String baseUrl = "https://open.feishu.cn";
    private CliProperties cli = new CliProperties();

    /** 飞书 CLI 导出配置。 */
    @Getter
    @Setter
    public static class CliProperties {
        private boolean enabled = true;
        private String executable;
        private String profile = "nexarag";
        private Duration timeout = Duration.ofSeconds(60);
    }
}
```

将语雀类型更名为 `YuqueProperties`，前缀仍为 `nexa.source.yuque`；同步 `YuqueSourceReader` 的 import 和字段类型。

- [ ] **Step 2: 配置 CLI 运行参数**

在已有 `nexa.source.feishu` 下追加：

```yaml
cli:
  enabled: ${NEXA_SOURCE_FEISHU_CLI_ENABLED:true}
  executable: ${NEXA_SOURCE_FEISHU_CLI_EXECUTABLE:}
  profile: ${NEXA_SOURCE_FEISHU_CLI_PROFILE:nexarag}
  timeout: ${NEXA_SOURCE_FEISHU_CLI_TIMEOUT:60s}
```

不要填入开发机特定目录。`executable` 为空时由后续 `ProcessBuilder` 使用 `lark-cli`，交给服务进程 `PATH` 解析。

### Task 2: 实现受限的飞书 CLI 导出器

**Files:**
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/source/feishu/FeishuCliDocumentExporter.java`

- [ ] **Step 1: 定义导出器边界和返回结果**

新增 Spring `@Component`，构造注入 `FeishuProperties` 与 `ObjectMapper`。仅暴露：

```java
/** 以飞书 CLI 应用身份导出单篇飞书 Docx 或 Wiki 文档。 */
public SourceReadResultBO export(SourceReadRequestDTO request)
```

成功时从 CLI JSON 的 `data.document` 读取 `content`、`document_id`、`revision_id`；快照直接使用 CLI 标准输出 UTF-8 字节，内容类型为 `application/json`，metadata 至少包含：

```java
Map.of("sourceType", "FEISHU", "reader", "FEISHU_CLI")
```

- [ ] **Step 2: 以参数列表安全启动 CLI**

构建参数列表，绝不经由 shell 拼接：

```java
List<String> command = new ArrayList<>();
command.add(resolveExecutable());
if (StringUtils.hasText(properties.getCli().getProfile())) {
    command.addAll(List.of("--profile", properties.getCli().getProfile()));
}
command.addAll(List.of("docs", "+fetch", "--as", "bot", "--doc", request.sourceUrl(),
        "--doc-format", "markdown", "--format", "json"));
Process process = new ProcessBuilder(command).start();
```

`resolveExecutable()` 在显式 `executable` 非空时验证常规文件和可执行性；否则返回 `lark-cli`。读取标准输出和标准错误时使用两个 Java 21 虚拟线程，避免任一管道缓冲区填满导致 `waitFor` 死锁。

- [ ] **Step 3: 明确超时与错误分类**

使用 `process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)`。超时必须调用 `destroyForcibly()`，等待输出读取任务结束，并抛出带 documentId 的内部 `FeishuCliExportException`。

退出码非零、空输出、JSON 解析失败、`ok != true`、正文为空均抛出该异常；错误文本限制为 300 个字符，避免日志与失败详情膨胀。解析到飞书权限拒绝码（例如 `131006`、`99991672` 或 `permission denied`）时抛出 `DocumentPipelineNonRetryableException`，不进入 OpenAPI 降级。

### Task 3: 将 Feishu Reader 改造为 CLI 优先、OpenAPI 降级

**Files:**
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/source/feishu/FeishuDocxSourceReader.java`

- [ ] **Step 1: 更新依赖和主读取流程**

将配置类型替换为 `FeishuProperties`，注入 `FeishuCliDocumentExporter`。`read` 的前半段改为：

```java
if (properties.getCli().isEnabled()) {
    try {
        return feishuCliDocumentExporter.export(request);
    } catch (DocumentPipelineNonRetryableException exception) {
        throw exception;
    } catch (FeishuCliExportException exception) {
        log.warn("飞书CLI导出失败，降级OpenAPI Block读取，documentId={}，reason={}",
                request.documentId(), exception.getMessage());
    }
}
return readByOpenApi(request);
```

保留 `supports` 与 URL 校验行为，CLI 关闭时直接进入 OpenAPI。

- [ ] **Step 2: 收拢既有 OpenAPI 实现为降级方法**

将原有 `read` 中 App ID/App Secret 校验、tenant token 获取、Wiki → Docx 解析、固定 revision、分页读取 Block、Markdown 转换和快照组装移动至：

```java
private SourceReadResultBO readByOpenApi(SourceReadRequestDTO request)
```

OpenAPI 成功结果的 metadata 增加 `reader=FEISHU_OPEN_API_FALLBACK`。原有 `HttpClientErrorException` 映射、429 重试语义和不可重试权限异常不得改变。

- [ ] **Step 3: 记录成功与降级恢复日志**

CLI 成功日志记录 `documentId`、CLI Profile、外部 documentId、revisionId 和耗时；不得记录 Markdown、完整命令行中的 URL 查询参数或凭据。OpenAPI 降级成功后记录 `documentId` 与 `reader=FEISHU_OPEN_API_FALLBACK`。

### Task 4: 同步操作文档并进行人工验收

**Files:**
- Modify: `docs/operations/feishu-cli-document-import.md`

- [ ] **Step 1: 同步最终配置名称与降级定义**

确认文档示例与 `FeishuProperties.CliProperties` 的键一致，明确 CLI Profile 由运行服务账号配置，CLI 失败的范围及权限拒绝不降级的语义。

- [ ] **Step 2: 执行人工 CLI 验收，不运行构建或自动化测试**

在运行服务的系统账号执行：

```powershell
lark-cli --profile nexarag whoami --as bot
lark-cli --profile nexarag docs +fetch --as bot --doc '<飞书Docx或Wiki URL>' --doc-format markdown --format json
```

预期：`identity=bot`、`tokenStatus=ready`、返回 `ok=true`，并包含 `data.document.content`、`document_id` 和 `revision_id`。随后从前端或导入接口发起一次飞书来源导入，确认 MinIO 同时生成 CLI JSON 来源快照与 Markdown 解析产物。

- [ ] **Step 3: 执行变更检查**

```powershell
git diff --check
git status --short
```

预期：不存在空白错误；仅出现本功能文件和用户已有未提交改动。

