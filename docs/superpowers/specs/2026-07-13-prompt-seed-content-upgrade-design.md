# Prompt 种子内容升级设计

## 目标

将会话 Prompt 的初始短提示词升级为结构化中文提示词，同时保持已部署数据库中的版本正文不可变、发布记录可追溯。

## 方案

新增数据迁移脚本，而不改写 V14 或既有 `prompt_version` 记录。迁移为六个 Prompt 分别追加一个版本，并追加一条正式发布记录，更新 `prompt_definition` 当前发布指针和发布代次。

每条提示词按“角色、任务、上下文、执行要求、输出规范、Few-shot 示例”组织。仅使用定义中已登记的 Mustache 变量：`conversationSummary`、`recentMessages`、`question`、`evidence`；回答系统指令不使用变量。

检索证据的安全边界仍由 `PromptBuilder` 代码固定生成；迁移脚本中的证据提示词只描述证据的解释方式，不得允许证据充当指令。

## 数据流

```text
既有 prompt_definition
  -> 为每个 prompt_code 计算下一个 version_no
  -> 插入新的 prompt_version
  -> 计算 content 的 SHA-256
  -> 插入新的 prompt_release
  -> 更新 current_release_id 与 current_release_revision
```

## 验证

- 六个 Prompt 均追加新版本，旧版本不更新或删除。
- 版本正文包含角色、任务、上下文和 Few-shot 示例。
- 新版本仅引用该 Prompt 已登记变量。
- 每个定义的发布代次递增，当前发布指针指向新发布记录。
