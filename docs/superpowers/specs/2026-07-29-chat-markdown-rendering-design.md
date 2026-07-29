# 对话回答 Markdown 渲染设计

## 1. 背景与目标

当前对话工作台的助手消息使用普通文本节点输出，模型返回的 Markdown 标记会直接显示。需要将助手回答安全地渲染为 Markdown，并支持公式、代码高亮和 Mermaid 图表，同时保持 SSE 流式回答稳定。

本次目标：

1. 支持标题、段落、强调、列表、引用、链接、任务列表、删除线和表格。
2. 支持行内和块级公式，兼容 `$...$`、`$$...$$`、`\\(...\\)`、`\\[...\\]` 四种定界符。
3. 支持带语言标识的代码块、高亮与复制。
4. 支持以 `mermaid` 标识的围栏代码块渲染流程图、时序图等图表。
5. 不解析模型返回的原始 HTML，避免 XSS。

不在本次范围内：富文本编辑、用户消息 Markdown 输入、图表编辑、服务端将 Markdown 转 HTML。

## 2. 方案选择

采用 React 原生渲染方案：`react-markdown` 作为解析入口，配合 `remark-gfm`、`remark-math`、`rehype-katex` 和 `rehype-highlight`；Mermaid 通过自定义代码块组件按需渲染。

相比“解析为 HTML 后注入页面”的方案，该方案默认忽略原始 HTML，组件边界与 React 一致；相比后端转 HTML，前端可以独立调整展示效果，且不会把展示职责耦合进对话接口。

## 3. 组件与数据流

新增以下前端组件，均位于 `features/chat/components`：

1. `AssistantMarkdown`：助手消息的唯一 Markdown 渲染入口，接收回答文本和消息状态。
2. `MarkdownCodeBlock`：渲染普通代码块，展示语言标签、语法高亮和复制按钮。
3. `MermaidDiagram`：渲染 `language-mermaid` 代码块，负责 Mermaid 初始化、错误降级和资源清理。

`ChatWorkspace` 的 `MessageBubble` 保持用户消息的纯文本输出。只有 `role === 'ASSISTANT'` 时改用 `AssistantMarkdown`。历史消息和 SSE `TOKEN` 累积后的消息内容共用同一个组件，无需改变后端接口或 SSE 事件格式。

```text
SSE TOKEN / 历史消息
        ↓
助手 message.content
        ↓
AssistantMarkdown
  ├─ GFM、数学、代码高亮
  └─ Mermaid 自定义代码块
```

## 4. 渲染规则

1. GFM 启用表格、任务列表、删除线和自动链接。
2. 公式使用 KaTeX 渲染。进入 Markdown 解析前，定界符规范化模块仅在非围栏代码块、非行内代码区域将 `\\(...\\)`、`\\[...\\]` 转换为等价数学节点，避免误改示例代码。
3. 普通代码块使用声明的语言高亮；未声明或不受支持的语言按纯文本展示。复制失败时显示可见的失败提示。
4. Mermaid 在围栏代码块闭合且助手消息完成后初始化，生成中仍显示原始代码，避免每个 TOKEN 都重建图表。语法或初始化失败时回退为原始 Mermaid 代码块，并显示“图表渲染失败”。
5. 外部链接新窗口打开时添加 `rel="noreferrer noopener"`，不放宽原始 HTML 解析。

## 5. 安全与性能

1. 不引入 `rehype-raw`，模型输出中的 HTML 标签按文本处理。
2. Mermaid 使用严格安全级别，禁止图表内容执行任意脚本和不受控交互。
3. 对 `AssistantMarkdown` 使用 `memo`，并将 Mermaid 初始化限制到消息完成后；SSE 每个 TOKEN 仍可更新文字、列表和已闭合的普通代码块，不触发 Mermaid 重绘。
4. KaTeX 或 Mermaid 解析异常只能影响对应片段，不能导致整条回答或流式会话失败。

## 6. 依赖与样式

新增 `react-markdown`、`remark-gfm`、`remark-math`、`rehype-katex`、`rehype-highlight`、`katex`、`mermaid` 及其必要类型定义。样式复用当前 Tailwind 视觉体系，并补充：标题层级、引用块、表格滚动容器、行内代码、深色代码块、KaTeX 溢出滚动和 Mermaid 容器。

## 7. 验收与测试

1. 为 Markdown 组件覆盖标题、粗体、列表、表格、链接、代码块和四种公式定界符。
2. 为 Mermaid 覆盖完成态渲染、生成态原文展示、异常降级三种状态。
3. 覆盖原始 HTML 不被执行、危险链接属性受限、复制按钮成功与失败提示。
4. 在 `ChatWorkspace` 测试 SSE 逐步追加内容，确认助手消息最终按 Markdown 呈现，且不会影响既有停止生成和断线恢复逻辑。
5. 运行相关 Vitest 测试与前端构建；若现有知识库页面的 TypeScript 错误仍存在，单独记录为工作区既有阻塞项。
