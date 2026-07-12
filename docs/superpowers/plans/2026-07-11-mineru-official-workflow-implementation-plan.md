# MinerU 官方解析完整入库链路实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 MinerU 官方 v4 文件解析，并通过真实 HTTP 上传接口打通 RocketMQ、Workflow、MinIO、文档切分、Milvus 和 Elasticsearch 的完整入库链路。

**Architecture:** 保持现有 `MinerUClient` 同步接口不变，由 `OfficialMinerUClient` 内部封装申请签名上传地址、PUT 上传、批次轮询和 ZIP 下载。默认测试使用本地 `HttpServer` 验证协议，真实集成测试通过显式开关启动 Spring Boot 随机端口应用，从 `/api/documents/upload` 发起完整异步流水线并保留最终数据。

**Tech Stack:** Java 21、Spring Boot 3.5、RestTemplate、Jackson、JUnit 5、AssertJ、内置 `HttpServer`、RocketMQ、MySQL、MinIO、Milvus、Elasticsearch、Maven。

---

## 文件结构

- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/client/OfficialMinerUClient.java`
  - 实现官方 API 完整调用和安全错误转换。
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/client/OfficialMinerUResponse.java`
  - 描述申请上传地址与批次查询响应中本次使用的字段。
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/config/MinerUProperties.java`
  - 为官方地址提供明确默认值，不增加模型配置。
- Modify: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/OfficialMinerUClientTest.java`
  - 使用本地 HTTP 服务按 TDD 覆盖成功、失败、超时和敏感信息保护。
- Create: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/OfficialMinerUClientIntegrationTest.java`
  - 显式开启后真实调用 MinerU 官方 API。
- Modify: `nexa-rag-boot/src/main/resources/application.yml`
  - 在用户现有配置改动基础上补充安全的官方地址和环境变量 Token 占位。
- Modify: `nexa-rag-boot/src/main/resources/application-integration.yml`
  - 增加官方 MinerU 与完整 Workflow 测试需要的环境变量配置。
- Create: `nexa-rag-boot/src/test/java/com/nexarag/boot/integration/MinerUOfficialDocumentWorkflowIntegrationTest.java`
  - 从真实 HTTP 上传到最终 `INDEXED`，并核验所有外部存储。

## Task 1：定义官方协议响应模型

**Files:**
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/client/OfficialMinerUResponse.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/OfficialMinerUClientTest.java`

- [ ] **Step 1：编写申请上传地址响应缺失字段的失败测试**

在 `OfficialMinerUClientTest` 中先新增一个本地 `HttpServer` 场景：`POST /api/v4/file-urls/batch` 返回 `code=0`，但不返回 `batch_id`。调用 `parse` 后断言抛出 `ServiceException`，消息包含“申请上传地址”“documentId=1”，且不包含测试 Token。

```java
@Test
void parseShouldRejectApplyResponseWithoutBatchId() throws Exception {
    startServer(exchange -> respondJson(exchange, 200, """
            {"code":0,"data":{"file_urls":["http://127.0.0.1/upload"]},"msg":"ok"}
            """));

    ServiceException exception = catchThrowableOfType(
            () -> createClient().parse(command()), ServiceException.class);

    assertThat(exception)
            .hasMessageContaining("申请上传地址")
            .hasMessageContaining("documentId=1")
            .hasMessageNotContaining(API_KEY);
}
```

- [ ] **Step 2：运行测试确认按预期失败**

Run:

```powershell
mvn -pl nexa-rag-infra -am -Dtest=OfficialMinerUClientTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL，原因是当前客户端仍抛出“官方MinerU接口尚未完成适配”，尚未解析官方响应。

- [ ] **Step 3：新增最小官方响应 DTO**

创建包级响应模型，所有类和字段说明使用简体中文 Java doc：

```java
@JsonIgnoreProperties(ignoreUnknown = true)
record OfficialMinerUResponse(int code, String msg, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(
            @JsonProperty("batch_id") String batchId,
            @JsonProperty("file_urls") List<String> fileUrls,
            @JsonProperty("extract_result") List<ExtractResult> extractResult) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExtractResult(
            @JsonProperty("file_name") String fileName,
            String state,
            @JsonProperty("full_zip_url") String fullZipUrl,
            @JsonProperty("err_msg") String errMsg) {
    }
}
```

在 `OfficialMinerUClient` 中注入 `ObjectMapper`，只实现申请响应反序列化与字段校验，使本测试从“未适配”转为预期协议异常。

- [ ] **Step 4：运行测试确认通过**

Run: 与 Step 2 相同。

Expected: PASS。

- [ ] **Step 5：提交本任务**

按 git 提交 skill 审查并只提交本任务相关文件，提交信息：

```text
feat(mineru): 定义官方API响应协议
```

## Task 2：实现申请地址和文件上传

**Files:**
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/client/OfficialMinerUClient.java`
- Modify: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/OfficialMinerUClientTest.java`

- [ ] **Step 1：编写申请请求和 PUT 上传失败测试**

增加两个独立测试：

1. 申请接口必须收到 `Authorization: Bearer test-api-key`，JSON 中包含单个文件、`data_id=document-1` 和 `model_version=vlm`。
2. 签名上传接口返回 HTTP 500 时，异常包含“上传原始文件”和 `documentId`，但不包含 API Key 和签名 URL。

```java
assertThat(applyAuthorization).isEqualTo("Bearer " + API_KEY);
assertThat(applyRequestBody)
        .contains("\"name\":\"demo.pdf\"")
        .contains("\"data_id\":\"document-1\"")
        .contains("\"model_version\":\"vlm\"");
assertThat(uploadAuthorization).isNull();
assertThat(uploadRequestBody).isEqualTo(FILE_BYTES);
```

- [ ] **Step 2：运行测试确认失败**

Run:

```powershell
mvn -pl nexa-rag-infra -am -Dtest=OfficialMinerUClientTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL，尚未发送申请和上传请求。

- [ ] **Step 3：实现申请地址和 PUT 上传**

在 `OfficialMinerUClient` 中增加以下职责清晰的私有方法：

```java
private ApplyResult applyUploadUrl(MinerUParseCommand command, byte[] fileBytes)
private void uploadFile(MinerUParseCommand command, String fileUrl, byte[] fileBytes)
private HttpHeaders buildAuthorizedJsonHeaders()
private RestTemplate buildRestTemplate()
private String resolveOfficialEndpoint()
```

实现关键步骤：

```java
// 1. 一次性读取原始文件，避免重复消费输入流
byte[] fileBytes = command.inputStream().readAllBytes();

// 2. 申请官方签名上传地址，固定使用 vlm
Map<String, Object> body = Map.of(
        "files", List.of(Map.of(
                "name", sanitizeFileName(command.fileName()),
                "data_id", "document-" + command.documentId())),
        "model_version", "vlm");

// 3. 使用签名地址上传原始字节，禁止携带 Bearer Token
HttpHeaders uploadHeaders = new HttpHeaders();
uploadHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
```

`officialEndpoint` 为空时回退到 `https://mineru.net`；错误信息不得包含签名 URL、Authorization Header 或 Token。

- [ ] **Step 4：运行测试确认通过**

Run: 与 Step 2 相同。

Expected: PASS。

- [ ] **Step 5：提交本任务**

```text
feat(mineru): 实现官方文件签名上传
```

## Task 3：实现异步轮询和 ZIP 下载

**Files:**
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/parser/mineru/client/OfficialMinerUClient.java`
- Modify: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/OfficialMinerUClientTest.java`

- [ ] **Step 1：编写 running 到 done 的成功测试**

本地服务依次返回：

- 申请成功；
- PUT 上传成功；
- 第一次查询 `running`；
- 第二次查询 `done` 并给出 `full_zip_url`；
- ZIP 下载返回测试 ZIP 字节。

断言：

```java
assertThat(response.zipInputStream().readAllBytes()).isEqualTo(ZIP_BYTES);
assertThat(response.metadata())
        .containsEntry("clientMode", "official")
        .containsEntry("batchId", "batch-1")
        .containsEntry("pollCount", 2)
        .containsEntry("zipSize", ZIP_BYTES.length)
        .doesNotContainValue(API_KEY)
        .doesNotContainValue(uploadUrl)
        .doesNotContainValue(zipUrl);
```

- [ ] **Step 2：编写 failed、轮询超时和线程中断测试**

分别验证：

- `state=failed` 时异常包含官方 `err_msg`、失败阶段和 `documentId`。
- `maxPollCount=2` 且持续 `running` 时只查询两次并抛出轮询超时。
- `Thread.sleep` 被中断时恢复中断标记并抛出中文异常。

- [ ] **Step 3：运行测试确认失败**

Run:

```powershell
mvn -pl nexa-rag-infra -am -Dtest=OfficialMinerUClientTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL，尚未轮询和下载 ZIP。

- [ ] **Step 4：实现轮询、终态判断和 ZIP 下载**

增加：

```java
private PollResult waitForResult(MinerUParseCommand command, String batchId)
private byte[] downloadZip(MinerUParseCommand command, String zipUrl)
private Duration resolvePollInterval()
private int resolveMaxPollCount()
```

关键流程：

```java
// 1. 按配置次数查询批次任务
for (int pollCount = 1; pollCount <= resolveMaxPollCount(); pollCount++) {
    OfficialMinerUResponse response = queryBatchResult(command, batchId);
    OfficialMinerUResponse.ExtractResult result = requiredFirstResult(command, response);

    // 2. 完成时返回 ZIP 地址，失败时立即终止
    if ("done".equalsIgnoreCase(result.state())) {
        return new PollResult(result.fullZipUrl(), pollCount);
    }
    if ("failed".equalsIgnoreCase(result.state())) {
        throw remoteFailure(command, "官方解析任务失败", result.errMsg());
    }

    // 3. 非终态等待下一轮，并正确传播线程中断
    sleepBeforeNextPoll(command);
}
```

`parse` 最终返回现有 `MinerUParseResponse`，不改动上层接口。

- [ ] **Step 5：运行官方客户端与本地客户端回归测试**

Run:

```powershell
mvn -pl nexa-rag-infra -am -Dtest=OfficialMinerUClientTest,LocalMinerUClientTest,MinerUDocumentParserTest,MinerUZipResultExtractorTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS。

- [ ] **Step 6：提交本任务**

```text
feat(mineru): 完成官方异步解析轮询
```

## Task 4：完善配置与敏感信息保护

**Files:**
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/config/MinerUProperties.java`
- Modify: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/MinerUPropertiesTest.java`
- Modify: `nexa-rag-boot/src/main/resources/application.yml`
- Modify: `nexa-rag-boot/src/main/resources/application-integration.yml`

- [ ] **Step 1：编写官方默认地址配置测试**

新增断言：

```java
assertThat(new MinerUProperties().getOfficialEndpoint())
        .isEqualTo("https://mineru.net");
```

- [ ] **Step 2：运行测试确认失败**

Run:

```powershell
mvn -pl nexa-rag-infra -am -Dtest=MinerUPropertiesTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL，当前默认值为空字符串。

- [ ] **Step 3：修改默认值与 YAML 配置**

`MinerUProperties`：

```java
/** 官方 MinerU 服务地址。 */
private String officialEndpoint = "https://mineru.net";
```

`application.yml` 仅修改用户已经变更的 MinerU 配置行，不触碰其他现有配置：

```yaml
official-endpoint: ${MINERU_OFFICIAL_ENDPOINT:https://mineru.net}
api-key: ${MINERU_API_KEY:}
```

`application-integration.yml` 增加：

```yaml
nexa:
  parser:
    mineru:
      enabled: true
      mode: ${MINERU_MODE:OFFICIAL}
      official-endpoint: ${MINERU_OFFICIAL_ENDPOINT:https://mineru.net}
      api-key: ${MINERU_API_KEY:}
      poll-interval: ${MINERU_POLL_INTERVAL:2s}
      max-poll-count: ${MINERU_MAX_POLL_COUNT:300}
```

- [ ] **Step 4：运行配置和客户端测试**

Run:

```powershell
mvn -pl nexa-rag-infra -am -Dtest=MinerUPropertiesTest,OfficialMinerUClientTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS。

- [ ] **Step 5：检查变更范围后提交**

提交前确认 `application.yml` 中用户其他改动原样保留，并按 git 提交 skill 提交：

```text
config(mineru): 配置官方解析服务
```

## Task 5：增加真实官方客户端集成测试

**Files:**
- Create: `nexa-rag-infra/src/test/java/com/nexarag/infra/parser/mineru/OfficialMinerUClientIntegrationTest.java`

- [ ] **Step 1：编写默认跳过的真实客户端测试**

测试使用双重条件：系统属性显式开启且 `MINERU_API_KEY` 非空。下载官方示例 PDF 后调用真实客户端，并用 `MinerUZipResultExtractor` 校验 ZIP 中存在 Markdown。

```java
@Tag("integration")
@EnabledIfSystemProperty(named = "nexa.integration.mineru.enabled", matches = "true")
class OfficialMinerUClientIntegrationTest {

    @Test
    void parseShouldReturnOfficialZip() throws Exception {
        String apiKey = System.getenv("MINERU_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "未配置 MinerU Token");

        byte[] pdfBytes = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(
                        "https://cdn-mineru.openxlab.org.cn/demo/example.pdf")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()).body();

        MinerUParseResponse response = createClient(apiKey).parse(command(pdfBytes));
        MinerUExtractedResult extracted = new MinerUZipResultExtractor()
                .extract(response.zipInputStream());

        assertThat(extracted.markdownContent()).isNotBlank();
    }
}
```

- [ ] **Step 2：验证默认构建跳过真实调用**

Run:

```powershell
mvn -pl nexa-rag-infra -am -Dtest=OfficialMinerUClientIntegrationTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: SKIPPED，不访问官方服务。

- [ ] **Step 3：在环境变量已由用户配置后执行真实测试**

先只检查环境变量是否存在，不输出值：

```powershell
if ([string]::IsNullOrWhiteSpace($env:MINERU_API_KEY)) { throw '请先在当前终端设置 MINERU_API_KEY' }
```

再执行：

```powershell
mvn -pl nexa-rag-infra -am -Dtest=OfficialMinerUClientIntegrationTest -Dnexa.integration.mineru.enabled=true "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS，并实际消耗一次 MinerU 解析额度。测试输出不得包含 Token 或签名 URL。

- [ ] **Step 4：提交真实客户端测试**

```text
test(mineru): 增加官方服务集成验证
```

## Task 6：增加真实 HTTP 完整 Workflow 测试

**Files:**
- Create: `nexa-rag-boot/src/test/java/com/nexarag/boot/integration/MinerUOfficialDocumentWorkflowIntegrationTest.java`

- [ ] **Step 1：编写 HTTP 上传到 INDEXED 的集成测试骨架**

使用：

```java
@Tag("integration")
@EnabledIfSystemProperty(named = "nexa.integration.workflow.enabled", matches = "true")
@ActiveProfiles("integration")
@SpringBootTest(
        classes = NexaRagApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MinerUOfficialDocumentWorkflowIntegrationTest {
```

测试通过 `HttpClient` 构造 multipart，不直接调用业务服务发起上传。请求 JSON 使用：

```json
{
  "title": "MinerU官方完整链路测试-时间戳",
  "description": "通过真实HTTP上传验证官方MinerU完整入库链路",
  "parseConfig": {
    "enableOcr": false,
    "enableImageDescription": false
  },
  "splitConfig": {
    "splitStrategy": "BROTHER_MARKDOWN",
    "chunkSize": 500,
    "chunkOverlap": 50
  },
  "indexConfig": {
    "enabled": true,
    "vectorEnabled": true,
    "keywordEnabled": true
  }
}
```

从 `Result<UploadDocumentResponse>` JSON 的 `/data/documentId` 读取文档 ID。

- [ ] **Step 2：运行测试确认因官方客户端或链路未完成而失败**

在真实环境开关开启后运行：

```powershell
mvn -pl nexa-rag-boot -am -Dtest=MinerUOfficialDocumentWorkflowIntegrationTest -Dnexa.integration.workflow.enabled=true "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: 在实现全部依赖前 FAIL，并明确记录当前失败阶段；不得通过跳过断言掩盖失败。

- [ ] **Step 3：实现 HTTP 状态轮询与失败诊断**

每 3 秒请求：

```http
GET /api/documents/{documentId}/process-status
```

最大等待 10 分钟。伪代码：

```java
for (int attempt = 1; attempt <= 200; attempt++) {
    JsonNode status = getProcessStatus(documentId);
    String documentStatus = status.at("/data/status").asText();
    if ("INDEXED".equals(documentStatus)) {
        break;
    }
    if ("FAILED".equals(documentStatus)) {
        fail("完整入库失败，documentId=%s，failureStage=%s，failureReason=%s"
                .formatted(documentId,
                        status.at("/data/failureStage").asText(),
                        status.at("/data/failureReason").asText()));
    }
    Thread.sleep(3000);
}
```

日志只记录文档 ID、标题、状态和阶段。

- [ ] **Step 4：增加 MySQL、MinIO 和分片断言**

测试允许自动注入只读核验所需服务，但上传入口仍必须是 HTTP：

```java
Document document = documentService.getRequiredDocument(documentId);
assertThat(document.getStatus()).isEqualTo(DocumentStatus.INDEXED);
assertThat(document.getMessageStatus()).isEqualTo(DocumentPipelineMessageStatus.COMPLETED);
assertThat(document.getOriginalObjectName()).isNotBlank();
assertThat(document.getParsedObjectName()).isNotBlank();

try (InputStream original = fileStorageService.load(document.getOriginalObjectName());
     InputStream parsed = fileStorageService.load(document.getParsedObjectName())) {
    assertThat(original.readAllBytes()).isNotEmpty();
    assertThat(new String(parsed.readAllBytes(), StandardCharsets.UTF_8)).isNotBlank();
}

List<DocumentChunk> chunks = documentChunkService.listByDocumentId(documentId);
assertThat(chunks).isNotEmpty();
assertThat(chunks.stream().filter(chunk -> !Integer.valueOf(1).equals(chunk.getSkipIndex())))
        .allMatch(chunk -> chunk.getStatus() == ChunkStatus.INDEXED)
        .allMatch(chunk -> StringUtils.hasText(chunk.getVectorId()))
        .allMatch(chunk -> StringUtils.hasText(chunk.getKeywordIndexId()));
```

- [ ] **Step 5：增加 Milvus 和 Elasticsearch 真实断言**

复用 `DocumentMilvusIndexIntegrationTest` 已验证的连接与查询方式，但从当前测试生成的首个已索引分片获取 `chunkId` 和文本，不使用固定文档 ID。Elasticsearch 凭据和地址从 Spring `Environment` 读取，不新增硬编码密码。

- [ ] **Step 6：确保测试数据不清理**

不得添加 `@AfterEach` 删除文档、MinIO 对象或索引记录。成功时打印：

```java
System.out.printf("完整入库成功，documentId=%d，title=%s%n", documentId, title);
```

- [ ] **Step 7：运行完整 Workflow 测试并按实际失败阶段修复**

执行前验证环境变量存在但不输出其值：

```powershell
if ([string]::IsNullOrWhiteSpace($env:MINERU_API_KEY)) { throw '请先在当前终端设置 MINERU_API_KEY' }
mvn -pl nexa-rag-boot -am -Dtest=MinerUOfficialDocumentWorkflowIntegrationTest -Dnexa.integration.workflow.enabled=true "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS，文档最终为 `INDEXED`，测试输出文档 ID 和唯一标题。若失败，先执行系统化调试并根据 `failureStage` 修复，不放宽断言。

- [ ] **Step 8：提交完整 Workflow 测试**

```text
test(workflow): 验证MinerU官方完整入库链路
```

## Task 7：全量回归与最终真实验收

**Files:**
- Verify all modified files

- [ ] **Step 1：运行 MinerU 相关回归测试**

```powershell
mvn -pl nexa-rag-infra -am -Dtest=OfficialMinerUClientTest,LocalMinerUClientTest,MinerUPropertiesTest,MinerUDocumentParserTest,MinerUZipResultExtractorTest,MarkdownImageUrlRewriterTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS。

- [ ] **Step 2：运行 infra 模块全部测试**

```powershell
mvn -pl nexa-rag-infra -am test
```

Expected: PASS。

- [ ] **Step 3：运行 boot 与 workflow 相关默认测试**

```powershell
mvn -pl nexa-rag-boot -am test
```

Expected: PASS；显式集成测试默认 SKIPPED。

- [ ] **Step 4：再次执行真实官方客户端测试**

```powershell
mvn -pl nexa-rag-infra -am -Dtest=OfficialMinerUClientIntegrationTest -Dnexa.integration.mineru.enabled=true "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS。

- [ ] **Step 5：执行最终完整 HTTP Workflow 验收**

```powershell
mvn -pl nexa-rag-boot -am -Dtest=MinerUOfficialDocumentWorkflowIntegrationTest -Dnexa.integration.workflow.enabled=true "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS，并保留一条新的完整入库文档。

- [ ] **Step 6：核对敏感信息与工作区范围**

```powershell
rg -n "Bearer eyJ|MINERU_API_KEY=.*eyJ|api-key:\s*eyJ" . -g '!target' -g '!.git'
git diff --check
git status --short --branch
```

Expected: 搜索无真实 Token；`git diff --check` 退出码为 0；`.superpowers/` 仍不提交；用户已有无关修改未被覆盖。

- [ ] **Step 7：按 git 提交 skill 整理最后提交并报告结果**

若前面任务已经逐步提交，只提交剩余的测试修复或小范围调整。最终报告必须包含：

- 所有提交哈希与中文 Conventional Commit 信息。
- 通过的单元测试、模块测试和两个真实集成测试命令。
- 完整 Workflow 最终文档 ID、唯一标题和 `INDEXED` 状态。
- 未提交的用户文件与默认排除目录。
- 真实 Token 未写入仓库的检查结果。
