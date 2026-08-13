# 前端飞书化重构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `nexa-rag-front` 整体重构为飞书桌面客户端风格（浅色、扁平、紧凑、`#3370FF` 主色），包括设计令牌、基础组件、外壳导航、7 个页面迁移，并新增路由管理页。

**Architecture:** 基于现有 React 19 + Vite + Tailwind 4 + Radix 技术栈自建设计系统。先在 `globals.css` 定义飞书风格设计令牌，重写/新增基础组件；再将 `AppShell` 重构为「顶栏 + 图标栏 + 模块面板 + 内容区」四段结构；最后按对话 → 知识库 → 模型管理 → 提示词的顺序逐页迁移，交互逻辑与 API 层全部保持不变。

**Tech Stack:** React 19、TypeScript、Vite 6、Tailwind CSS 4、Radix UI、lucide-react、react-router 7、vitest。

**设计文档:** `docs/superpowers/specs/2026-08-13-feishu-style-frontend-redesign-design.md`

---

## 0. 执行说明

- 所有任务在 `nexa-rag-front/` 下执行（命令工作目录均为 `nexa-rag-front/`）。
- 每个任务完成标准：对应 vitest 用例通过，且 `npm run build` 通过（仅涉及样式/组件重写的任务至少保证现有测试不回归）。
- 提交粒度：按任务提交，只暂存该任务涉及的文件；中文 Conventional Commit。
- 本轮明确不做（见设计文档第 7 节）：深色模式、设置页功能、Agent 占位功能、聊天引用来源展示、全局搜索行为（顶栏搜索框仅呈现样式）、移动端专项适配。
- 执行环境建议：按仓库惯例在独立 worktree 中执行（参考 `using-git-worktrees` 技能）。

## 1. 文件结构

### 新增

| 文件 | 职责 |
| --- | --- |
| `src/components/ui/tag.tsx` | 飞书风格标签（成功/信息/警告/危险/中性） |
| `src/components/ui/table.tsx` | 标准表格原语（Table/TableHeader/TableRow/TableHead/TableCell） |
| `src/components/ui/pagination.tsx` | 标准分页组件 |
| `src/components/ui/tabs.tsx` | 下划线式 Tab 组件 |
| `src/components/ui/toast.tsx` | 统一 Toast 展示组件 |
| `src/components/layout/TopBar.tsx` | 顶栏（全局搜索样式 + 帮助 + 头像） |
| `src/components/layout/IconRail.tsx` | 左侧 48px 图标栏 |
| `src/components/layout/ModulePanel.tsx` | 模块面板分发（按路由渲染对应面板） |
| `src/features/conversations/ConversationPanel.tsx` | 对话模块面板（会话列表，从 AppShell 迁入） |
| `src/features/knowledge-base/KnowledgePanel.tsx` | 知识库模块面板（库导航 + 视图 + 快捷筛选 + 上传） |
| `src/features/models/ModelPanel.tsx` | 模型管理模块面板（分组菜单） |
| `src/features/models/pages/ModelRoutePage.tsx` | 路由管理页（新增） |
| `src/features/settings/SettingsPage.tsx` | 设置占位页 |
| `src/components/ui/table.test.tsx`、`pagination.test.tsx`、`tabs.test.tsx`、`tag.test.tsx` | 新组件测试 |
| `src/components/layout/IconRail.test.tsx`、`ModulePanel.test.tsx` | 外壳导航测试 |
| `src/features/models/pages/ModelRoutePage.test.tsx` | 路由页测试 |

### 重写 / 修改

| 文件 | 变更 |
| --- | --- |
| `src/styles/globals.css` | 重写设计令牌（保留 markdown 样式） |
| `src/components/ui/button.tsx` | 飞书化（6px 圆角、紧凑高度、新增 danger 变体） |
| `src/components/ui/input.tsx`、`textarea.tsx` | 飞书化 |
| `src/components/ui/select.tsx` | 视觉替换为飞书蓝 |
| `src/components/ui/dialog.tsx`、`dropdown-menu.tsx`、`tooltip.tsx` | 视觉微调（圆角、边框、主色） |
| `src/app/AppShell.tsx` | 重构为四段外壳，会话列表逻辑迁出 |
| `src/app/router.tsx` | 新增 `/models/routes`、`/settings` 路由 |
| `src/features/chat/ChatWorkspace.tsx` | 仅渲染层飞书化（逻辑不动） |
| `src/features/knowledge-base/pages/KnowledgeBaseListPage.tsx` | 概览/列表飞书化 + 支持 `status` URL 参数 |
| `src/features/knowledge-base/components/DocumentListTable.tsx` | 改用 Table 原语 |
| `src/features/knowledge-base/pages/DocumentDetailPage.tsx` | 头部卡/步骤条/Tab 飞书化 |
| `src/features/models/pages/ModelConfigPage.tsx` | 重写主渲染：两栏 + 表格含测试连接/删除列 |
| `src/features/models/pages/ModelGovernancePage.tsx` | 视觉飞书化（逻辑不动） |
| `src/features/models/api/model-api.ts` | 新增路由候选配置接口方法 |
| `src/features/prompts/pages/PromptManagementPage.tsx` | 移除自带列表，编辑器飞书化 |
| `src/features/prompts/api/prompt-api.ts` | 不变（仅确认） |
| 各页面测试 | 按需更新（类名/结构变化导致的选择器问题） |

## 2. 样式迁移速查表

页面迁移时统一按下表替换，避免逐处手改：

| 现有样式 | 替换为 |
| --- | --- |
| `bg-[#6f62e8]`、`bg-[#5f52d9]`、`text-[#6b5ce7]`、`text-[#5649ce]`、`bg-[#eeecff]` | `bg-primary` / `text-primary` / `bg-primary-light` |
| `text-[#6256da]`、`hover:text-[#5649ce]`、`text-[#7166f7]` | `text-primary`（hover 同） |
| `bg-[#f8f8fb]`、`bg-[#f8f9fc]`、`bg-slate-50`、`bg-gradient-to-*` | `bg-background`（删除渐变） |
| `bg-[#fcfcfe]`、`bg-white`、`bg-card` | `bg-card` |
| `border-[#e8e7ee]`、`border-[#e8ebf1]`、`border-slate-200` | `border-border` |
| `text-[#9a98a6]`、`text-[#aaa8b4]`、`text-slate-400`、`text-[#a09eaa]` | `text-tertiary` |
| `text-slate-600`、`text-[#656370]`、`text-[#757280]` | `text-secondary` |
| `text-slate-900`、`text-[#373541]`、`text-[#302e39]` | `text-foreground` |
| `rounded-xl`、`rounded-2xl`、`rounded-[22px]` | `rounded-md` / `rounded-lg`（6px/8px） |
| `shadow-sm`、`shadow-md`、`shadow-xl`、`shadow-2xl`、`shadow-\[0_12px_30px_...\]` | 删除或仅保留 `shadow-xs`（极轻） |
| `bg-red-50`/`text-red-600`/`text-rose-600` 等危险色 | `bg-danger-light` / `text-danger` |
| `bg-emerald-50`/`text-emerald-600` 等成功色 | `bg-success-light` / `text-success` |
| `bg-amber-50`/`text-amber-600` 等警告色 | `bg-warning-light` / `text-warning` |
| `text-indigo-600`、`bg-indigo-600`、`from-indigo-600 to-violet-600` | `text-primary` / `bg-primary`（删除渐变） |

---

## Phase 0：设计令牌与基础组件

### Task 1: 重写 globals.css 设计令牌

**Files:**
- Modify: `src/styles/globals.css`（整体替换）

- [ ] **Step 1: 编写令牌测试不存在（纯样式无单测）——先备份并确认基线**

运行：
```powershell
npm test -- --run
```
Expected: 全部现有测试通过（记录基线）。

- [ ] **Step 2: 整体替换 globals.css**

将 `src/styles/globals.css` 替换为以下内容（保留 `.assistant-markdown` 全部样式）：

```css
@import "katex/dist/katex.min.css";
@import "tailwindcss";

@theme inline {
  --color-background: var(--background);
  --color-foreground: var(--foreground);
  --color-card: var(--card);
  --color-card-foreground: var(--card-foreground);
  --color-primary: var(--primary);
  --color-primary-foreground: var(--primary-foreground);
  --color-primary-light: var(--primary-light);
  --color-muted: var(--muted);
  --color-muted-foreground: var(--muted-foreground);
  --color-secondary: var(--secondary);
  --color-tertiary: var(--tertiary);
  --color-border: var(--border);
  --color-input: var(--input);
  --color-ring: var(--ring);
  --color-success: var(--success);
  --color-success-light: var(--success-light);
  --color-warning: var(--warning);
  --color-warning-light: var(--warning-light);
  --color-danger: var(--danger);
  --color-danger-light: var(--danger-light);
  --radius-sm: calc(var(--radius) - 2px);
  --radius-md: var(--radius);
  --radius-lg: calc(var(--radius) + 2px);
  --radius-xl: calc(var(--radius) + 6px);
}

:root {
  --background: #f2f3f5;
  --foreground: #1f2329;
  --card: #ffffff;
  --card-foreground: #1f2329;
  --primary: #3370ff;
  --primary-foreground: #ffffff;
  --primary-light: #e8f3ff;
  --muted: #f7f8fa;
  --muted-foreground: #86909c;
  --secondary: #4e5969;
  --tertiary: #86909c;
  --border: #e5e6eb;
  --input: #c9cdd4;
  --ring: #3370ff;
  --success: #00b42a;
  --success-light: #e8ffea;
  --warning: #ff7d00;
  --warning-light: #fff3e8;
  --danger: #f53f3f;
  --danger-light: #ffece8;
  --radius: 0.375rem;
}

* {
  border-color: var(--border);
}

body {
  margin: 0;
  min-width: 320px;
  min-height: 100vh;
  background: var(--background);
  color: var(--foreground);
  font-family: "PingFang SC", "Microsoft YaHei", system-ui, -apple-system, sans-serif;
}

.assistant-markdown {
  overflow-wrap: anywhere;
}

.assistant-markdown > :first-child {
  margin-top: 0;
}

.assistant-markdown > :last-child {
  margin-bottom: 0;
}

.assistant-markdown h1,
.assistant-markdown h2,
.assistant-markdown h3,
.assistant-markdown h4 {
  margin: 1.25rem 0 0.5rem;
  font-weight: 600;
  line-height: 1.45;
}

.assistant-markdown h1 { font-size: 1.5rem; }
.assistant-markdown h2 { font-size: 1.25rem; }
.assistant-markdown h3 { font-size: 1.125rem; }

.assistant-markdown p,
.assistant-markdown ul,
.assistant-markdown ol,
.assistant-markdown blockquote {
  margin: 0.75rem 0;
}

.assistant-markdown ul,
.assistant-markdown ol {
  padding-left: 1.5rem;
}

.assistant-markdown ul { list-style: disc; }
.assistant-markdown ol { list-style: decimal; }

.assistant-markdown blockquote {
  border-left: 3px solid var(--border);
  padding-left: 0.875rem;
  color: var(--muted-foreground);
}

.assistant-markdown :not(pre) > code {
  border-radius: 0.25rem;
  background: var(--muted);
  padding: 0.1rem 0.3rem;
  font-size: 0.875em;
}

.assistant-markdown table {
  display: block;
  max-width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
}

.assistant-markdown th,
.assistant-markdown td {
  border: 1px solid var(--border);
  padding: 0.45rem 0.7rem;
  text-align: left;
}

.assistant-markdown th {
  background: var(--muted);
  font-weight: 600;
}

.assistant-markdown .katex-display {
  max-width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 0.25rem 0;
}
```

- [ ] **Step 3: 验证构建**

运行：`npm run build`
Expected: `tsc` 与 `vite build` 均退出码 0。

- [ ] **Step 4: 回归测试**

运行：`npm test`
Expected: 全部通过（令牌替换后 shadcn 类名仍可用）。

- [ ] **Step 5: 提交**

```powershell
git add src/styles/globals.css
git commit -m "style(ui): 落地飞书风格设计令牌"
```

### Task 2: Button 组件飞书化

**Files:**
- Modify: `src/components/ui/button.tsx`
- Test: `src/components/ui/button.test.tsx`（新增）

- [ ] **Step 1: 写失败测试**

创建 `src/components/ui/button.test.tsx`：

```tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Button } from './button'

describe('Button', () => {
  it('渲染 danger 变体与图标尺寸', () => {
    render(<Button variant="danger" size="icon" aria-label="删除">×</Button>)
    const button = screen.getByRole('button', { name: '删除' })
    expect(button.className).toContain('bg-danger')
    expect(button.className).toContain('size-8')
  })
})
```

运行：`npx vitest run src/components/ui/button.test.tsx`
Expected: FAIL（`bg-danger` 不存在）。

- [ ] **Step 2: 重写 button.tsx**

```tsx
import { Slot } from '@radix-ui/react-slot'
import { cva, type VariantProps } from 'class-variance-authority'
import type { ButtonHTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

const buttonVariants = cva(
  'inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/30 disabled:pointer-events-none disabled:opacity-50',
  {
    variants: {
      variant: {
        default: 'bg-primary text-primary-foreground hover:bg-primary/90',
        outline: 'border border-input bg-card text-foreground hover:bg-muted',
        ghost: 'text-secondary hover:bg-muted hover:text-foreground',
        danger: 'bg-danger text-white hover:bg-danger/90',
      },
      size: {
        default: 'h-8 px-3.5',
        sm: 'h-7 rounded px-2.5 text-xs',
        icon: 'size-8 rounded',
      },
    },
    defaultVariants: {
      variant: 'default',
      size: 'default',
    },
  },
)

/** 飞书风格基础按钮组件。 */
export interface ButtonProps
  extends ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof buttonVariants> {
  asChild?: boolean
}

export function Button({ className, variant, size, asChild = false, ...props }: ButtonProps) {
  const Component = asChild ? Slot : 'button'
  return <Component className={cn(buttonVariants({ variant, size, className }))} {...props} />
}
```

- [ ] **Step 3: 跑测试确认通过**

运行：`npx vitest run src/components/ui/button.test.tsx`
Expected: PASS。

- [ ] **Step 4: 回归**

运行：`npm test`
Expected: 全部通过。

- [ ] **Step 5: 提交**

```powershell
git add src/components/ui/button.tsx src/components/ui/button.test.tsx
git commit -m "style(ui): 按钮组件飞书化"
```

### Task 3: Input / Textarea 飞书化

**Files:**
- Modify: `src/components/ui/input.tsx`
- Modify: `src/components/ui/textarea.tsx`

- [ ] **Step 1: 替换 input.tsx 类名**

将 `className` 中 `flex h-10 w-full rounded-xl border border-input bg-background px-3 py-2 text-sm outline-none placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50` 替换为：

```tsx
'flex h-8 w-full rounded-md border border-input bg-card px-2.5 text-sm outline-none placeholder:text-tertiary focus-visible:ring-2 focus-visible:ring-ring/30 disabled:cursor-not-allowed disabled:opacity-50'
```

- [ ] **Step 2: 替换 textarea.tsx 类名**

将 `flex min-h-20 w-full rounded-xl border border-input bg-transparent px-3 py-2 text-sm outline-none placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50` 替换为：

```tsx
'flex min-h-20 w-full rounded-md border border-input bg-transparent px-2.5 py-2 text-sm outline-none placeholder:text-tertiary focus-visible:ring-2 focus-visible:ring-ring/30 disabled:cursor-not-allowed disabled:opacity-50'
```

- [ ] **Step 3: 验证**

运行：`npm test && npm run build`
Expected: 均退出码 0。

- [ ] **Step 4: 提交**

```powershell
git add src/components/ui/input.tsx src/components/ui/textarea.tsx
git commit -m "style(ui): 输入组件飞书化"
```

### Task 4: 新增 Tag 组件

**Files:**
- Create: `src/components/ui/tag.tsx`
- Test: `src/components/ui/tag.test.tsx`

- [ ] **Step 1: 写失败测试**

```tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Tag } from './tag'

describe('Tag', () => {
  it('按变体渲染状态色', () => {
    render(<Tag variant="success">已索引</Tag>)
    const tag = screen.getByText('已索引')
    expect(tag.className).toContain('bg-success-light')
    expect(tag.className).toContain('text-success')
  })
})
```

运行：`npx vitest run src/components/ui/tag.test.tsx`
Expected: FAIL（模块不存在）。

- [ ] **Step 2: 实现 tag.tsx**

```tsx
import { cva, type VariantProps } from 'class-variance-authority'
import type { HTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

const tagVariants = cva('inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-xs leading-5', {
  variants: {
    variant: {
      success: 'bg-success-light text-success',
      info: 'bg-primary-light text-primary',
      warning: 'bg-warning-light text-warning',
      danger: 'bg-danger-light text-danger',
      neutral: 'bg-muted text-secondary',
    },
  },
  defaultVariants: { variant: 'neutral' },
})

export type TagVariant = NonNullable<VariantProps<typeof tagVariants>['variant']>

/** 飞书风格状态标签。 */
export function Tag({ className, variant, ...props }: HTMLAttributes<HTMLSpanElement> & VariantProps<typeof tagVariants>) {
  return <span className={cn(tagVariants({ variant }), className)} {...props} />
}
```

- [ ] **Step 3: 跑测试确认通过**

运行：`npx vitest run src/components/ui/tag.test.tsx`
Expected: PASS。

- [ ] **Step 4: 提交**

```powershell
git add src/components/ui/tag.tsx src/components/ui/tag.test.tsx
git commit -m "feat(ui): 新增飞书风格状态标签组件"
```

### Task 5: 新增 Table 原语

**Files:**
- Create: `src/components/ui/table.tsx`
- Test: `src/components/ui/table.test.tsx`

- [ ] **Step 1: 写失败测试**

```tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from './table'

describe('Table', () => {
  it('渲染表头与单元格', () => {
    render(
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>文档</TableHead>
            <TableHead>状态</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow>
            <TableCell>Q3 财报.pdf</TableCell>
            <TableCell>已索引</TableCell>
          </TableRow>
        </TableBody>
      </Table>,
    )
    expect(screen.getByText('Q3 财报.pdf')).toBeInTheDocument()
    expect(screen.getByText('文档').className).toContain('text-secondary')
  })
})
```

运行：`npx vitest run src/components/ui/table.test.tsx`
Expected: FAIL（模块不存在）。

- [ ] **Step 2: 实现 table.tsx**

```tsx
import type { HTMLAttributes, TdHTMLAttributes, ThHTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

/** 飞书风格表格根组件。 */
export function Table({ className, ...props }: HTMLAttributes<HTMLTableElement>) {
  return <table className={cn('w-full border-collapse text-sm text-foreground', className)} {...props} />
}

/** 表格头部容器。 */
export function TableHeader({ className, ...props }: HTMLAttributes<HTMLTableSectionElement>) {
  return <thead className={cn('bg-muted text-xs text-secondary', className)} {...props} />
}

/** 表格主体容器。 */
export function TableBody({ className, ...props }: HTMLAttributes<HTMLTableSectionElement>) {
  return <tbody className={cn('divide-y divide-border', className)} {...props} />
}

/** 表格行。 */
export function TableRow({ className, ...props }: HTMLAttributes<HTMLTableRowElement>) {
  return <tr className={cn('transition-colors hover:bg-muted/60', className)} {...props} />
}

/** 表头单元格。 */
export function TableHead({ className, ...props }: ThHTMLAttributes<HTMLTableCellElement>) {
  return <th className={cn('px-4 py-2.5 text-left font-medium', className)} {...props} />
}

/** 数据单元格。 */
export function TableCell({ className, ...props }: TdHTMLAttributes<HTMLTableCellElement>) {
  return <td className={cn('px-4 py-2.5 align-middle', className)} {...props} />
}
```

- [ ] **Step 3: 跑测试确认通过**

运行：`npx vitest run src/components/ui/table.test.tsx`
Expected: PASS。

- [ ] **Step 4: 提交**

```powershell
git add src/components/ui/table.tsx src/components/ui/table.test.tsx
git commit -m "feat(ui): 新增飞书风格表格原语"
```

### Task 6: 新增 Pagination 组件

**Files:**
- Create: `src/components/ui/pagination.tsx`
- Test: `src/components/ui/pagination.test.tsx`

- [ ] **Step 1: 写失败测试**

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Pagination } from './pagination'

describe('Pagination', () => {
  it('翻页回调与禁用边界', async () => {
    const onPageChange = vi.fn()
    render(<Pagination total={42} current={1} totalPages={3} onPageChange={onPageChange} />)
    expect(screen.getByText('共 42 条')).toBeInTheDocument()
    expect(screen.getByText('第 1 / 3 页')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '上一页' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: '下一页' }))
    expect(onPageChange).toHaveBeenCalledWith(2)
  })
})
```

运行：`npx vitest run src/components/ui/pagination.test.tsx`
Expected: FAIL（模块不存在）。

- [ ] **Step 2: 实现 pagination.tsx**

```tsx
import { cn } from '@/lib/utils'

export interface PaginationProps {
  total: number
  current: number
  totalPages: number
  onPageChange: (page: number) => void
  className?: string
}

/** 飞书风格分页组件。 */
export function Pagination({ total, current, totalPages, onPageChange, className }: PaginationProps) {
  return (
    <div className={cn('flex items-center justify-between text-xs text-tertiary', className)}>
      <span>共 {total} 条</span>
      <div className="flex items-center gap-2">
        <button
          type="button"
          disabled={current <= 1}
          onClick={() => onPageChange(current - 1)}
          className="h-7 rounded border border-border bg-card px-3 text-secondary transition-colors hover:bg-muted disabled:cursor-not-allowed disabled:opacity-40"
        >
          上一页
        </button>
        <span className="rounded border border-primary bg-primary-light px-3 py-1 text-primary">
          第 {current} / {totalPages} 页
        </span>
        <button
          type="button"
          disabled={current >= totalPages}
          onClick={() => onPageChange(current + 1)}
          className="h-7 rounded border border-border bg-card px-3 text-secondary transition-colors hover:bg-muted disabled:cursor-not-allowed disabled:opacity-40"
        >
          下一页
        </button>
      </div>
    </div>
  )
}
```

- [ ] **Step 3: 跑测试确认通过**

运行：`npx vitest run src/components/ui/pagination.test.tsx`
Expected: PASS。

- [ ] **Step 4: 提交**

```powershell
git add src/components/ui/pagination.tsx src/components/ui/pagination.test.tsx
git commit -m "feat(ui): 新增飞书风格分页组件"
```

### Task 7: 新增 Tabs 组件

**Files:**
- Create: `src/components/ui/tabs.tsx`
- Test: `src/components/ui/tabs.test.tsx`

- [ ] **Step 1: 写失败测试**

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Tabs } from './tabs'

describe('Tabs', () => {
  it('下划线指示当前 Tab 并触发切换', async () => {
    const onChange = vi.fn()
    render(<Tabs items={[{ value: 'all', label: '全部' }, { value: 'done', label: '已完成' }]} value="all" onChange={onChange} />)
    const active = screen.getByRole('button', { name: '全部' })
    expect(active.className).toContain('text-primary')
    await userEvent.click(screen.getByRole('button', { name: '已完成' }))
    expect(onChange).toHaveBeenCalledWith('done')
  })
})
```

运行：`npx vitest run src/components/ui/tabs.test.tsx`
Expected: FAIL（模块不存在）。

- [ ] **Step 2: 实现 tabs.tsx**

```tsx
import { cn } from '@/lib/utils'

export interface TabItem<T extends string> {
  value: T
  label: string
}

export interface TabsProps<T extends string> {
  items: TabItem<T>[]
  value: T
  onChange: (value: T) => void
  className?: string
}

/** 飞书风格下划线 Tab。 */
export function Tabs<T extends string>({ items, value, onChange, className }: TabsProps<T>) {
  return (
    <div className={cn('flex items-center gap-5 border-b border-border', className)}>
      {items.map((item) => {
        const isActive = item.value === value
        return (
          <button
            key={item.value}
            type="button"
            onClick={() => onChange(item.value)}
            className={cn(
              'relative pb-2.5 text-sm transition-colors',
              isActive ? 'font-medium text-primary' : 'text-secondary hover:text-foreground',
            )}
          >
            {item.label}
            {isActive && <span className="absolute inset-x-0 bottom-0 h-0.5 rounded-full bg-primary" />}
          </button>
        )
      })}
    </div>
  )
}
```

- [ ] **Step 3: 跑测试确认通过**

运行：`npx vitest run src/components/ui/tabs.test.tsx`
Expected: PASS。

- [ ] **Step 4: 提交**

```powershell
git add src/components/ui/tabs.tsx src/components/ui/tabs.test.tsx
git commit -m "feat(ui): 新增飞书风格 Tab 组件"
```

### Task 8: 新增 Toast 组件

**Files:**
- Create: `src/components/ui/toast.tsx`

- [ ] **Step 1: 实现 toast.tsx**

```tsx
/** 飞书风格顶部 Toast 展示组件。 */
export function Toast({ message }: { message: string | null }) {
  if (!message) return null
  return (
    <div className="fixed right-6 top-4 z-50" role="status">
      <div className="rounded-md bg-foreground px-4 py-2.5 text-xs font-medium text-card shadow-lg">
        {message}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: 验证构建**

运行：`npm run build`
Expected: 退出码 0。

- [ ] **Step 3: 提交**

```powershell
git add src/components/ui/toast.tsx
git commit -m "feat(ui): 新增统一 Toast 组件"
```

### Task 9: Select / Dialog / Dropdown 视觉飞书化

**Files:**
- Modify: `src/components/ui/select.tsx`
- Modify: `src/components/ui/dialog.tsx`
- Modify: `src/components/ui/dropdown-menu.tsx`

- [ ] **Step 1: 替换 select.tsx 中紫色类名**

在 `src/components/ui/select.tsx` 中按速查表替换：
- `rounded-xl` → `rounded-md`
- `border-slate-200/90` → `border-input`
- `hover:border-[#b9b1f7]`、`border-[#6f62e8]` → `hover:border-primary` / `border-primary`
- `focus:ring-[#eeecff]`、`ring-[#eeecff]` → `focus:ring-primary/30` / `ring-primary/30`
- `bg-[#eeecff] text-[#5649ce]` → `bg-primary-light text-primary`
- `text-[#6f62e8]` → `text-primary`
- `rounded-xl`（下拉面板）→ `rounded-md`，`shadow-xl ring-1 ring-slate-900/5` → `shadow-lg`

- [ ] **Step 2: 替换 dialog.tsx 视觉**

`DialogOverlay`：`bg-slate-950/40` → `bg-foreground/40`。
`DialogContent`：`rounded-xl ... p-6 shadow-xl` → `rounded-lg ... p-5 shadow-lg`。

- [ ] **Step 3: 检查 dropdown-menu.tsx**

打开 `src/components/ui/dropdown-menu.tsx`，将内容面板类名中的 `rounded-xl` → `rounded-md`、`shadow-xl` → `shadow-lg`、`text-slate-*` 按速查表替换。

- [ ] **Step 4: 验证**

运行：`npm test && npm run build`
Expected: 均退出码 0。

- [ ] **Step 5: 提交**

```powershell
git add src/components/ui/select.tsx src/components/ui/dialog.tsx src/components/ui/dropdown-menu.tsx
git commit -m "style(ui): 选择器与弹层组件飞书化"
```

## Phase 0：外壳重构

### Task 10: 新增 TopBar 组件

**Files:**
- Create: `src/components/layout/TopBar.tsx`

- [ ] **Step 1: 实现 TopBar.tsx**

```tsx
import { HelpCircle, Search } from 'lucide-react'

/** 飞书风格顶栏：全局搜索样式 + 帮助 + 用户头像。全局搜索行为本轮不做。 */
export function TopBar() {
  return (
    <header className="flex h-11 shrink-0 items-center gap-4 border-b border-border bg-card px-4">
      <div className="relative mx-auto w-full max-w-md">
        <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-tertiary" aria-hidden="true" />
        <input
          aria-label="全局搜索"
          placeholder="搜索会话、文档、模型…"
          className="h-7 w-full rounded-md border border-transparent bg-muted pl-8 pr-3 text-xs text-foreground outline-none placeholder:text-tertiary focus:border-input focus:bg-card"
        />
      </div>
      <div className="ml-auto flex items-center gap-3 text-tertiary">
        <button type="button" aria-label="帮助" className="transition-colors hover:text-primary">
          <HelpCircle className="size-4" />
        </button>
        <span className="flex size-6 items-center justify-center rounded-full bg-tertiary/30 text-[10px] font-semibold text-secondary" aria-label="当前用户">
          N
        </span>
      </div>
    </header>
  )
}
```

- [ ] **Step 2: 验证构建**

运行：`npm run build`
Expected: 退出码 0。

- [ ] **Step 3: 提交**

```powershell
git add src/components/layout/TopBar.tsx
git commit -m "feat(layout): 新增飞书风格顶栏"
```

### Task 11: 新增 IconRail 组件

**Files:**
- Create: `src/components/layout/IconRail.tsx`
- Test: `src/components/layout/IconRail.test.tsx`

- [ ] **Step 1: 写失败测试**

```tsx
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { IconRail } from './IconRail'

describe('IconRail', () => {
  it('高亮当前模块', () => {
    render(
      <MemoryRouter initialEntries={['/chat']}>
        <IconRail activeKey="chat" />
      </MemoryRouter>,
    )
    const chatLink = screen.getByRole('link', { name: /对话/ })
    expect(chatLink.className).toContain('bg-primary-light')
    expect(screen.getByRole('link', { name: /知识库/ })).toBeInTheDocument()
  })
})
```

运行：`npx vitest run src/components/layout/IconRail.test.tsx`
Expected: FAIL（模块不存在）。

- [ ] **Step 2: 实现 IconRail.tsx**

```tsx
import { LibraryBig, MessageSquareText, Settings, SlidersHorizontal, Sparkles, type LucideIcon } from 'lucide-react'
import { NavLink } from 'react-router-dom'
import { cn } from '@/lib/utils'

export interface IconRailItem {
  key: string
  label: string
  icon: LucideIcon
  to: string
}

export const iconRailItems: IconRailItem[] = [
  { key: 'chat', label: '对话', icon: MessageSquareText, to: '/chat' },
  { key: 'knowledge', label: '知识库', icon: LibraryBig, to: '/knowledge-base' },
  { key: 'models', label: '模型管理', icon: SlidersHorizontal, to: '/models' },
  { key: 'settings', label: '设置', icon: Settings, to: '/settings' },
]

/** 飞书风格 48px 图标栏。 */
export function IconRail({ activeKey }: { activeKey: string }) {
  return (
    <nav aria-label="主导航" className="flex w-12 shrink-0 flex-col items-center border-r border-border bg-card py-2">
      <NavLink
        to="/chat"
        aria-label="NexaRAG 首页"
        className="mb-3 flex size-7 items-center justify-center rounded-md bg-primary text-sm font-bold text-primary-foreground"
      >
        N
      </NavLink>
      {iconRailItems.map(({ key, label, icon: Icon, to }) => {
        const isActive = key === activeKey
        return (
          <NavLink
            key={key}
            to={to}
            aria-label={label}
            title={label}
            className={cn(
              'mb-1 flex size-9 items-center justify-center rounded-md transition-colors',
              isActive ? 'bg-primary-light text-primary' : 'text-tertiary hover:bg-muted hover:text-secondary',
            )}
          >
            <Icon className="size-4.5" />
          </NavLink>
        )
      })}
      <div className="flex-1" />
      <span className="mt-3 flex size-6 items-center justify-center rounded-full bg-tertiary/30 text-[10px] font-semibold text-secondary" aria-label="当前用户">
        N
      </span>
    </nav>
  )
}
```

说明：`size-4.5` 为 Tailwind 4 合法值；如构建报错改用 `size-[18px]`。

- [ ] **Step 3: 跑测试确认通过**

运行：`npx vitest run src/components/layout/IconRail.test.tsx`
Expected: PASS。

- [ ] **Step 4: 提交**

```powershell
git add src/components/layout/IconRail.tsx src/components/layout/IconRail.test.tsx
git commit -m "feat(layout): 新增飞书风格图标栏"
```

### Task 12: 迁出会话列表到 ConversationPanel

**Files:**
- Create: `src/features/conversations/ConversationPanel.tsx`
- Modify: `src/app/AppShell.tsx`（删除 aside 与相关状态）

- [ ] **Step 1: 创建 ConversationPanel.tsx**

将当前 `AppShell.tsx` 中如下代码原样迁移到新文件（保持逻辑不变，仅按速查表替换样式类名）：
- 顶部 `useState` 区块：`search`、`editingId`、`editTitle`、`confirmDeleteId`、`isDeleting`；
- `filteredConversations` / `pinnedItems` / `todayItems` / `earlierItems` 三个 `useMemo`；
- `openConversation`、`handleStartRename`、`handleSaveRename`、`handleKeyDownRename`、`handleConfirmDelete` 五个 handler；
- 原 `<aside>` 整个 JSX（新建对话按钮、搜索框、会话列表分组、功能配置区删除、底部用户卡删除）与 `ConversationListItemRow` 子组件；
- 删除 Dialog 整体保留在面板内。

替换要点：
- `bg-[#fcfcfe]` → `bg-card`；`border-[#e8e7ee]` → `border-border`；
- 选中态 `bg-[#eeecff] text-[#5649ce]` → `bg-primary-light text-primary`；
- hover `bg-[#f1f0f5]` → `bg-muted`；主按钮紫色 → `bg-primary text-primary-foreground`；
- 圆角 `rounded-xl` → `rounded-md`、`rounded-lg` → `rounded-md`；
- 移除「功能配置」板块（迁移到模型/知识库面板）与底部用户卡（迁移到 TopBar/IconRail）。

新组件签名：

```tsx
export function ConversationPanel() {
  // 全部迁移后的状态与 handler
  // 渲染：<aside aria-label="会话列表" className="flex w-[232px] shrink-0 flex-col border-r border-border bg-card">
}
```

- [ ] **Step 2: 删除 AppShell 中的 aside 与状态**

从 `src/app/AppShell.tsx` 删除：aside 相关 import（`Search`、`Pin` 等仅会话列表使用的图标）、全部会话状态与 handler、`ConversationListItemRow` 组件、删除 Dialog；保留 `ConversationNavigationProvider` 与 `<Outlet />` 的容器。

- [ ] **Step 3: 验证测试与构建**

运行：`npm test && npm run build`
Expected: 均退出码 0；若 `App.test.tsx` 断言了已迁移内容，按需更新其断言为 `ConversationPanel` 的可见文本。

- [ ] **Step 4: 提交**

```powershell
git add src/features/conversations/ConversationPanel.tsx src/app/AppShell.tsx src/app/App.test.tsx
git commit -m "refactor(layout): 会话列表迁入对话模块面板"
```

### Task 13: 新增 KnowledgePanel 与 ModelPanel

**Files:**
- Create: `src/features/knowledge-base/KnowledgePanel.tsx`
- Create: `src/features/models/ModelPanel.tsx`

- [ ] **Step 1: 实现 KnowledgePanel.tsx**

```tsx
import { FileUp, FolderOpen } from 'lucide-react'
import { NavLink } from 'react-router-dom'
import { cn } from '@/lib/utils'

const items = [
  { label: '知识库概览', to: '/knowledge-base', end: true },
  { label: '全部文档', to: '/knowledge-base?view=documents', end: true },
  { label: '处理中', to: '/knowledge-base?view=documents&status=PROCESSING', end: true },
  { label: '处理失败', to: '/knowledge-base?view=documents&status=FAILED', end: true },
]

/** 知识库模块面板：库导航 + 视图 + 快捷筛选 + 上传入口。 */
export function KnowledgePanel() {
  return (
    <aside aria-label="知识库导航" className="flex w-[232px] shrink-0 flex-col border-r border-border bg-card py-2">
      <div className="mb-1 flex items-center justify-between px-4 py-1.5">
        <span className="text-sm font-semibold text-foreground">知识库</span>
        <FolderOpen className="size-4 text-tertiary" aria-hidden="true" />
      </div>
      <div className="mx-2 mb-2 flex items-center gap-2 rounded-md bg-primary-light px-2.5 py-1.5 text-xs font-medium text-primary">
        默认知识库
      </div>
      <p className="px-4 pb-1 pt-2 text-xs font-medium text-tertiary">视图</p>
      {items.slice(0, 2).map((item) => (
        <NavLink
          key={item.label}
          to={item.to}
          end={item.end}
          className={({ isActive }) =>
            cn(
              'mx-2 rounded px-3 py-1.5 text-sm transition-colors',
              isActive ? 'bg-primary-light font-medium text-primary' : 'text-secondary hover:bg-muted hover:text-foreground',
            )}
        >
          {item.label}
        </NavLink>
      ))}
      <p className="px-4 pb-1 pt-3 text-xs font-medium text-tertiary">快捷筛选</p>
      {items.slice(2).map((item) => (
        <NavLink
          key={item.label}
          to={item.to}
          end={item.end}
          className={({ isActive }) =>
            cn(
              'mx-2 rounded px-3 py-1.5 text-sm transition-colors',
              isActive ? 'bg-primary-light font-medium text-primary' : 'text-secondary hover:bg-muted hover:text-foreground',
            )}
        >
          {item.label}
        </NavLink>
      ))}
      <div className="flex-1" />
      <NavLink
        to="/knowledge-base?view=documents&upload=1"
        className="mx-3 mb-2 flex items-center justify-center gap-1.5 rounded-md bg-primary py-1.5 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
      >
        <FileUp className="size-3.5" />
        上传文档
      </NavLink>
    </aside>
  )
}
```

- [ ] **Step 2: 实现 ModelPanel.tsx**

```tsx
import { NavLink } from 'react-router-dom'
import { cn } from '@/lib/utils'

const groups = [
  {
    label: '模型管理',
    items: [
      { label: '模型配置', to: '/models', end: true },
      { label: '路由管理', to: '/models/routes', end: true },
      { label: '治理参数', to: '/models/governance', end: true },
    ],
  },
  {
    label: '模板',
    items: [{ label: '提示词管理', to: '/prompts', end: true }],
  },
]

/** 模型管理模块面板：分组菜单。 */
export function ModelPanel() {
  return (
    <aside aria-label="模型管理导航" className="flex w-[232px] shrink-0 flex-col border-r border-border bg-card py-3">
      {groups.map((group) => (
        <div key={group.label} className="mb-3">
          <p className="px-4 pb-1 text-xs font-medium text-tertiary">{group.label}</p>
          {group.items.map((item) => (
            <NavLink
              key={item.label}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'mx-2 rounded px-3 py-1.5 text-sm transition-colors',
                  isActive ? 'bg-primary-light font-medium text-primary' : 'text-secondary hover:bg-muted hover:text-foreground',
                )}
            >
              {item.label}
            </NavLink>
          ))}
        </div>
      ))}
    </aside>
  )
}
```

- [ ] **Step 3: 验证构建**

运行：`npm run build`
Expected: 退出码 0。

- [ ] **Step 4: 提交**

```powershell
git add src/features/knowledge-base/KnowledgePanel.tsx src/features/models/ModelPanel.tsx
git commit -m "feat(layout): 新增知识库与模型管理模块面板"
```

### Task 14: 重构 AppShell 为四段外壳并新增路由

**Files:**
- Modify: `src/app/AppShell.tsx`
- Modify: `src/app/router.tsx`
- Test: `src/app/router.test.tsx`

- [ ] **Step 1: 实现 AppShell.tsx**

```tsx
import { useLocation } from 'react-router-dom'
import { Outlet } from 'react-router-dom'
import { TopBar } from '@/components/layout/TopBar'
import { IconRail } from '@/components/layout/IconRail'
import { ModulePanel } from '@/components/layout/ModulePanel'
import { ConversationNavigationProvider } from '@/features/conversations/ConversationNavigationContext'

function resolveActiveKey(pathname: string): string {
  if (pathname.startsWith('/knowledge-base')) return 'knowledge'
  if (pathname.startsWith('/models') || pathname.startsWith('/prompts')) return 'models'
  if (pathname.startsWith('/settings')) return 'settings'
  return 'chat'
}

/** AI 中台页面外壳：顶栏 + 图标栏 + 模块面板 + 内容区。 */
export function AppShell() {
  const { pathname } = useLocation()
  return (
    <ConversationNavigationProvider>
      <div className="flex h-dvh min-h-[560px] flex-col overflow-hidden bg-background text-foreground">
        <TopBar />
        <div className="flex min-h-0 flex-1">
          <IconRail activeKey={resolveActiveKey(pathname)} />
          <ModulePanel />
          <main className="relative min-w-0 flex-1">
            <Outlet />
          </main>
        </div>
      </div>
    </ConversationNavigationProvider>
  )
}
```

- [ ] **Step 2: 实现 ModulePanel.tsx**

```tsx
import { useLocation } from 'react-router-dom'
import { ConversationPanel } from '@/features/conversations/ConversationPanel'
import { KnowledgePanel } from '@/features/knowledge-base/KnowledgePanel'
import { ModelPanel } from '@/features/models/ModelPanel'

/** 模块面板分发：按当前路由渲染对应面板。 */
export function ModulePanel() {
  const { pathname } = useLocation()
  if (pathname.startsWith('/knowledge-base')) return <KnowledgePanel />
  if (pathname.startsWith('/models') || pathname.startsWith('/prompts')) return <ModelPanel />
  if (pathname.startsWith('/settings')) return null
  return <ConversationPanel />
}
```

- [ ] **Step 3: 更新 router.tsx**

在 `children` 中加入：

```tsx
{ path: 'models/routes', element: <ModelRoutePage /> },
{ path: 'settings', element: <SettingsPage /> },
```

并补充 import：

```tsx
import ModelRoutePage from '@/features/models/pages/ModelRoutePage'
import { SettingsPage } from '@/features/settings/SettingsPage'
```

（Task 18 创建 `ModelRoutePage`、Task 21 创建 `SettingsPage` 前，可先创建占位导出，避免构建中断。）

- [ ] **Step 4: 更新 router.test.tsx**

在测试中新增断言：`/models/routes` 渲染路由管理页标题、`/settings` 渲染设置占位页标题。

- [ ] **Step 5: 验证**

运行：`npm test && npm run build`
Expected: 均退出码 0。

- [ ] **Step 6: 提交**

```powershell
git add src/app/AppShell.tsx src/app/router.tsx src/app/router.test.tsx src/components/layout/ModulePanel.tsx
git commit -m "feat(layout): 外壳重构为飞书式四段结构并接入新路由"
```

## Phase 1：对话页迁移

### Task 15: ChatWorkspace 渲染层飞书化

**Files:**
- Modify: `src/features/chat/ChatWorkspace.tsx`
- Test: `src/features/chat/ChatWorkspace.test.tsx`（如不存在则创建基础渲染测试）

- [ ] **Step 1: 保留全部逻辑，替换 return 段**

保持文件顶部所有 `useState`/`useEffect`/handler 不变，仅替换从 `return (` 开始的 JSX。替换要点：

- 外层 `<section>`：`bg-[#fbfbfd]` → `bg-background`；
- `<header>`：`h-[68px] ... bg-white/80 backdrop-blur` → `h-11 bg-card border-b border-border`，标题字号 `text-sm`、副标题 `text-xs text-tertiary`；
- 消息区：`max-w-[920px]` → `max-w-[880px]`，`bg-background`；
- Welcome：外层 `rounded-2xl border ... shadow` 建议卡 → `rounded-lg border border-border bg-card`，去掉 hover 位移与重阴影；
- `MessageBubble`：助手气泡 `border bg-card` → `bg-muted`，圆角 `rounded-2xl` → `rounded-lg`；用户气泡 `bg-primary text-primary-foreground`（class 名不变，但 token 已替换为飞书蓝）；`rounded-[8px 8px 2px 8px]` 调整为 `rounded-lg`；
- Composer：`rounded-[22px] border-[#dedbe8] shadow-\[0_12px_30px...\] focus-within:ring-4` → `rounded-lg border-border shadow-none focus-within:ring-2 focus-within:ring-primary/30`；发送按钮 `size-8 rounded-xl bg-[#6f62e8]` → `size-7 rounded-md bg-primary`；
- 顶部右侧"帮助/我的工作区"按钮：`text-[#757280] hover:text-[#6256da]` → `text-secondary hover:text-primary`。

- [ ] **Step 2: 补充渲染测试**

创建 `src/features/chat/ChatWorkspace.test.tsx`（如不存在）：

```tsx
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import ChatWorkspace from './ChatWorkspace'

describe('ChatWorkspace', () => {
  it('渲染欢迎态与组合器', () => {
    render(
      <MemoryRouter initialEntries={['/chat']}>
        <ChatWorkspace />
      </MemoryRouter>,
    )
    expect(screen.getByText('你好，今天想做什么？')).toBeInTheDocument()
    expect(screen.getByLabelText('消息内容')).toBeInTheDocument()
  })
})
```

运行：`npx vitest run src/features/chat/ChatWorkspace.test.tsx`
Expected: PASS。

- [ ] **Step 3: 全量回归**

运行：`npm test && npm run build`
Expected: 均退出码 0。

- [ ] **Step 4: 提交**

```powershell
git add src/features/chat/ChatWorkspace.tsx src/features/chat/ChatWorkspace.test.tsx
git commit -m "style(chat-ui): 对话工作台飞书化"
```

## Phase 2：知识库页迁移

### Task 16: 知识库列表页飞书化并支持 status 参数

**Files:**
- Modify: `src/features/knowledge-base/pages/KnowledgeBaseListPage.tsx`
- Modify: `src/features/knowledge-base/components/DocumentListTable.tsx`
- Test: `src/features/knowledge-base/pages/KnowledgeBaseListPage.test.tsx`

- [ ] **Step 1: 支持 `status` URL 参数**

在 `KnowledgeBaseListPage` 组件开头新增：

```tsx
const statusParam = searchParams.get('status')
const [statusFilter, setStatusFilter] = useState<StatusFilterType>(
  statusParam === 'PROCESSING' || statusParam === 'FAILED' ? statusParam : 'ALL',
)
```

并删除原有的 `useState<StatusFilterType>('ALL')`。

- [ ] **Step 2: 概览区飞书化**

按速查表替换 `KnowledgeOverview`：删除 `bg-gradient-to-b from-slate-50 ...`、`rounded-2xl border-indigo-* shadow-sm` 等，外层改为 `bg-background`，卡片改为 `rounded-lg border border-border bg-card`，指标卡改扁平（无 `shadow-inner`），Hero 按钮 `bg-gradient-to-r from-indigo-600 to-violet-600` → `bg-primary`。

- [ ] **Step 3: DocumentLibrary 结构飞书化**

- 面包屑文字：`text-slate-* hover:text-indigo-600` → `text-tertiary hover:text-primary`；
- 状态 Tab：改为 `Tabs` 组件（`items` 为 全部状态/已索引/处理中/处理失败，`onChange` 调用 `onStatusFilterChange`）；
- 搜索框与刷新按钮：`h-10 rounded-xl border-slate-200` → `h-8 rounded-md border-border bg-card`；
- 底部分页与"20 条/页"：移除"20 条/页"静态块，改用 `Pagination` 组件（`total` 来自 `page.total`，`totalPages` 来自 `page.pages`）。

- [ ] **Step 4: DocumentListTable 改用 Table 原语**

打开 `src/features/knowledge-base/components/DocumentListTable.tsx`，将原 `<table>`/`<thead>`/`<tbody>`/`<tr>`/`<th>`/`<td>` 标签替换为 `Table`/`TableHeader`/`TableBody`/`TableRow`/`TableHead`/`TableCell`，表头 6 列与数据行使用相同网格（如原为固定 grid 则保持），状态列改用 `Tag` 组件：

```tsx
<Tag variant={status === 'INDEXED' ? 'success' : isProcessingStatus(status) ? 'info' : 'danger'}>
  {statusLabel}
</Tag>
```

- [ ] **Step 5: 验证**

运行：`npx vitest run src/features/knowledge-base && npm run build`
Expected: 均退出码 0；如测试断言旧类名，按新结构更新。

- [ ] **Step 6: 提交**

```powershell
git add src/features/knowledge-base/pages/KnowledgeBaseListPage.tsx src/features/knowledge-base/components/DocumentListTable.tsx src/features/knowledge-base/pages/KnowledgeBaseListPage.test.tsx
git commit -m "style(knowledge-ui): 知识库列表与概览飞书化"
```

### Task 17: 文档详情页飞书化

**Files:**
- Modify: `src/features/knowledge-base/pages/DocumentDetailPage.tsx`

- [ ] **Step 1: 替换渲染层样式**

按速查表替换：外层渐变背景 → `bg-background`；头部 Banner `rounded-2xl shadow-sm backdrop-blur` → `rounded-lg border border-border bg-card`；文件大小/状态等指标卡 `rounded-xl bg-*-50/80` → `rounded-md bg-muted`；Tab 导航改用 `Tabs` 组件（文档概览/文本分块）；流水线三阶段块 `bg-indigo-600` → `bg-primary`、`bg-emerald-100` → `bg-success-light text-success`、`bg-slate-200/60` → `bg-muted text-tertiary`；分块空态 `rounded-2xl border-dashed` → `rounded-lg border border-dashed border-border`。

- [ ] **Step 2: 验证**

运行：`npm test && npm run build`
Expected: 均退出码 0。

- [ ] **Step 3: 提交**

```powershell
git add src/features/knowledge-base/pages/DocumentDetailPage.tsx
git commit -m "style(knowledge-ui): 文档详情页飞书化"
```

## Phase 3：模型管理

### Task 18: model-api 补充路由候选配置接口

**Files:**
- Modify: `src/features/models/api/model-api.ts`
- Test: `src/features/models/api/model-api.test.ts`

- [ ] **Step 1: 写失败测试**

创建 `src/features/models/api/model-api.test.ts`（与 `conversation-api.test.ts` 相同模式）：

```ts
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createModelRoute,
  deleteModelRoute,
  getModelRouteConfigs,
  getModelRoutes,
} from './model-api'

function successData(data: unknown): Response {
  return new Response(JSON.stringify({ code: '0', message: null, data, traceId: null }), { status: 200 })
}

describe('模型路由接口', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('查询路由候选配置应调用对应路径并返回 data', async () => {
    const fetchMock = vi.fn().mockResolvedValue(successData([
      { routeConfigId: 1, routeId: 2, configId: 3, modelName: 'qwen-plus' },
    ]))
    vi.stubGlobal('fetch', fetchMock)

    const list = await getModelRouteConfigs(2)

    expect(fetchMock).toHaveBeenCalledWith('/api/model/routes/2/configs', undefined)
    expect(list[0]?.modelName).toBe('qwen-plus')
  })

  it('创建路由应 POST 到 /api/model/routes', async () => {
    const fetchMock = vi.fn().mockResolvedValue(successData({ routeId: 9, routeKey: 'DEFAULT_LLM', modelType: 'CHAT' }))
    vi.stubGlobal('fetch', fetchMock)

    await createModelRoute({ routeKey: 'DEFAULT_LLM', modelType: 'CHAT' })

    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe('/api/model/routes')
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body))).toEqual({ routeKey: 'DEFAULT_LLM', modelType: 'CHAT' })
  })

  it('删除路由应调用 DELETE 接口', async () => {
    const fetchMock = vi.fn().mockResolvedValue(successData(null))
    vi.stubGlobal('fetch', fetchMock)

    await deleteModelRoute(5)

    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe('/api/model/routes/5')
    expect(init.method).toBe('DELETE')
  })

  it('查询路由列表应调用 GET 接口', async () => {
    const fetchMock = vi.fn().mockResolvedValue(successData([
      { routeId: 1, routeKey: 'DEFAULT_LLM', modelType: 'CHAT', strategy: 'FAILOVER', enabled: true },
    ]))
    vi.stubGlobal('fetch', fetchMock)

    const routes = await getModelRoutes()

    expect(fetchMock).toHaveBeenCalledWith('/api/model/routes', undefined)
    expect(routes[0]?.routeKey).toBe('DEFAULT_LLM')
  })
})
```

- [ ] **Step 2: 新增接口方法与类型**

在 `model-api.ts` 末尾新增：

```ts
export interface ModelRouteConfigItem {
  routeConfigId: number | string
  routeId: number | string
  configId: number | string
  modelName?: string
  provider?: string
  priority?: number
  weight?: number
  enabled?: boolean
}

export interface ModelRouteConfigCreateRequest {
  configId: number | string
  priority?: number
  weight?: number
}

export interface ModelRouteConfigUpdateRequest {
  priority?: number
  weight?: number
  enabled?: boolean
}

/** 查询路由候选配置列表 */
export function getModelRouteConfigs(routeId: number | string): Promise<ModelRouteConfigItem[]> {
  return request<ModelRouteConfigItem[]>(`/api/model/routes/${routeId}/configs`)
}

/** 创建路由候选配置 */
export function createModelRouteConfig(routeId: number | string, data: ModelRouteConfigCreateRequest): Promise<ModelRouteConfigItem> {
  return request<ModelRouteConfigItem>(`/api/model/routes/${routeId}/configs`, {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

/** 更新路由候选配置 */
export function updateModelRouteConfig(routeId: number | string, routeConfigId: number | string, data: ModelRouteConfigUpdateRequest): Promise<ModelRouteConfigItem> {
  return request<ModelRouteConfigItem>(`/api/model/routes/${routeId}/configs/${routeConfigId}`, {
    method: 'PATCH',
    body: JSON.stringify(data),
  })
}

/** 删除路由候选配置 */
export function deleteModelRouteConfig(routeId: number | string, routeConfigId: number | string): Promise<void> {
  return request<void>(`/api/model/routes/${routeId}/configs/${routeConfigId}`, {
    method: 'DELETE',
  })
}

/** 创建模型路由 */
export function createModelRoute(data: { routeKey: string; modelType: string; strategy?: string; enabled?: boolean; remark?: string }): Promise<ModelRouteItem> {
  return request<ModelRouteItem>('/api/model/routes', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

/** 删除模型路由 */
export function deleteModelRoute(routeId: number | string): Promise<void> {
  return request<void>(`/api/model/routes/${routeId}`, {
    method: 'DELETE',
  })
}
```

- [ ] **Step 3: 验证**

运行：`npx vitest run src/features/models/api && npm run build`
Expected: 均退出码 0。

- [ ] **Step 4: 提交**

```powershell
git add src/features/models/api/model-api.ts src/features/models/api/model-api.test.ts
git commit -m "feat(models): 补充路由候选配置与路由 CRUD 接口"
```

### Task 19: 新增路由管理页

**Files:**
- Create: `src/features/models/pages/ModelRoutePage.tsx`
- Test: `src/features/models/pages/ModelRoutePage.test.tsx`

- [ ] **Step 1: 写失败测试**

```tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import ModelRoutePage from './ModelRoutePage'

vi.mock('../api/model-api', () => ({
  getModelRoutes: vi.fn().mockResolvedValue([
    { routeId: 1, routeKey: 'DEFAULT_LLM', modelType: 'CHAT', strategy: 'FAILOVER', enabled: true },
  ]),
  getModelConfigs: vi.fn().mockResolvedValue([]),
  deleteModelRoute: vi.fn(),
}))

describe('ModelRoutePage', () => {
  it('渲染路由表格', async () => {
    render(<ModelRoutePage />)
    expect(await screen.findByText('DEFAULT_LLM')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /新建路由/ })).toBeInTheDocument()
  })
})
```

运行：`npx vitest run src/features/models/pages/ModelRoutePage.test.tsx`
Expected: FAIL（模块不存在）。

- [ ] **Step 2: 实现 ModelRoutePage.tsx**

页面结构（飞书风格，逻辑与 API 名称按 Task 18）：

```tsx
import { useEffect, useState } from 'react'
import { Plus, RefreshCw, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Pagination } from '@/components/ui/pagination'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tag } from '@/components/ui/tag'
import { Toast } from '@/components/ui/toast'
import {
  deleteModelRoute,
  getModelRoutes,
  type ModelRouteItem,
} from '../api/model-api'

export default function ModelRoutePage() {
  const [routes, setRoutes] = useState<ModelRouteItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [toastMessage, setToastMessage] = useState<string | null>(null)

  const loadRoutes = async () => {
    setLoading(true)
    setError(null)
    try {
      setRoutes(await getModelRoutes())
    } catch (err) {
      setError(err instanceof Error ? err.message : '路由列表加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadRoutes()
  }, [])

  const handleDelete = async (route: ModelRouteItem) => {
    if (!window.confirm(`确认删除路由 [${route.routeKey}]？`)) return
    try {
      await deleteModelRoute(route.routeId)
      setToastMessage(`已删除路由 [${route.routeKey}]`)
      void loadRoutes()
    } catch (err) {
      setToastMessage(err instanceof Error ? err.message : '删除失败')
    }
  }

  return (
    <div className="flex h-full min-h-0 flex-1 flex-col overflow-y-auto bg-background">
      <Toast message={toastMessage} />
      <div className="mx-auto w-full max-w-[1200px] px-6 py-5">
        <div className="mb-4 flex items-center justify-between">
          <div>
            <h1 className="text-xl font-semibold text-foreground">路由管理</h1>
            <p className="mt-0.5 text-xs text-tertiary">配置模型调度路由与候选模型</p>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => void loadRoutes()}>
              <RefreshCw className="size-3.5" />
              刷新
            </Button>
            <Button size="sm">
              <Plus className="size-3.5" />
              新建路由
            </Button>
          </div>
        </div>
        {error && <div className="mb-3 rounded-md border border-danger-light bg-danger-light p-3 text-xs text-danger">{error}</div>}
        <div className="overflow-hidden rounded-lg border border-border bg-card">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>路由名称</TableHead>
                <TableHead>routeKey</TableHead>
                <TableHead>模型类型</TableHead>
                <TableHead>策略</TableHead>
                <TableHead>状态</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                <TableRow><TableCell colSpan={6} className="py-10 text-center text-xs text-tertiary">正在加载…</TableCell></TableRow>
              ) : routes.length === 0 ? (
                <TableRow><TableCell colSpan={6} className="py-10 text-center text-xs text-tertiary">暂无路由，点击右上角新建</TableCell></TableRow>
              ) : (
                routes.map((route) => (
                  <TableRow key={route.routeId}>
                    <TableCell className="font-medium">{route.routeKey}</TableCell>
                    <TableCell className="font-mono text-xs">{route.routeKey}</TableCell>
                    <TableCell><Tag variant="info">{route.modelType}</Tag></TableCell>
                    <TableCell>{route.strategy || '—'}</TableCell>
                    <TableCell>
                      <Tag variant={route.enabled ? 'success' : 'neutral'}>{route.enabled ? '启用' : '禁用'}</Tag>
                    </TableCell>
                    <TableCell className="text-right">
                      <button type="button" aria-label={`删除 ${route.routeKey}`} onClick={() => void handleDelete(route)} className="text-danger hover:underline">
                        <Trash2 className="size-3.5" />
                      </button>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
          {routes.length > 0 && (
            <div className="border-t border-border px-4 py-3">
              <Pagination total={routes.length} current={1} totalPages={1} onPageChange={() => undefined} />
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
```

说明：新建路由、候选模型配置、路由级治理弹窗在本任务仅保留入口按钮（`新建路由` 按钮与行操作可先接 `createModelRoute`/`getModelRouteConfigs`，弹窗交互在 Task 20 完成），确保页面可运行。

- [ ] **Step 3: 跑测试确认通过**

运行：`npx vitest run src/features/models/pages/ModelRoutePage.test.tsx`
Expected: PASS。

- [ ] **Step 4: 提交**

```powershell
git add src/features/models/pages/ModelRoutePage.tsx src/features/models/pages/ModelRoutePage.test.tsx
git commit -m "feat(models): 新增路由管理页"
```

### Task 20: 模型配置页重写

**Files:**
- Modify: `src/features/models/pages/ModelConfigPage.tsx`

- [ ] **Step 1: 重写主渲染为两栏布局**

保留文件顶部全部数据加载与表单状态（`loadData`、弹窗状态、`handleCreateSubmit` 等），仅替换 `return` 段：

- 外层：`bg-[#f8f9fc]` → `bg-background`，去掉 max-w 容器渐变；
- 主区域改为两栏：左栏厂商列表（宽 232px，搜索框 + 厂商卡片，卡片选中 `bg-primary-light text-primary`），右栏厂商详情；
- 厂商详情头部：Logo + `displayName` + `Tag variant="success"` 已接入 + `添加模型` 按钮；
- 信息条：Base URL / 脱敏 Key，`rounded-md bg-muted px-3 py-2 text-xs`；
- 推荐模型：扁平小标签 `border border-border bg-card rounded px-2 py-0.5` + 一键接入按钮；
- 模型配置表使用 `Table` 原语，列：模型标识 / 类型 / 端点 / 密钥 / 操作（编辑）/ 测试连接 / 删除：

```tsx
<TableHead className="text-right">操作</TableHead>
<TableHead>测试连接</TableHead>
<TableHead>删除</TableHead>
...
<TableCell className="text-right">
  <button type="button" onClick={() => handleOpenEditModel(config)} className="text-primary hover:underline">编辑</button>
</TableCell>
<TableCell>
  <button
    type="button"
    disabled={testingConfigId === config.configId}
    onClick={() => void handleTestConnectionReal(config.configId, config.modelName)}
    className="text-primary hover:underline disabled:opacity-50"
  >
    {testingConfigId === config.configId ? '测试中…' : '测试连接'}
  </button>
</TableCell>
<TableCell>
  <button
    type="button"
    onClick={() => void handleDeleteConfigReal(config.configId, config.modelName)}
    className="text-danger hover:underline"
  >
    删除
  </button>
</TableCell>
```

- 三个弹窗（凭据/添加模型/编辑模型）按速查表替换紫色类名与圆角；底部 Toast 改为 `<Toast message={toastMessage} />`。

- [ ] **Step 2: 验证**

运行：`npm test && npm run build`
Expected: 均退出码 0。

- [ ] **Step 3: 提交**

```powershell
git add src/features/models/pages/ModelConfigPage.tsx
git commit -m "style(models): 模型配置页重写为飞书式两栏布局"
```

### Task 21: 模型治理页飞书化

**Files:**
- Modify: `src/features/models/pages/ModelGovernancePage.tsx`

- [ ] **Step 1: 替换渲染层样式**

按速查表替换：外层 `bg-[#f8f9fc]` → `bg-background`；页头卡片 → `bg-card border-b border-border`；4 张指标卡 → `rounded-md border border-border bg-card`（去掉彩色渐变底）；表格改用 `Table` 原语；编辑弹窗圆角/主色替换；Toast → `<Toast message={toastMessage} />`。全部逻辑与表单字段不变。

- [ ] **Step 2: 验证**

运行：`npm test && npm run build`
Expected: 均退出码 0。

- [ ] **Step 3: 提交**

```powershell
git add src/features/models/pages/ModelGovernancePage.tsx
git commit -m "style(models): 模型治理页飞书化"
```

## Phase 4：提示词与设置

### Task 22: 提示词页重构（列表移入模块面板）

**Files:**
- Modify: `src/features/prompts/pages/PromptManagementPage.tsx`
- Test: `src/features/prompts/pages/PromptManagementPage.test.tsx`

- [ ] **Step 1: 移除页面内左列列表**

删除 `PromptManagementPage` 中 `<aside className="flex w-[270px] ...">` 整段（提示词列表、搜索框），并删除对应状态（`search`、`filteredPrompts` 仅列表使用的部分）。选中项状态 `selectedCode` 保留。

- [ ] **Step 2: 页面结构调整为「工具栏 + 编辑器 + 检查器」**

- 外层：`bg-[#f6f7fb]` → `bg-background`，`overflow-hidden` 保留；
- 顶部工具栏：`h-14 border-b` → `h-11 border-b border-border bg-card px-4`，Studio 徽标改为 `Tag variant="info"`；
- 编辑器区域：行号列 `bg-muted text-tertiary`，正文 textarea 类名按速查表替换；
- 右侧检查器：`border-[#e8ebf1]` → `border-border`，Tab 改用 `Tabs` 组件（发布信息/版本历史）；
- 弹窗（预览/发布/回滚/Diff/编辑定义）按速查表替换；Toast → `<Toast message={toastMessage} />`。

- [ ] **Step 3: 验证**

运行：`npx vitest run src/features/prompts && npm run build`
Expected: 均退出码 0；如测试断言列表标题在页面内，改为断言模块面板（`ModelPanel`）中的"提示词管理"链接。

- [ ] **Step 4: 提交**

```powershell
git add src/features/prompts/pages/PromptManagementPage.tsx src/features/prompts/pages/PromptManagementPage.test.tsx
git commit -m "style(prompts): 提示词页面重构为飞书式编辑器布局"
```

### Task 23: 新增设置占位页

**Files:**
- Create: `src/features/settings/SettingsPage.tsx`

- [ ] **Step 1: 实现 SettingsPage.tsx**

```tsx
import { Settings } from 'lucide-react'

/** 设置占位页：后端能力未接入。 */
export function SettingsPage() {
  return (
    <div className="flex h-full min-h-0 flex-1 items-center justify-center bg-background">
      <div className="text-center">
        <Settings className="mx-auto size-8 text-tertiary" aria-hidden="true" />
        <h1 className="mt-3 text-base font-semibold text-foreground">设置</h1>
        <p className="mt-1 text-xs text-tertiary">个人偏好与系统设置将在后续阶段接入。</p>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: 验证构建**

运行：`npm run build`
Expected: 退出码 0。

- [ ] **Step 3: 提交**

```powershell
git add src/features/settings/SettingsPage.tsx
git commit -m "feat(settings): 新增设置占位页"
```

## Phase 5：收尾

### Task 24: 全量验证与文档同步

**Files:**
- Modify: `README.md`（如前端验证命令/页面说明变化）

- [ ] **Step 1: 全量测试与构建**

运行：
```powershell
npm test
npm run build
```
Expected: 均退出码 0，无 TypeScript 错误。

- [ ] **Step 2: 静态检查**

运行：
```powershell
git status --short
git diff --check
```
Expected: `git diff --check` 无空白错误；未纳入提交的仅 `.superpowers/` 等排除项。

- [ ] **Step 3: 手动回归清单**

启动后端与 `npm run dev`，逐项验证：
1. 对话：发送消息流式输出、停止生成、重试失败消息、切换会话；
2. 知识库：上传文档、状态轮询、分块浏览/编辑、删除文档；
3. 模型：厂商切换、添加/编辑模型、测试连接、删除模型；
4. 路由：列表加载、新建/删除路由（如有弹窗）;
5. 提示词：预览、提交发布、发布配置、回滚、Diff；
6. 外壳：图标栏切换模块、面板折叠、顶栏样式。

- [ ] **Step 4: 更新 README 前端说明**

如页面路由或验证命令有变化，同步 `README.md` 中 `nexa-rag-front` 一节。

- [ ] **Step 5: 提交**

```powershell
git add README.md
git commit -m "docs(ui): 同步前端飞书化重构说明"
```

---

## 3. 自查记录

**Spec 覆盖：** 设计文档第 3 节（设计系统）→ Task 1-9；第 4 节（外壳与导航）→ Task 10-14；第 5.1（对话页）→ Task 15；5.2（知识库）→ Task 16-17；5.3-5.5（模型配置/路由/治理）→ Task 18-21；5.6-5.7（提示词/设置）→ Task 22-23；第 6 节（测试与验证）→ Task 24。

**占位扫描：** 无 TBD/TODO；Task 19 中"新建路由弹窗交互在 Task 20 完成"已明确定义，页面保持可运行。

**类型一致性：** `ModelRouteConfigItem`、`getModelRouteConfigs` 等名称在 Task 18 定义并在 Task 19 引用；`Tag`、`Table`、`Pagination`、`Tabs`、`Toast` 组件名称与导入路径在 Task 4-8 定义并在后续任务引用，保持一致。
