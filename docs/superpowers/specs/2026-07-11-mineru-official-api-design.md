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

## 3. 非目标

- 不改造 `DocumentParser` 为跨节点异步任务。
- 不持久化 MinerU 的 `batch_id`，不提供服务重启后的任务恢复。
- 不支持切换 `pipeline` 或 `MinerU-HTML` 模型。
- 不修改本地 MinerU 调用流程。
- 不调用 Agent 轻量解析 API。
- 不将真实官方服务集成测试纳入默认测试流程，避免普通构建意外消耗账号解析额度。

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

另外增加显式开启的真实官方服务集成测试：

- 仅在指定开关和环境变量同时存在时执行，默认 Maven 测试必须跳过。
- Token 从 `MINERU_API_KEY` 环境变量读取，不写入源码、配置文件、测试参数、日志或异常信息。
- 使用 MinerU 官方示例 PDF URL 下载一份体积小、页数少的公开文件作为输入，避免提交用户私有文件。
- 测试完整覆盖申请上传地址、PUT 上传、异步轮询、ZIP 下载与 ZIP 产物基本校验。
- 单次测试只提交一个文件，允许消耗账号解析额度；失败后不自动重复提交新任务。
- 测试输出只记录任务阶段和最终结果，不输出 Token、Authorization Header、签名上传地址或完整下载地址。

## 10. 验收标准

- 配置 `mode=official` 和有效 Token 后，现有文档解析入口能够取得官方 ZIP 产物。
- 官方请求固定使用 `model_version=vlm`。
- 上层 `MinerUDocumentParser` 无需针对官方模式增加分支。
- 所有新增注释、Java doc、日志和异常信息使用简体中文。
- Token、Authorization Header 和签名上传地址不会出现在日志、异常或 metadata 中。
- 本地 MinerU 模式现有测试保持通过。
- 官方客户端成功、失败、超时和协议异常测试全部通过。
- 显式开启真实集成测试后，能够通过官方服务完成一次小文件解析并取得有效 ZIP 产物。
