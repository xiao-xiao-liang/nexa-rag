# 对话回答 Markdown 渲染实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将助手回答安全地渲染为支持 GFM、公式、代码高亮和 Mermaid 图表的 Markdown，并保持 SSE 流式输出可用。

**Architecture:** `MessageBubble` 仅对助手消息委托给 `AssistantMarkdown`。该组件组合 GFM、数学和代码高亮插件；Mermaid 作为独立代码块组件，仅在消息完成后初始化，失败时回退为原始代码。数学定界符在 Markdown 解析前通过无状态扫描器规范化，且跳过代码区域。

**Tech Stack:** React 19、TypeScript、Vite、Vitest、react-markdown、remark-gfm、remark-math、rehype-katex、rehype-highlight、KaTeX、Mermaid、Tailwind CSS。

---

## 文件结构

| 文件 | 职责 |
| --- | --- |
| `nexa-rag-front/package.json` | 声明 Markdown、数学、代码高亮和 Mermaid 依赖。 |
| `nexa-rag-front/package-lock.json` | 锁定新增依赖版本。 |
| `nexa-rag-front/src/styles/globals.css` | 引入 KaTeX 基础样式，提供回答 Markdown 的版式样式。 |
| `nexa-rag-front/src/features/chat/components/markdown-normalizer.ts` | 在非代码区域兼容 LaTeX 反斜杠定界符。 |
| `nexa-rag-front/src/features/chat/components/MarkdownCodeBlock.tsx` | 普通代码块的高亮、语言标签和复制反馈。 |
| `nexa-rag-front/src/features/chat/components/MermaidDiagram.tsx` | 严格模式 Mermaid 渲染、生成中展示与异常降级。 |
| `nexa-rag-front/src/features/chat/components/AssistantMarkdown.tsx` | 助手 Markdown 的唯一渲染入口与安全链接策略。 |
| `nexa-rag-front/src/features/chat/components/AssistantMarkdown.test.tsx` | Markdown、公式、安全边界与 Mermaid 行为测试。 |
| `nexa-rag-front/src/features/chat/ChatWorkspace.tsx` | 将助手消息接入 `AssistantMarkdown`，用户消息保持纯文本。 |
| `nexa-rag-front/src/App.test.tsx` | 验证聊天流最终使用 Markdown 输出，保留现有 SSE 回归用例。 |

### Task 1: 安装依赖并建立样式入口

**Files:**
- Modify: `nexa-rag-front/package.json`
- Modify: `nexa-rag-front/package-lock.json`
- Modify: `nexa-rag-front/src/styles/globals.css`

- [ ] **Step 1: 安装运行时依赖**

Run:

```powershell
cd E:\Code\Projects\MyProject\AI\nexa-rag\nexa-rag-front
npm install react-markdown remark-gfm remark-math rehype-katex rehype-highlight katex mermaid
```

Expected: `package.json` 和 `package-lock.json` 出现上述依赖，命令退出码为 0。

- [ ] **Step 2: 在全局样式引入 KaTeX 并增加可读性样式**

在 `globals.css` 顶部保留 CSS import 顺序，并加入：

```css
@import "katex/dist/katex.min.css";
@import "tailwindcss";

.assistant-markdown {
  overflow-wrap: anywhere;
}

.assistant-markdown table {
  display: block;
  max-width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
}
```

补全标题、引用、表格单元格、行内代码、代码块和 `.katex-display` 的间距与横向滚动样式，颜色使用现有 `--foreground`、`--muted`、`--border` 变量，不引入新的全局色板。

- [ ] **Step 3: 验证依赖与样式可解析**

Run:

```powershell
npm test -- App.test.tsx
```

Expected: 既有 11 个对话工作台测试仍通过；此步骤不应新增前端行为。

- [ ] **Step 4: 提交依赖变更**

```powershell
git add nexa-rag-front/package.json nexa-rag-front/package-lock.json nexa-rag-front/src/styles/globals.css
git commit -m "feat(markdown): 引入对话渲染依赖"
```

### Task 2: 实现并测试公式定界符规范化

**Files:**
- Create: `nexa-rag-front/src/features/chat/components/markdown-normalizer.ts`
- Create: `nexa-rag-front/src/features/chat/components/markdown-normalizer.test.ts`

- [ ] **Step 1: 写入失败测试**

```ts
import { describe, expect, it } from 'vitest'
import { normalizeMathDelimiters } from './markdown-normalizer'

describe('normalizeMathDelimiters', () => {
  it('应将反斜杠公式定界符转换为美元定界符', () => {
    expect(normalizeMathDelimiters('行内 \\(x^2\\)\\n\\[a+b\\]'))
      .toBe('行内 $x^2$\\n$$a+b$$')
  })

  it('不应修改围栏代码块和行内代码', () => {
    expect(normalizeMathDelimiters('`\\(code\\)`\\n```tex\\n\\[block\\]\\n```'))
      .toBe('`\\(code\\)`\\n```tex\\n\\[block\\]\\n```')
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- markdown-normalizer.test.ts`

Expected: FAIL，原因是模块尚未创建。

- [ ] **Step 3: 用扫描器实现最小逻辑**

```ts
const FENCE = '```'

export function normalizeMathDelimiters(markdown: string): string {
  let result = ''
  let cursor = 0
  while (cursor < markdown.length) {
    if (markdown.startsWith(FENCE, cursor)) {
      const closing = markdown.indexOf(FENCE, cursor + FENCE.length)
      if (closing < 0) return result + markdown.slice(cursor)
      result += markdown.slice(cursor, closing + FENCE.length)
      cursor = closing + FENCE.length
      continue
    }
    if (markdown[cursor] === '`') {
      const closing = markdown.indexOf('`', cursor + 1)
      if (closing < 0) return result + markdown.slice(cursor)
      result += markdown.slice(cursor, closing + 1)
      cursor = closing + 1
      continue
    }
    const block = markdown.startsWith('\\[', cursor) ? '\\]' : null
    const inline = markdown.startsWith('\\(', cursor) ? '\\)' : null
    const closing = block ?? inline
    if (closing) {
      const end = markdown.indexOf(closing, cursor + 2)
      if (end >= 0) {
        result += block ? `$$${markdown.slice(cursor + 2, end)}$$` : `$${markdown.slice(cursor + 2, end)}$`
        cursor = end + 2
        continue
      }
    }
    result += markdown[cursor]
    cursor += 1
  }
  return result
}
```

- [ ] **Step 4: 运行单元测试确认通过**

Run: `npm test -- markdown-normalizer.test.ts`

Expected: 2 个测试通过。

- [ ] **Step 5: 提交公式兼容逻辑**

```powershell
git add nexa-rag-front/src/features/chat/components/markdown-normalizer.ts nexa-rag-front/src/features/chat/components/markdown-normalizer.test.ts
git commit -m "feat(markdown): 兼容 LaTeX 公式定界符"
```

### Task 3: 实现安全 Markdown 与普通代码块

**Files:**
- Create: `nexa-rag-front/src/features/chat/components/MarkdownCodeBlock.tsx`
- Create: `nexa-rag-front/src/features/chat/components/AssistantMarkdown.tsx`
- Create: `nexa-rag-front/src/features/chat/components/AssistantMarkdown.test.tsx`

- [ ] **Step 1: 写入渲染与安全失败测试**

```tsx
it('应渲染 GFM、四种公式和普通代码块', () => {
  render(<AssistantMarkdown status="COMPLETED" content={'## 标题\\n\\n| A | B |\\n| - | - |\\n| 1 | 2 |\\n\\(x^2\\)\\n```ts\\nconst value = 1\\n```'} />)
  expect(screen.getByRole('heading', { name: '标题' })).toBeInTheDocument()
  expect(screen.getByRole('table')).toBeInTheDocument()
  expect(screen.getByText('const value = 1')).toBeInTheDocument()
})

it('不应执行模型返回的原始 HTML', () => {
  render(<AssistantMarkdown status="COMPLETED" content={'<img src=x onerror=alert(1) />'} />)
  expect(document.querySelector('img')).toBeNull()
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- AssistantMarkdown.test.tsx`

Expected: FAIL，原因是 `AssistantMarkdown` 尚未创建。

- [ ] **Step 3: 实现普通代码块组件**

`MarkdownCodeBlock` 接收 `language` 和 `code`，通过 `navigator.clipboard.writeText(code)` 复制。复制成功后临时显示“已复制”，失败后显示“复制失败”；没有语言时显示“文本”。普通代码使用 `rehype-highlight` 生成的内容，不在组件中执行 HTML 注入。

```tsx
export function MarkdownCodeBlock({ code, language }: { code: string; language?: string }) {
  const [copyState, setCopyState] = useState<'idle' | 'success' | 'error'>('idle')
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(code)
      setCopyState('success')
    } catch {
      setCopyState('error')
    }
  }
  return <div className="not-prose my-3 overflow-hidden rounded-lg border bg-[#1f2430]"><button type="button" onClick={() => void copy()}>{copyState === 'success' ? '已复制' : copyState === 'error' ? '复制失败' : '复制'}</button><pre><code>{code}</code></pre></div>
}
```

- [ ] **Step 4: 实现 Markdown 入口组件**

使用 `ReactMarkdown` 的 `remarkPlugins={[remarkGfm, remarkMath]}`、`rehypePlugins={[rehypeKatex, rehypeHighlight]}`。将内容先传入 `normalizeMathDelimiters`，不添加 `rehypeRaw`。在 `components.code` 中解析 `language-*` 类名：普通块转交 `MarkdownCodeBlock`；链接组件固定 `target="_blank"` 与 `rel="noreferrer noopener"`；Mermaid 分支在 Task 4 接入。

```tsx
export const AssistantMarkdown = memo(function AssistantMarkdown({ content, status }: AssistantMarkdownProps) {
  return <div className="assistant-markdown"><ReactMarkdown remarkPlugins={[remarkGfm, remarkMath]} rehypePlugins={[rehypeKatex, rehypeHighlight]}>{normalizeMathDelimiters(content)}</ReactMarkdown></div>
})
```

- [ ] **Step 5: 运行测试确认通过**

Run: `npm test -- AssistantMarkdown.test.tsx`

Expected: 标题、表格、公式、代码和 HTML 安全用例通过。

- [ ] **Step 6: 提交安全 Markdown 基础能力**

```powershell
git add nexa-rag-front/src/features/chat/components/MarkdownCodeBlock.tsx nexa-rag-front/src/features/chat/components/AssistantMarkdown.tsx nexa-rag-front/src/features/chat/components/AssistantMarkdown.test.tsx
git commit -m "feat(markdown): 渲染安全的助手回答"
```

### Task 4: 实现并测试 Mermaid 图表降级

**Files:**
- Create: `nexa-rag-front/src/features/chat/components/MermaidDiagram.tsx`
- Modify: `nexa-rag-front/src/features/chat/components/AssistantMarkdown.tsx`
- Modify: `nexa-rag-front/src/features/chat/components/AssistantMarkdown.test.tsx`

- [ ] **Step 1: 写入 Mermaid 失败测试并模拟第三方库**

```tsx
vi.mock('mermaid', () => ({ default: { initialize: vi.fn(), render: vi.fn() } }))

it('生成中应展示 Mermaid 原始代码，完成后应渲染图表', async () => {
  render(<AssistantMarkdown status="GENERATING" content={'```mermaid\\ngraph TD; A-->B\\n```'} />)
  expect(screen.getByText('graph TD; A-->B')).toBeInTheDocument()
  render(<AssistantMarkdown status="COMPLETED" content={'```mermaid\\ngraph TD; A-->B\\n```'} />)
  await expect(screen.findByLabelText('Mermaid 图表')).resolves.toBeInTheDocument()
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- AssistantMarkdown.test.tsx`

Expected: FAIL，原因是 Mermaid 代码块仍按普通代码块显示。

- [ ] **Step 3: 实现严格 Mermaid 组件**

组件仅在 `status === 'COMPLETED'` 时调用：

```ts
mermaid.initialize({ startOnLoad: false, securityLevel: 'strict', theme: 'neutral' })
const { svg } = await mermaid.render(`mermaid-${crypto.randomUUID()}`, code)
```

使用取消标识防止卸载后的状态更新。成功时在带有 `aria-label="Mermaid 图表"` 的容器展示 Mermaid 返回的 SVG；异常时保留 `MarkdownCodeBlock` 并增加“图表渲染失败”提示。`AssistantMarkdown` 的 `code` 组件检测 `language-mermaid` 后传入该组件。

- [ ] **Step 4: 补充异常降级与严格配置断言**

```tsx
it('Mermaid 渲染异常时应回退为原始代码并提示', async () => {
  vi.mocked(mermaid.render).mockRejectedValueOnce(new Error('语法错误'))
  render(<AssistantMarkdown status="COMPLETED" content={'```mermaid\\n错误图表\\n```'} />)
  expect(await screen.findByText('图表渲染失败')).toBeInTheDocument()
  expect(screen.getByText('错误图表')).toBeInTheDocument()
})
```

- [ ] **Step 5: 运行测试确认通过**

Run: `npm test -- AssistantMarkdown.test.tsx`

Expected: Mermaid 生成中、成功、失败三类断言均通过。

- [ ] **Step 6: 提交 Mermaid 支持**

```powershell
git add nexa-rag-front/src/features/chat/components/MermaidDiagram.tsx nexa-rag-front/src/features/chat/components/AssistantMarkdown.tsx nexa-rag-front/src/features/chat/components/AssistantMarkdown.test.tsx
git commit -m "feat(markdown): 支持 Mermaid 图表渲染"
```

### Task 5: 接入对话气泡并完成回归验证

**Files:**
- Modify: `nexa-rag-front/src/features/chat/ChatWorkspace.tsx:329-337`
- Modify: `nexa-rag-front/src/App.test.tsx`

- [ ] **Step 1: 写入工作台回归测试**

在 `App.test.tsx` 的 SSE 用例中推入：

```ts
controller.enqueue(encoder.encode('event: TOKEN\\ndata: {"content":"## 流式标题"}\\n\\n'))
controller.enqueue(encoder.encode('event: COMPLETE\\ndata: {}\\n\\n'))
```

并断言 `await screen.findByRole('heading', { name: '流式标题' })` 成功；新增用户消息断言，确认用户输入 `**原文**` 后仍显示为纯文本而不是 `strong` 节点。

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- App.test.tsx`

Expected: FAIL，助手消息仍由 `<p>` 直接输出，找不到标题角色。

- [ ] **Step 3: 最小接入 MessageBubble**

在 `ChatWorkspace.tsx` 导入 `AssistantMarkdown`，并将原本统一的文本节点替换为：

```tsx
{isUser
  ? <p className="whitespace-pre-wrap break-words">{message.content}</p>
  : <AssistantMarkdown content={message.content || (message.status === 'GENERATING' ? '正在生成…' : '')} status={message.status} />}
```

保留失败、取消提示和所有 SSE 状态处理；不修改接口、请求或取消逻辑。

- [ ] **Step 4: 运行定向测试确认通过**

Run:

```powershell
npm test -- markdown-normalizer.test.ts AssistantMarkdown.test.tsx App.test.tsx
```

Expected: 所有列出的测试文件通过。

- [ ] **Step 5: 执行生产构建并记录既有阻塞项**

Run: `npm run build`

Expected: 若构建仍被 `DocumentDetailPage.tsx`、`KnowledgeBaseListPage.tsx` 的既有 TypeScript 错误阻断，记录这些文件和错误；不得为通过本任务构建而修改知识库页面。

- [ ] **Step 6: 提交对话接入与回归测试**

```powershell
git add nexa-rag-front/src/features/chat/ChatWorkspace.tsx nexa-rag-front/src/App.test.tsx
git commit -m "feat(chat-ui): 接入助手 Markdown 回答"
```

## 计划自检

1. 规格覆盖：Task 1 覆盖依赖和样式；Task 2 覆盖四种公式定界符与代码排除；Task 3 覆盖 GFM、代码高亮、复制、链接和 HTML 安全；Task 4 覆盖 Mermaid 的完成态、生成态、严格模式和失败降级；Task 5 覆盖真实 SSE 接入与构建验证。
2. 无占位项：每项均指定准确路径、测试、命令与预期结果。
3. 类型一致性：`AssistantMarkdown` 始终接收 `content` 与消息 `status`；`MermaidDiagram` 仅由 `AssistantMarkdown` 的 `language-mermaid` 分支调用。
