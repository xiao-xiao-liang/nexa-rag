# 告警 HTML 邮件模板与真实发送测试设计

## 目标

将邮件告警由纯文本升级为结构化 HTML 邮件，并提供只在显式开启后可用的测试接口，便于在本地使用真实飞书 Webhook 与 SMTP 配置验证投递效果。

## 范围与边界

- 邮件渠道使用 `MimeMessage` 发送 UTF-8 HTML 正文；告警字段必须 HTML 转义。
- 模板展示告警级别、任务类型、文档与父任务 ID、失败原因、失败时间和重试次数；严重级别决定标题色带颜色。
- 飞书渠道使用 `interactive` 动态卡片协议；严重级别决定红色或橙色标题，动态字段进行 Markdown 转义。
- 测试接口复用真实渠道发送器，分别发送飞书或邮件测试消息；不写入 Outbox，也不触发重试或死信状态变更。
- 接口与告警能力共用 `nexa.alert.enabled` 开关；响应不返回 Webhook、邮箱地址、授权码或异常原文。

## 接口

`POST /api/alert-tests/{channel}`

- `channel`：`feishu` 或 `email`，大小写不敏感。
- 成功时返回无敏感信息的成功响应。
- 渠道不存在或未启用时返回明确业务错误。
- 测试消息使用固定的非业务 ID，并含“测试告警”标记，方便与真实失败告警区分。

## 实现结构

- `EmailAlertHtmlTemplate`：只负责将 `AlertMessage` 渲染为 HTML 字符串和邮件主题。
- `FeishuAlertCardTemplate`：只负责将 `AlertMessage` 渲染为飞书动态卡片结构。
- `EmailAlertChannelSender`：负责配置校验与 MIME 邮件投递。
- `AlertTestService`：构造测试告警并委托对应 `AlertChannelSender`。
- `AlertTestController`：仅接收渠道参数并调用服务；通过告警总开关控制 Bean 注册。

## 验证

- 单元测试验证 HTML 正文包含核心字段、严重级别样式以及特殊字符转义。
- 单元测试验证测试服务按渠道选择对应发送器，并拒绝未知渠道。
- 当 `nexa.alert.enabled=true` 时，启动本地应用后分别请求 `POST /api/alert-tests/feishu` 和 `POST /api/alert-tests/email`，人工确认群消息与收件箱效果。
