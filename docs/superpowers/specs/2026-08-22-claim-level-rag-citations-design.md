# 结论级 RAG 引用设计

## 目标与范围

在知识库回答中，为每个可核验结论展示可点击的 `[1]`、`[2]`。点击后，用户在回答正文附近的弹层中查看当前可访问的文档分块摘录、文档标题和章节，并可通过服务端授权动作打开原文档。

本期只覆盖知识库检索证据，不改变回答的 Markdown 主体、会话历史分页、工具运行卡、知识库权限模型或文档切分规则。引用不保存正文快照，也不支持跨消息检索、统计和审计分析。

## 已验证事实

- `RetrievalChunk` 已提供 `chunkId`、`documentId`、`chunkIndex`、`parentChunkId`、标题、来源、正文、通道和分数。
- 回答节点只把 `acceptedEvidenceResults` 作为证据正文传给模型，故它是唯一可生成用户引用的来源集合。
- `chat_message.references_json`、`ChatMessage.referencesJson` 和 `ChatMessageVO.referencesJson` 已存在，但助手最终化节点当前传入 `null`，历史前端也未渲染引用。
- SSE 已具备带版本号的 Redis 缓冲和重放能力；现有事件类型、前端 TypeScript 联合类型和 Markdown 渲染器可扩展。

## 领域模型

### 引用清单存储模型

每条助手消息的 `references_json` 保存下列版本化结构。它是服务端内部定位信息，不能原样作为公开接口响应。

```json
{
  "version": 1,
  "citations": [
    {
      "citationId": 1,
      "knowledgeBaseId": 1001,
      "documentId": 2001,
      "chunkId": "chunk-2001-003",
      "chunkOrder": 3,
      "title": "费用报销管理制度",
      "sectionId": 301,
      "rank": 1,
      "score": 0.92,
      "channel": "hybrid"
    }
  ]
}
```

`citationId` 仅在同一 `messageId` 内有效，从 `1` 按 `acceptedEvidenceResults` 的稳定顺序分配。正文、原始文件 URL、对象名和外部 URL 不进入此 JSON。无法解析、版本不支持或为空的历史值均按无引用处理。

### 公开引用投影

SSE 和历史消息只向前端提供足以匹配 Markdown 编号的最小投影：

```json
{
  "citationId": 1
}
```

文档标题、分块正文、文档或分块标识均由引用详情接口在点击时按当前权限返回。

## 生成与持久化流程

1. `EvidenceQualityNode` 输出的 `acceptedEvidenceResults` 被映射为不可变 `CitationSet`，并按其顺序编号。
2. `AnswerGenerationNode` 为每段证据增加编号提示，例如 `【证据 1】`，同时将同一 `CitationSet` 置入工作流状态。
3. 服务端在首个回答正文 token 前发布 `CITATIONS` SSE 事件，事件版本与其他事件相同，由 Redis 缓冲；之后开始原有 Markdown token 流。
4. 模型在同一条 Markdown 流内为可核验结论输出 `[n]`。无可用证据编号的具体结论输出 `【未提供引用】`。
5. 最终化节点解析正文中出现的 `[n]`。编号只有在当前 `CitationSet` 中存在才视为有效；无效编号在渲染投影中降级为“未提供引用”，并记录可观测告警。服务端不尝试从无编号句子中推断结论或补造来源。
6. `COMPLETED`、`CANCELLED` 和 `FAILED` 都持久化同一 `CitationSet` 至 `references_json`。空正文不展示引用入口；保留正文中的有效编号仍可查看。
7. `COMPLETE`、`CANCELLED`、`ERROR` 终态事件重带公开引用投影，历史消息读取返回相同投影，保证实时、重放和历史显示一致。

## 提示词变更

在原有角色、证据优先、证据不足、冲突处理和提示注入防护要求之后，增加以下输出约束：

```text
每个由检索证据直接支持或可明确推出的具体结论，必须紧随一个或多个证据编号 [n]。
只能使用本轮证据中已提供的编号，不得编造编号、文档标题、URL 或来源。
若某个具体结论无法提供证据编号，保留该结论并紧随【未提供引用】。
不要输出独立“参考来源”列表，不要在代码块、链接文本或普通方括号中模拟引用编号。
```

提示词不是安全边界：服务端仍负责编号范围校验和前端安全渲染。

## SSE 与历史接口契约

### `CITATIONS` SSE 事件

事件在首个 `ANSWER_DELTA` 或 `TOKEN` 前发出：

```json
{
  "type": "CITATIONS",
  "conversationId": "c1",
  "generationId": "g1",
  "messageId": "m2",
  "eventVersion": 3,
  "citations": [{ "citationId": 1 }, { "citationId": 2 }]
}
```

终态事件携带同一 `citations` 字段，供前端最终对账。事件重放沿用现有 `eventVersion` 去重规则。

### 历史消息投影

`GET /api/conversations/{conversationId}/messages` 的助手消息新增：

```json
{
  "messageId": "m2",
  "content": "报销需先经直属主管审批。[1]",
  "citations": [{ "citationId": 1 }]
}
```

不再将原始 `referencesJson` 作为前端契约；兼容期间可保留该字段但前端不得依赖它。

### 引用详情接口

```text
GET /api/chat/messages/{messageId}/citations/{citationId}
```

接口以当前登录用户为权限主体，查询助手消息内的 `CitationSet` 并重新校验知识库、文档和分块是否仍可访问。成功响应包含当前可展示的标题、章节、分块摘录和授权导航动作；不提供任何内部对象名或未授权 URL。

```json
{
  "status": "AVAILABLE",
  "title": "费用报销管理制度",
  "sectionTitle": "第三章 报销流程",
  "excerpt": "员工提交申请后，由直属主管审批，再进入财务复核。",
  "openDocument": { "type": "IN_APP_PREVIEW", "target": "/documents/2001" }
}
```

不可用统一以成功业务响应表示：`EXPIRED` 表示文档或分块不再存在，`FORBIDDEN` 表示当前用户已无访问权限。站内上传文档使用站内预览；外部来源文档由服务端返回受控跳转动作。两类场景均不得让浏览器直接读取数据库 URL。

## 前端渲染与交互

1. 前端在收到 `CITATIONS` 后，将该助手消息的合法 `citationId` 集合暂存到页面内存和会话历史缓存。
2. Markdown 渲染器在非代码块、非既有链接文本位置识别 `[n]`：仅当 `n` 存在于当前消息集合时，渲染为可访问的引用按钮；其他方括号文本保持普通 Markdown 文本。
3. `【未提供引用】` 和无效编号的渲染结果为不可点击的弱提示“未提供引用”。
4. 点击引用按钮后调用引用详情接口，在编号附近显示单一轻量弹层。弹层显示加载、可预览、已失效、无权查看和请求失败状态；再次点击其他编号替换弹层内容。
5. 弹层提供“打开原文档”动作。站内预览在当前产品路由中打开；外部来源由后端返回的受控地址跳转。
6. 取消、失败和历史消息走相同的 Markdown 与引用详情逻辑；无正文的助手消息不显示引用按钮或来源入口。

## 错误处理与安全不变量

- 只有 `acceptedEvidenceResults` 能进入 `CitationSet`，原始、融合、重排淘汰或章节导航候选均不得引用。
- 所有引用详情查询同时校验消息归属和当前知识库访问权限；消息拥有权不能替代文档权限。
- 任何无效 `[n]` 均不能成为链接、触发分块查询或泄露内部标识。
- 文档删除、重切分、索引重建或权限回收后，历史回答保留编号文本，但详情显示 `EXPIRED` 或 `FORBIDDEN`，不回退到正文快照。
- 单次流式回答不增加第二次模型调用、不要求模型输出 JSON，也不阻塞首 token 等待后处理。

## 验收与验证

### 后端

- `CitationSet` 仅由已接纳证据生成，排序和编号稳定；空证据集不发布可用引用。
- `CITATIONS` 严格早于首个正文事件，重放与终态事件的引用清单一致。
- 完成、取消、失败均正确持久化；旧 `references_json` 和空值兼容读取。
- 无效、重复、越界编号不会产生可点击公开引用。
- 引用详情正确区分消息无权、文档无权、文档/分块失效和可访问状态；禁止泄露 URL、对象名和分块正文给未授权用户。

### 前端

- 实时、断线恢复和历史消息都只让合法 `[n]` 可点击。
- 代码块、普通方括号、已有链接与 `【未提供引用】` 不被误渲染成引用。
- 弹层能展示加载、可用、已失效、无权和请求失败状态；键盘操作可触发和关闭。
- 工具运行卡、Markdown 图片、复制功能和历史分页不回归。

## 非目标

- 不建立引用正文快照、证据审计版本、跨消息引用分析或引用使用率统计。
- 不通过第二个模型调用、结构化 JSON 输出或启发式句子切分补齐遗漏引用。
- 不保证模型为每个结论都正确标注；协议只强制和校验可识别的标记。
