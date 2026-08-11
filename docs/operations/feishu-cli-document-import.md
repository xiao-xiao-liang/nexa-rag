# 飞书 CLI 文档导入配置指南

本文说明 NexaRAG 通过飞书 CLI 读取飞书云文档和 Wiki 文档的部署方式、权限要求及常见故障处理。系统以应用身份（bot）调用 CLI，不使用用户扫码授权。

## 1. 适用范围与工作方式

飞书来源的读取顺序如下：

```text
FeishuCliDocumentExporter
  → lark-cli --profile <profile> --as bot docs +fetch
  → 导出的 Markdown 与 CLI JSON 快照写入 MinIO
  → CLI 不可用或导出失败时，降级到飞书 OpenAPI Block API
```

CLI 导出的 Markdown 会保留例如 `<whiteboard token="..."></whiteboard>` 的扩展标记。它们先作为解析产物的一部分保存；画板图片、附件等媒体的下载是后续独立步骤。

> 不要使用 `--as user` 作为服务端导入身份。用户 OAuth 令牌需要授权及轮换，不适合后台任务。

## 2. 配置飞书应用

1. 进入[飞书开放平台应用管理](https://open.feishu.cn/app)，创建或打开 NexaRAG 自建应用。
2. 在[凭证与基础信息](https://open.feishu.cn/document/ukTMukTMukTM/uMTNz4yM1MjLzUzM)获取 App ID 与 App Secret。
3. 在[权限管理说明](https://open.feishu.cn/document/home/introduction-to-scope-and-permissions)中开通文档导入需要的应用身份权限。至少应包括：
   - 查看云文档内容：`docs:document.content:read`；
   - 下载云文档中的图片和附件：`docs:document.media:download`；
   - 导出云文档：`drive:export:readonly`；
   - 查看知识库：`wiki:wiki:readonly`。
4. 在[应用版本管理](https://open.feishu.cn/app)中创建版本并发布，使权限在目标租户中生效。
5. 打开目标云文档或知识库，在“文档权限”或“成员管理”中，将该应用加入可访问成员范围。

应用权限只代表“允许调用 API”；它不自动获得任意文档的内容权限。未给应用资源权限时，常见响应是 `131006` 或 `node permission denied`。

## 3. 安装 CLI

运行 NexaRAG 的操作系统账号必须安装 [Node.js](https://nodejs.org/) 与[飞书 CLI 安装指南](https://open.feishu.cn/document/no_class/mcp-archive/feishu-cli-installation-guide)中的 CLI。Windows PowerShell 示例：

```powershell
npm install -g @larksuite/cli # https://www.npmjs.com/package/@larksuite/cli
lark-cli --version
```

若 `lark-cli` 不在 `PATH`，可使用其绝对路径；例如 Node 全局安装目录中的 `lark-cli.exe`。不要将某个开发者机器的绝对路径写死进代码或提交到仓库。

可按需更新：

```powershell
lark-cli update
```

## 4. 初始化 NexaRAG 应用 Profile

以下命令只需在每个运行账号下执行一次。它创建独立的 `nexarag` Profile，不影响已有的个人 Profile。

```powershell
$larkCli = 'lark-cli'
$env:NEXARAG_LARK_APP_SECRET = Read-Host '请输入 NexaRAG App Secret'
$env:NEXARAG_LARK_APP_SECRET | & $larkCli config init `
  --app-id 'cli_xxx' `
  --app-secret-stdin `
  --brand feishu `
  --name nexarag
Remove-Item Env:\NEXARAG_LARK_APP_SECRET

& $larkCli --profile nexarag config default-as bot
& $larkCli --profile nexarag config strict-mode bot
& $larkCli --profile nexarag whoami --as bot
```

`whoami` 应显示 `identity=bot` 与 `tokenStatus=ready`。应用身份令牌会自动获取和刷新，日常导入不会触发扫码。

## 5. 验证文档读取

```powershell
lark-cli --profile nexarag docs +fetch `
  --as bot `
  --doc 'https://<tenant>.feishu.cn/wiki/<token>' `
  --doc-format markdown `
  --format json
```

返回中的 `ok` 必须为 `true`，并应包含 `data.document.content`、`document_id` 与 `revision_id`。Wiki URL 由 CLI 自动解析，无需在前端区分 Wiki 与 Docx。

## 6. NexaRAG 配置

在 `application.yml` 配置 CLI 路径、Profile 和超时。`executable` 留空时，程序只从服务进程的 `PATH` 查找 `lark-cli`；显式配置的路径优先级最高。

```yaml
nexa:
  source:
    feishu:
      app-id: ${NEXA_SOURCE_FEISHU_APP_ID:}
      app-secret: ${NEXA_SOURCE_FEISHU_APP_SECRET:}
      cli:
        enabled: true
        executable: ${NEXA_SOURCE_FEISHU_CLI_EXECUTABLE:}
        profile: ${NEXA_SOURCE_FEISHU_CLI_PROFILE:nexarag}
        timeout: 60s
```

生产环境运行服务的账号通常不同于开发者登录账号，因此必须在该服务账号下初始化 `nexarag` Profile，或将该账号的 CLI 配置安全地挂载到运行环境。

## 7. 常见故障

| 现象 | 原因与处理 |
| --- | --- |
| `lark-cli` 找不到 | 配置 `nexa.source.feishu.cli.executable`，或将 CLI 安装目录加入服务进程的 `PATH`。 |
| `identity` 不是 `bot` | 执行 `lark-cli --profile nexarag config default-as bot`，并检查严格模式。 |
| `131006` / `node permission denied` | 应用没有目标文档或 Wiki 节点的资源权限；将应用加入文档/知识库成员。 |
| 权限已开通仍然失败 | 确认应用版本已发布且租户管理员已生效；重新执行 `whoami --as bot` 与手工 `docs +fetch`。 |
| CLI 导出失败但导入仍可继续 | 这是 OpenAPI Block API 降级路径生效。检查服务日志中的 CLI 退出码、标准错误与超时原因。 |
| 内容没有画板图 | Markdown 仅保留 `whiteboard token`。画板媒体下载需使用 `docs +media-download --type whiteboard` 的后续处理。 |

## 8. 安全边界

- App Secret 不要作为命令行参数传入；使用 `--app-secret-stdin`，避免出现在进程参数列表。
- 不要提交 `.lark-cli` 配置、App Secret、用户 OAuth 令牌或导出的私人文档。
- CLI Profile 仅提供运行环境的应用身份；NexaRAG 的业务代码仍不读取或持久化用户个人授权令牌。
