# Prompt 统一管理体系设计

## 设计目标

本设计为会话 Workflow 建立全局 Prompt 的统一管理体系，替代 `PromptBuilder` 中硬编码的提示词正文。

系统需要支持：

- Prompt 在线编辑并在提交后立即生效；
- 不可变版本、发布历史与一键回滚；
- 按用户稳定命中的灰度发布；
- 多实例本地缓存的精准失效；
- 同一 Workflow 请求内各 Prompt 版本绑定稳定；
- Prompt 正文、变量契约、实际版本和模型调用链路可追溯。

首期范围限定为全局 Prompt，不支持租户、知识库或用户级覆盖。编辑提交即发布，不引入审核流和编辑、审核职责分离。

## 调研结论

`know-engine` 将提示词迁移至 classpath 文件并按业务 Key 永久缓存，适合作为本地文件种子和懒加载缓存的参考，但缺少版本、发布和跨实例刷新能力。

`ragent` 将 Prompt 模板加载、变量填充、上下文格式化和场景选择拆分，尤其适合借鉴“核心规则与上下文包装片段分离”的方式；其现有实现仍以 classpath 文件为主，数据库中的提示词字段也没有独立的 PromptOps 版本与发布模型。

因此本设计不采用文件或配置中心作为运行时事实源。数据库是唯一事实源；本地 Markdown 文件只承担初始种子模板和代码评审载体的职责。

## 架构与职责边界

```text
PromptBuilder
  -> PromptRenderService
      -> PromptExecutionSnapshot
      -> PromptReleaseResolver
          -> 本地有界缓存
          -> PromptRepository（数据库）
      -> MustacheRenderer

PromptPublishService
  -> PromptRepository
  -> Redis Pub/Sub 发布事件
```

职责划分如下：

- `PromptBuilder`：固定模型消息角色、顺序、安全边界、变量来源和长度裁剪；不保存 Prompt 正文。
- `PromptReleaseResolver`：在请求开始时按正式/灰度发布规则选择版本，生成请求级快照。
- `PromptRenderService`：按已绑定版本校验变量并渲染模板，返回渲染结果与版本追溯信息。
- `PromptPublishService`：处理编辑提交、发布记录、灰度调整、回滚和发布后刷新通知。
- `PromptRepository`：读取定义、版本和发布记录；数据库是唯一事实源。

消息角色、固定顺序和检索证据的安全边界属于 Workflow 协议，由代码控制；可在线编辑的是各位置实际使用的 Prompt 文本。这样既可在线优化规则文案，也不会因编辑错误改变消息语义或移除检索证据边界。

最终回答的固定消息结构示例：

```text
SYSTEM -> chat.answer.system-instruction
SYSTEM -> chat.answer.conversation-summary
历史 USER/ASSISTANT 消息
SYSTEM -> chat.answer.retrieval-evidence
USER   -> chat.answer.current-question
```

上述每个逻辑片段均可独立拥有版本，但其注入位置和消息角色不可由在线编辑改变。

## 数据模型

### Prompt 定义

`prompt_definition` 保存稳定身份和变量契约：

- `prompt_id`：主键；
- `prompt_code`：全局唯一业务标识，例如 `chat.answer.retrieval-evidence`；
- `name`、`description`、`scene`：管理展示和用途说明；
- `variable_schema`：允许与必填变量的 JSON 契约；
- `enabled`：是否启用；
- `current_release_id`、`current_release_revision`：当前发布指针与单调递增代次；
- 创建、更新时间和操作者信息。

变量契约由代码和定义共同维护，在线编辑不能引用未登记变量。

### Prompt 版本

`prompt_version` 保存不可变的文本版本：

- `version_id`、`prompt_id`、`version_no`；
- `content`：Mustache 模板正文；
- `content_checksum`：正文摘要，用于去重和审计；
- `variable_schema_snapshot`：创建时的变量契约快照；
- `created_by`、`created_at`、变更说明。

编辑提交不会更新旧正文，而是新增一条版本记录。首期没有审核状态；未来若需要审核，可在版本创建与发布之间增加状态，而无需改变版本、发布和缓存模型。

### 发布记录

`prompt_release` 追加记录每次生效决策：

- `release_id`、`prompt_id`、`release_revision`；
- `stable_version_id`：正式版本；
- `canary_version_id`：可空的灰度版本；
- `canary_rule`：灰度规则 JSON；
- `released_by`、`released_at`、发布说明、`rollback_from_release_id`。

回滚不是改写旧版本，而是新增发布记录，将正式版本或灰度版本指向某个历史版本。版本是否生效由发布记录决定，不在 `prompt_version` 上维护 `PUBLISHED` 状态。

### Redis Pub/Sub 刷新事件

Prompt 刷新复用现有模型注册表的 Redis Pub/Sub 刷新模式，不额外引入 MQ 或 Outbox。Redis Pub/Sub 只承担快速失效通知，不承担可靠投递。发布事务提交后，由事务后置回调发布 `PromptReleaseChangedMessage`；事件至少携带 `promptCode`、`releaseId` 和 `releaseRevision`，不携带完整 Prompt 正文。

发布实例在事务提交后先清除本机的当前发布快照缓存，再发送 Redis Pub/Sub 消息。其他实例收到消息后，按 `promptCode` 精确删除当前发布快照缓存。

每个实例维护已观察到的 Prompt 发布代次，并定时从数据库查询全部启用 Prompt 的当前发布代次。发现数据库代次高于本机记录时，删除对应当前发布快照缓存；下一次请求才懒加载新版 Prompt。该对账只读取轻量版本元数据，不读取 Prompt 正文、不会重渲染模板，也不在请求路径增加 I/O。Redis 订阅连接重连后立即触发一次对账。

Redis Pub/Sub 是轻量的瞬时通知，不提供离线消息堆积或消费确认；定时发布代次对账负责补偿漏收通知，使存活实例最终收敛。后续若需求升级，可在刷新消息客户端抽象下增加 RocketMQ 或 Redis Stream 适配，不影响 Prompt 发布、缓存和渲染接口。

## 编辑、发布、灰度和回滚

编辑提交即发布，事务流程如下：

```text
校验模板
  -> 创建 prompt_version
  -> 创建 prompt_release
  -> 更新 prompt_definition 当前发布指针与发布代次
  -> 提交事务
  -> 清除本机缓存并发布 Redis Pub/Sub 刷新事件
```

灰度首期支持用户百分比规则：

```json
{
  "type": "PERCENTAGE",
  "percentage": 10,
  "subject": "USER_ID"
}
```

命中使用 `hash(promptCode + releaseRevision + userId) % 10000` 计算。相同用户在同一发布代次内稳定命中同一版本；发布新代次或回滚后才重新计算。

发布采用最终一致性：发布事务完成后通过 Redis Pub/Sub 使所有实例快速删除本地当前发布缓存。由于事件传播存在极短窗口，发布接口返回后个别实例可能仍用旧缓存处理一次新请求。若实例在订阅连接异常期间漏收通知，发布代次定时对账会发现差异并删除旧缓存，使存活实例最终收敛；监听容器重连后必须立即执行一次对账。应用重启时本地缓存也天然为空。系统不在每个请求上读取中心化发布代次，也不等待所有实例确认，以换取无额外 I/O 的稳定读路径。

## 缓存策略

Prompt 变更频率低、读取频率高，本地缓存不设置时间过期。缓存只在发布事件到达时精准失效，并设置最大容量或最大权重作为 JVM 内存上限。

缓存分为两类：

- 当前发布快照缓存：键为 `promptCode`，值为正式/灰度发布规则及其版本快照。收到发布事件后删除该键。
- 指定版本快照缓存：键为 `promptCode + versionId`，值为不可变正文、变量契约和已编译模板。旧版本可保留到 LRU 淘汰。

消费者以 `releaseRevision` 幂等处理事件，记录已处理最大代次并忽略乱序或过期事件。应用重启时本地缓存天然为空；后续未命中从数据库懒加载并回填。

缓存只保存原始模板、编译结果和轻量元数据，不缓存含会话历史、检索证据的渲染后完整 Prompt。中文模板在 Java 9+ 通常以 UTF-16 字节数组存储，正文占用约为每字符两个字节并附加对象开销；全局 Prompt 数量有限时，原始文本与编译对象通常仅占用低个位数 MB。仍需使用有界缓存防止未来规模增长。

## 模板与变量

模板正文使用 Mustache 语法。每个定义预先登记允许与必填变量，例如：

```text
chat.rewrite.instruction
  conversationSummary、recentMessages、question

chat.answer.retrieval-evidence
  evidence
```

保存时执行以下静态校验：

1. 解析 Mustache 语法；
2. 提取变量并禁止引用未登记变量；
3. 校验必填变量和模板大小；
4. 校验变量名格式与逻辑片段类型；
5. 计算内容摘要，避免重复正文创建无意义版本。

渲染时再次校验必填变量；缺失时直接失败，不以空字符串静默替代。Prompt 为模型纯文本，变量插值使用原文输出，不进行 HTML 转义。

编辑页提供变量说明和服务端脱敏样例预览。预览只渲染模板，不调用模型；提交成功后返回新增版本号和发布代次。

## 请求级版本快照与追溯

`ChatWorkflowRunner` 在 Workflow 启动时，为本次会话流程涉及的所有 Prompt 解析版本绑定，生成 `PromptExecutionSnapshot`。该快照只保存轻量映射：

```text
chat.rewrite.instruction       -> versionId=101
chat.intent.instruction        -> versionId=205
chat.answer.system-instruction -> versionId=318
chat.answer.retrieval-evidence -> versionId=319
chat.answer.current-question   -> versionId=320
```

不同 Prompt 的版本号可以不同；稳定性要求是同一次 Workflow 内每个 Prompt 的绑定不变。若执行期间发布了某个 Prompt 的新版本，已启动 Workflow 仍按其初始绑定渲染，新启动 Workflow 才按新的发布记录绑定。

快照不保存渲染后的 Prompt、会话历史或检索证据。节点按 `versionId` 获取模板并渲染；版本永久保留，因此即使已不再发布，也能在缓存未命中时从数据库重新读取。异步摘要是新的模型任务，应在任务启动时重新解析当时发布版本。

每次模型调用日志和链路追踪必须记录实际使用的 `promptCode`、`versionId`、`releaseId` 与 `releaseRevision`，以支持复现、灰度效果分析和故障定位。

## 代码演进与验证

现有 `PromptTemplateService.render(key, variables)` 演进为按 `PromptExecutionSnapshot` 中已绑定版本渲染的接口。现有 `ChatWorkflowPromptBuilder` 更名为 `PromptBuilder`。classpath Markdown 模板迁移为数据库初始化种子；运行时不再由资源文件作为事实源。

`PromptBuilder` 保留消息组装职责，移除硬编码 Prompt 正文；所有规则文本、摘要包装、检索证据包装和问题包装均迁移为独立 Prompt 定义。

验证范围包括：

- 模板语法、变量白名单、必填变量和原文插值校验；
- 编辑提交后创建新版本、发布记录和 Redis Pub/Sub 刷新事件；
- 灰度用户在同一发布代次内稳定命中；
- 发布与回滚的精准缓存失效、事件幂等与乱序处理、漏收事件后的发布代次对账；
- 模型消息角色、顺序和检索证据安全边界保持固定；
- 同一 Workflow 内各 Prompt 的版本绑定稳定，新 Workflow 使用新发布版本；
- 模型调用日志可准确定位实际版本。
