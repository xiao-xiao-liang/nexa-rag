# 飞书 CLI 优先文档读取设计

## 1. 目标

将飞书单篇 Docx/Wiki 文档的正文读取主路径切换为飞书 CLI 的 Markdown 导出能力，以保留 CLI 对画板等扩展块的引用标记；当 CLI 不可用或导出异常时，继续使用现有飞书 OpenAPI Block API，保证文档流水线可用。

## 2. 设计决策

- 飞书来源仍由 `FeishuDocxSourceReader` 对外实现 `ExternalDocumentSourceReader`，不改变来源路由和工作流接口。
- 新增 `FeishuCliDocumentExporter`，仅负责安全启动 CLI、限制超时、解析 JSON 输出并生成 `SourceReadResultBO`。
- CLI 为优先路径：执行 `lark-cli --profile <profile> --as bot docs +fetch --doc <url> --doc-format markdown --format json`。
- OpenAPI Block API 退回为 Reader 的私有降级路径，继续处理 Wiki 解析、revision 固定、分页和 `FeishuBlockMarkdownConverter`。
- CLI 成功时，完整 JSON 输出作为来源快照写入 MinIO；规范化 Markdown 继续写入既有 parsed artifact 位置。
- CLI 显式返回资源权限拒绝时，直接抛出不可重试异常；其他启动、超时、退出码、协议解析和临时 API 失败均可降级 OpenAPI。
- 使用 `CloudDocumentProperties` 统一管理飞书和语雀配置；飞书 CLI 子配置位于 `CloudDocumentProperties.FeishuProperties.CliProperties`。

## 3. CLI 可执行文件解析

1. `nexa.cloud-document.feishu.cli.executable` 非空时，使用其绝对路径并校验可执行性。
2. 未配置时，使用命令名 `lark-cli`，由服务进程的 `PATH` 解析。
3. 两者都不可用时，记录含 documentId 的告警并进入 OpenAPI 降级；不硬编码任何开发机或 Node 全局目录。

## 4. 配置

```yaml
nexa.cloud-document.feishu.cli:
  enabled: true
  executable: ${NEXA_SOURCE_FEISHU_CLI_EXECUTABLE:}
  profile: ${NEXA_SOURCE_FEISHU_CLI_PROFILE:nexarag}
  timeout: 60s
```

`profile` 为空时不传 `--profile` 参数。部署账号必须提前初始化应用身份 Profile，且命令固定使用 `--as bot`，不触发用户 OAuth 扫码。

## 5. 错误与可观测性

- 正常 CLI 成功日志记录 documentId、CLI Profile、导出 documentId、revisionId 与耗时，不记录内容和凭据。
- CLI 降级日志记录 documentId、失败类型、退出码或超时信息；随后 OpenAPI 成功时记录已降级成功。
- CLI 与 OpenAPI 都失败时，保留 OpenAPI 的现有错误分类，以便文档流水线正确进入重试或失败主题。

## 6. 非目标

- 本次不下载或 OCR 画板、图片、附件，不将 `<whiteboard>` 转成图片 Markdown。
- 本次不删除 `FeishuBlockMarkdownConverter`；它是明确的可用性降级实现。
- 本次不改变外部来源数据库字段、MinIO 制品路径、切分和索引流程。

## 7. 验收标准

1. 以 bot Profile 导入 Docx 或 Wiki 时，MinIO 快照为 CLI JSON，解析产物为 CLI Markdown。
2. CLI 可执行文件不存在、超时或失败时，同一导入能自动调用 OpenAPI Block API。
3. 显式配置的 CLI 路径优先于 `PATH`，并且不依赖开发机特定路径。
4. 配置类重命名后，配置绑定与注入点保持一致。
5. 项目文档提供应用权限、资源授权、CLI 安装、Profile 初始化、验证与故障排查的可复现步骤。
