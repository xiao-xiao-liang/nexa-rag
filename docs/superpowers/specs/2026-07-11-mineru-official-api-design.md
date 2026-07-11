# MinerU 官方 API 接入设计

## 1. 背景

系统已经通过 `MinerUClient` 屏蔽本地部署与官方服务的调用差异，并由 `MinerUDocumentParser` 统一消费 MinerU 返回的 ZIP 解析产物。当前 `LocalMinerUClient` 已实现本地 `/file_parse` 调用，`OfficialMinerUClient` 仅保留 API Key 校验和未适配异常。

MinerU 官方精准解析 API 采用异步任务模型。本地文件需要先申请签名上传地址，将文件上传到对象存储，再轮询批次任务状态，最终下载包含 Markdown、JSON 和图片资源的 ZIP 产物。因此，本次在不改变上层解析合同的前提下补全官方调用流程。

## 2. 目标

- 在 `mode=OFFICIAL` 时通过 MinerU 官方 v4 API 解析本地 PDF 或 Word 文件。
- 固定使用官方推荐的 `vlm` 模型，不新增模型版本配置。
- 保持 `MinerUClient.parse` 和 `MinerUParseResponse` 不变，使现有 ZIP 提取、图片地址重写和对象存储逻辑继续复用。
- 使用现有连接超时、读取超时、轮询间隔和最大轮询次数配置。
- 确保 Token 不进入日志、异常信息和解析元数据。
- 通过系统真实 HTTP 上传接口发起文档处理，打通 RocketMQ、Workflow、官方 MinerU、切分和检索索引的完整入库链路。
- 完整链路成功后保留测试文档及其外部资源，便于在系统和中间件中查看实际效果。

## 3. 非目标

- 不改造 `DocumentParser` 为跨节点异步任务。
- 不持久化 MinerU 的 `batch_id`，不提供服务重启后的任务恢复。
- 不支持切换 `pipeline` 或 `MinerU-HTML` 模型。
- 不修改本地 MinerU 调用流程。
- 不调用 Agent 轻量解析 API。
- 不将真实官方服务集成测试纳入默认测试流程，避免普通构建意外消耗账号解析额度。
- 不在完整链路测试结束后自动清理 MySQL、MinIO、Milvus 或 Elasticsearch 中的测试数据。

## 4. 方案选择

采用在 `OfficialMinerUClient` 内封装完整官方调用流程的方案。

该方案保持现有客户端接口与上层解析流程稳定，改动范围最小。拆分多个 Spring Bean 会在当前单一调用方场景下增加装配复杂度；将官方异步任务提升为工作流状态则会改变现有同步解析合同，超出本次范围。

## 5. 配置约定

沿用现有配置项：

```yaml
nexa:
  parser:
    mineru:
      mode: official
      official-endpoint: https://mineru.net
      api-key: ${MINERU_API_KEY:}
      connect-timeout: 3s
      read-timeout: 120s
      poll-interval: 2s
      max-poll-count: 60
```

- `official-endpoint` 为空时默认使用 `https://mineru.net`。
- `official-endpoint` 末尾的 `/` 在拼接接口路径前移除。
- `api-key` 保存 MinerU API 管理页面创建的 Token，并通过 `Authorization: Bearer <Token>` 发送。
- 模型版本固定为 `vlm`。
- Token 仅允许通过环境变量或外部配置注入，仓库配置文件中不保存真实值。

## 6. 调用流程

### 6.1 申请签名上传地址

调用：

```http
POST /api/v4/file-urls/batch
Authorization: Bearer <Token>
Content-Type: application/json
```

请求体只包含一个文件：

```json
{
  "files": [
    {
      "name": "document.pdf",
      "data_id": "document-123"
    }
  ],
  "model_version": "vlm"
}
```

`data_id` 使用文档 ID 生成稳定业务标识。客户端校验 HTTP 状态、业务 `code`、`batch_id` 以及首个 `file_url`。任何字段缺失均视为官方协议异常。

### 6.2 上传原始文件

使用申请接口返回的签名 URL 发起 PUT 请求：

```http
PUT <file_url>
Content-Type: application/octet-stream
```

上传请求不携带 MinerU Bearer Token。为兼容当前 `InputStream` 合同并支持后续 HTTP 请求，客户端在进入调用流程时读取一次文件字节，上传时复用该字节数组。

### 6.3 轮询批次结果

调用：

```http
GET /api/v4/extract-results/batch/{batch_id}
Authorization: Bearer <Token>
```

客户端按 `poll-interval` 轮询，最多执行 `max-poll-count` 次：

- `done`：读取 `full_zip_url` 并进入结果下载。
- `failed`：使用官方 `err_msg` 构造远程服务异常。
- `pending`、`running` 等非终态：等待下一轮。
- 达到最大次数仍未完成：抛出轮询超时异常。

批次请求只有一个文件，因此客户端读取 `extract_result` 的首个结果。数组为空或状态字段缺失时视为官方协议异常。

### 6.4 下载 ZIP 产物

客户端通过 `full_zip_url` 下载 ZIP 字节，不携带 Bearer Token。HTTP 成功且响应体非空后，将字节封装为 `ByteArrayInputStream`，构造现有 `MinerUParseResponse`。

返回元数据仅包含：

- `clientMode=official`
- `batchId`
- `pollCount`
- `zipSize`

## 7. 错误处理

所有官方调用异常统一转换为 `ServiceException`，并使用远程服务错误码。错误信息使用简体中文，至少包含 `documentId` 和失败阶段，便于定位：

- API Key 为空。
- 申请上传地址失败。
- 上传原始文件失败。
- 查询解析结果失败。
- 官方任务返回失败状态。
- 下载 ZIP 失败。
- 轮询次数耗尽。
- 官方响应 JSON 无法解析或关键字段缺失。

HTTP 响应内容只保留有限长度用于诊断，且不得拼接请求 Header、Token 或签名上传地址。签名 URL 也不写入日志和异常信息。

线程中断时恢复中断标记，并终止轮询，避免吞掉中断信号。

## 8. 代码结构

主要修改：

- `OfficialMinerUClient`：实现申请上传地址、文件上传、结果轮询和 ZIP 下载。
- `MinerUProperties`：保持字段不变，仅确保官方地址空值具备默认回退语义。
- `application.yml`：补充官方默认地址和环境变量形式的 Token 占位，不写入真实 Token。
- `OfficialMinerUClientTest`：使用本地 `HttpServer` 覆盖完整交互和异常场景。
- `application-integration.yml`：增加显式启用的官方 MinerU 配置和完整链路测试参数。
- 完整 Workflow 集成测试：通过随机端口启动真实应用并使用 HTTP multipart 上传文件，验证最终入库结果。

如响应对象直接使用 `Map` 会导致字段访问分散，可在 `OfficialMinerUClient` 同包内增加仅服务于官方协议的 DTO。DTO 只描述本次使用的响应字段，不扩展未使用的官方协议内容。

## 9. 测试设计

默认测试采用本地 `HttpServer` 模拟 MinerU 官方服务，不访问互联网：

1. API Key 为空时在发送请求前失败。
2. 成功流程校验申请接口的 Bearer Header、文件名、`data_id` 和固定的 `vlm`。
3. 校验 PUT 请求体等于原文件字节，且不携带 Bearer Header。
4. 首次查询返回 `running`、后续返回 `done` 时能够下载并返回 ZIP。
5. 官方任务返回 `failed` 时抛出包含阶段和 `documentId` 的异常。
6. 达到最大轮询次数时抛出超时异常。
7. 申请、上传、查询或下载接口返回非成功 HTTP 状态时抛出远程服务异常。
8. 官方响应缺少 `batch_id`、`file_url`、结果项或 `full_zip_url` 时明确失败。
9. 返回 metadata 不包含 Token 和签名 URL。

测试按 TDD 顺序执行：先增加单一行为测试并确认预期失败，再实现最小代码使其通过，最后运行 MinerU 相关测试和 `nexa-rag-infra` 模块测试。

另外增加显式开启的真实官方客户端集成测试：

- 仅在指定开关和环境变量同时存在时执行，默认 Maven 测试必须跳过。
- Token 从 `MINERU_API_KEY` 环境变量读取，不写入源码、配置文件、测试参数、日志或异常信息。
- 使用 MinerU 官方示例 PDF URL 下载一份体积小、页数少的公开文件作为输入，避免提交用户私有文件。
- 测试完整覆盖申请上传地址、PUT 上传、异步轮询、ZIP 下载与 ZIP 产物基本校验。
- 单次测试只提交一个文件，允许消耗账号解析额度；失败后不自动重复提交新任务。
- 测试输出只记录任务阶段和最终结果，不输出 Token、Authorization Header、签名上传地址或完整下载地址。

## 10. 完整 Workflow 真实集成测试

### 10.1 测试入口

完整链路测试使用 `@SpringBootTest(webEnvironment = RANDOM_PORT)` 启动真实 Spring Boot 应用，通过 HTTP multipart 调用：

```http
POST /api/documents/upload
Content-Type: multipart/form-data
```

测试不得直接调用 `DocumentService`、`DocumentUploadService` 或 Workflow 内部服务代替上传入口。multipart 请求包含：

- `file`：从 MinerU 官方公开示例地址下载的小型 PDF 字节。
- `request`：文档标题、解析配置、切分配置和索引配置组成的 JSON part。

文档标题使用 `MinerU官方完整链路测试-<时间戳>`，确保多次执行时可以区分测试记录。上传成功后从统一响应中取得 `documentId`。

### 10.2 开启条件

完整链路测试默认跳过，仅在以下条件同时满足时执行：

- JVM 系统属性 `nexa.integration.workflow.enabled=true`。
- 环境变量 `MINERU_API_KEY` 非空。
- 集成环境的 MySQL、Redis、RocketMQ、MinIO、Milvus、Elasticsearch 和模型网关均可用。

`application-integration.yml` 为 MinerU 提供以下配置：

```yaml
nexa:
  parser:
    mineru:
      enabled: true
      mode: official
      official-endpoint: ${MINERU_OFFICIAL_ENDPOINT:https://mineru.net}
      api-key: ${MINERU_API_KEY:}
```

测试运行命令只引用环境变量名，不在 Maven 参数或命令历史中传递真实 Token。

### 10.3 状态轮询

HTTP 上传接口会自动提交文档流水线。测试通过以下接口轮询状态：

```http
GET /api/documents/{documentId}/process-status
```

轮询需要覆盖 RocketMQ Outbox 发布、消息消费、Workflow 执行、官方 MinerU 异步解析、切分和索引耗时，总等待上限设置为约 10 分钟：

- 状态达到 `INDEXED`：进入外部结果核验。
- 状态达到 `FAILED`：立即失败，并输出 `documentId`、失败阶段、失败原因和消息状态。
- 超过等待上限：失败，并输出最后一次文档状态和消息状态。

状态轮询日志只输出文档 ID、当前阶段和状态，不输出 Token、签名 URL 或官方结果下载地址。

### 10.4 入库核验

达到 `INDEXED` 后执行以下核验：

1. MySQL 中的文档状态为 `INDEXED`，流水线消息状态为 `COMPLETED`。
2. 文档的原文件对象名和解析后文件对象名均非空。
3. MinIO 中可以读取原始 PDF、解析后的 Markdown 以及 MinerU ZIP 中提取的资源文件；若该示例文件没有图片资源，则至少验证原文件和 Markdown。
4. MySQL 中存在非空文档分片；需要索引的分片状态均为 `INDEXED`，并具有向量 ID 和关键词索引 ID。
5. Milvus 中能够按照首个已索引分片的 `chunkId` 读取真实向量记录。
6. Elasticsearch 刷新索引后，按照 `documentId` 能够检索到至少一个分片，并可使用分片文本关键词命中结果。

### 10.5 数据保留

完整链路测试不执行自动清理。以下数据全部保留供人工查看：

- MySQL 文档、分片和消息记录。
- MinIO 原文件、Markdown 和图片等资源。
- Milvus 向量记录。
- Elasticsearch 关键词索引记录。

测试成功时输出文档 ID 和唯一标题，作为人工查看入口。测试失败时同样保留已生成的数据，方便定位失败阶段。

### 10.6 失败重试策略

真实完整链路测试单次只上传一个文件，不在测试方法内部自动创建多个新任务。首次失败后先根据文档失败阶段、RocketMQ 消息状态和外部服务响应定位并修复：

- 可恢复的已有文档优先通过系统重试入口继续处理。
- 只有原任务无法恢复或需要重新验证上传阶段时，才重新上传并创建新文档。
- 持续验证直到文档达到 `INDEXED` 且全部外部存储核验通过。

## 11. 验收标准

- 配置 `mode=official` 和有效 Token 后，现有文档解析入口能够取得官方 ZIP 产物。
- 官方请求固定使用 `model_version=vlm`。
- 上层 `MinerUDocumentParser` 无需针对官方模式增加分支。
- 所有新增注释、Java doc、日志和异常信息使用简体中文。
- Token、Authorization Header 和签名上传地址不会出现在日志、异常或 metadata 中。
- 本地 MinerU 模式现有测试保持通过。
- 官方客户端成功、失败、超时和协议异常测试全部通过。
- 显式开启真实集成测试后，能够通过官方服务完成一次小文件解析并取得有效 ZIP 产物。
- 显式开启完整 Workflow 测试后，能够通过真实 HTTP 上传接口创建文档，并最终达到 `INDEXED`。
- 完整 Workflow 测试能够验证 MySQL、MinIO、Milvus、Elasticsearch 和 RocketMQ 流水线结果。
- 完整 Workflow 测试产生的数据保留，且可通过测试输出的文档 ID 和唯一标题查看。
