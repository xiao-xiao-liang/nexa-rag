# 知识库单文件上传体验改版实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将知识库上传弹窗改造成支持拖拽、客户端预校验、文件卡片和可恢复错误状态的单文件上传体验。

**Architecture:** `UploadDocumentDialog` 继续管理表单状态和 `uploadDocument` 调用；文件规则收敛为无副作用工具函数，拖拽和文件卡片收敛为无接口依赖的 `FileDropzone`。成功后仍由列表页跳转文档详情。

**Tech Stack:** React 19、TypeScript、Vite、Tailwind CSS 4、Lucide、Radix Dialog、Vitest、Testing Library。

---

## 文件结构

- 新增 `nexa-rag-front/src/features/knowledge-base/file-upload.ts`：格式、100MB 限制、标题与大小格式化。
- 新增 `nexa-rag-front/src/features/knowledge-base/file-upload.test.ts`：纯函数边界测试。
- 新增 `nexa-rag-front/src/features/knowledge-base/components/FileDropzone.tsx`：拖拽、选择、文件卡片、替换与移除。
- 新增 `nexa-rag-front/src/features/knowledge-base/components/FileDropzone.test.tsx`：拖拽和可访问性交互测试。
- 修改 `nexa-rag-front/src/features/knowledge-base/components/UploadDocumentDialog.tsx`：组合上传流程。
- 新增 `nexa-rag-front/src/features/knowledge-base/components/UploadDocumentDialog.test.tsx`：表单状态和接口提交测试。
- 修改 `nexa-rag-front/src/features/knowledge-base/pages/KnowledgeBaseListPage.test.tsx`：上传成功跳转回归测试。

## 任务 1：建立文件规则工具

**文件：**

- 新增：`nexa-rag-front/src/features/knowledge-base/file-upload.ts`
- 新增：`nexa-rag-front/src/features/knowledge-base/file-upload.test.ts`

- [ ] **步骤 1：写失败测试。**

```tsx
import { describe, expect, it } from 'vitest'
import { MAX_DOCUMENT_FILE_SIZE_BYTES, deriveDocumentTitle, formatFileSize, validateUploadFile } from './file-upload'

describe('知识库上传文件规则', () => {
  it('应识别支持格式并拒绝未知格式', () => {
    expect(validateUploadFile(new File(['x'], '员工手册.PDF'))).toBeNull()
    expect(validateUploadFile(new File(['x'], '脚本.exe'))).toContain('暂不支持 .exe 格式')
  })
  it('应以 100MB 为客户端校验边界', () => {
    const file = new File(['x'], '资料.pdf')
    Object.defineProperty(file, 'size', { value: MAX_DOCUMENT_FILE_SIZE_BYTES + 1 })
    expect(validateUploadFile(file)).toBe('文件大小超过 100MB 限制，请选择更小的文件。')
  })
  it('应推导标题并格式化大小', () => {
    expect(deriveDocumentTitle('员工手册.v2.docx')).toBe('员工手册.v2')
    expect(formatFileSize(1_572_864)).toBe('1.5 MB')
  })
})
```

- [ ] **步骤 2：运行测试确认失败。**

```powershell
npm --prefix nexa-rag-front test -- --run src/features/knowledge-base/file-upload.test.ts
```

预期：失败信息包含 `Failed to resolve import "./file-upload"`。

- [ ] **步骤 3：实现文件规则。**

在 `file-upload.ts` 定义以下稳定接口：

```ts
export const MAX_DOCUMENT_FILE_SIZE_BYTES = 100 * 1024 * 1024
export function getFileExtension(fileName: string): string
export function getFileTypeLabel(fileName: string): string
export function deriveDocumentTitle(fileName: string): string
export function formatFileSize(bytes: number): string
export function validateUploadFile(file: File): string | null
```

支持集合固定为 `pdf/doc/docx/xls/xlsx/csv/ppt/pptx/md/markdown/txt`；格式名称固定为 `PDF`、`Word`、`Excel/CSV`、`PPT`、`Markdown`、`TXT`。未知扩展名返回“暂不支持 .扩展名 格式，请选择 PDF、Word、Excel/CSV、PPT、Markdown 或 TXT 文件。”；超过 100MB 返回“文件大小超过 100MB 限制，请选择更小的文件。”。

- [ ] **步骤 4：运行测试确认通过。**

```powershell
npm --prefix nexa-rag-front test -- --run src/features/knowledge-base/file-upload.test.ts
```

预期：3 项测试通过。

- [ ] **步骤 5：提交。**

```powershell
git add nexa-rag-front/src/features/knowledge-base/file-upload.ts nexa-rag-front/src/features/knowledge-base/file-upload.test.ts
git commit -m "feat(knowledge-ui): 增加上传文件校验工具"
```

## 任务 2：实现拖拽区和文件卡片

**文件：**

- 新增：`nexa-rag-front/src/features/knowledge-base/components/FileDropzone.tsx`
- 新增：`nexa-rag-front/src/features/knowledge-base/components/FileDropzone.test.tsx`

- [ ] **步骤 1：写失败测试。**

```tsx
it('拖入文件后应通知调用方并给出拖入反馈', () => {
  const onFileChange = vi.fn()
  render(<FileDropzone file={null} disabled={false} error={null} onFileChange={onFileChange} onRemove={vi.fn()} />)
  const dropzone = screen.getByRole('button', { name: '选择要上传的知识库文件' })
  fireEvent.dragEnter(dropzone, { dataTransfer: { files: [] } })
  expect(dropzone).toHaveAttribute('data-dragging', 'true')
  fireEvent.drop(dropzone, { dataTransfer: { files: [new File(['x'], '员工手册.pdf')] } })
  expect(onFileChange).toHaveBeenCalledWith(expect.objectContaining({ name: '员工手册.pdf' }))
})

it('已选文件应展示卡片并支持移除', async () => {
  const onRemove = vi.fn()
  render(<FileDropzone file={new File(['x'], '员工手册.pdf')} disabled={false} error={null} onFileChange={vi.fn()} onRemove={onRemove} />)
  expect(screen.getByText('员工手册.pdf')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: '移除员工手册.pdf' }))
  expect(onRemove).toHaveBeenCalledOnce()
})
```

- [ ] **步骤 2：运行测试确认失败。**

```powershell
npm --prefix nexa-rag-front test -- --run src/features/knowledge-base/components/FileDropzone.test.tsx
```

预期：失败信息包含 `Failed to resolve import "./FileDropzone"`。

- [ ] **步骤 3：实现组件。**

组件只接收 `file`、`disabled`、`error`、`onFileChange(file)`、`onRemove()`。初始态使用与隐藏单文件 input 相邻的、可聚焦 `role="button"` 容器；`accept` 为 `.pdf,.doc,.docx,.xls,.xlsx,.csv,.ppt,.pptx,.md,.markdown,.txt`。点击、Enter、Space 触发原生选择；拖拽事件必须 `preventDefault()`，仅取第一个文件。不得将 input 嵌套进原生 button。

初始态必须显示“拖拽文件到这里”“或点击选择文件”和“支持 PDF、Word、Excel/CSV、PPT、Markdown、TXT，最大 100MB”。已选态显示类型图标、文件名、格式、格式化大小、“更换文件”与“移除 文件名”。错误使用 `role="alert"`。提交中传入 `disabled=true` 时，所有选择、替换和移除入口禁用。

- [ ] **步骤 4：运行组件测试确认通过。**

```powershell
npm --prefix nexa-rag-front test -- --run src/features/knowledge-base/components/FileDropzone.test.tsx
```

预期：拖拽、卡片、移除、键盘入口和禁用状态测试通过。

- [ ] **步骤 5：提交。**

```powershell
git add nexa-rag-front/src/features/knowledge-base/components/FileDropzone.tsx nexa-rag-front/src/features/knowledge-base/components/FileDropzone.test.tsx
git commit -m "feat(knowledge-ui): 增加文档拖拽上传区"
```

## 任务 3：改造上传弹窗并回归路由

**文件：**

- 修改：`nexa-rag-front/src/features/knowledge-base/components/UploadDocumentDialog.tsx`
- 新增：`nexa-rag-front/src/features/knowledge-base/components/UploadDocumentDialog.test.tsx`
- 修改：`nexa-rag-front/src/features/knowledge-base/pages/KnowledgeBaseListPage.test.tsx`

- [ ] **步骤 1：写失败测试。**

```tsx
it('选择有效文件后应自动填入可编辑标题，描述默认收起', async () => {
  renderDialog()
  await userEvent.upload(screen.getByLabelText('选择要上传的知识库文件'), new File(['x'], '员工手册.pdf'))
  expect(screen.getByRole('textbox', { name: '文档标题' })).toHaveValue('员工手册')
  expect(screen.queryByRole('textbox', { name: '文档描述' })).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: '添加描述（可选）' }))
  expect(screen.getByRole('textbox', { name: '文档描述' })).toBeInTheDocument()
})

it('接口失败后应保留文件和已填写元数据', async () => {
  vi.mocked(uploadDocument).mockRejectedValueOnce(new Error('网络请求失败，请稍后重试'))
  renderDialog()
  await userEvent.upload(screen.getByLabelText('选择要上传的知识库文件'), new File(['x'], '员工手册.pdf'))
  await userEvent.click(screen.getByRole('button', { name: '添加描述（可选）' }))
  await userEvent.type(screen.getByRole('textbox', { name: '文档描述' }), '内部制度')
  await userEvent.click(screen.getByRole('button', { name: '开始上传' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('网络请求失败，请稍后重试')
  expect(screen.getByText('员工手册.pdf')).toBeInTheDocument()
  expect(screen.getByRole('textbox', { name: '文档描述' })).toHaveValue('内部制度')
})
```

还要覆盖：超限文件不调用接口；提交参数为 `{ file, title, description }`；等待接口时主按钮为“正在提交并创建处理任务”且不可移除；成功调用 `onUploaded(documentId)`。

- [ ] **步骤 2：运行测试确认失败。**

```powershell
npm --prefix nexa-rag-front test -- --run src/features/knowledge-base/components/UploadDocumentDialog.test.tsx
```

预期：找不到“选择要上传的知识库文件”或“添加描述（可选）”。

- [ ] **步骤 3：组合 `FileDropzone` 与现有 API。**

新增 `descriptionExpanded`、`fileError`、`uploadError` 状态。文件选择时先调用 `validateUploadFile`；合法文件写入 file，标题用 `deriveDocumentTitle(file.name)` 自动填入，清理两类错误。替换文件也自动覆盖标题；移除文件清理 file、title 和 fileError。

关闭弹窗仅在非提交状态清理文件、标题、描述、展开状态和错误。提交继续调用 `uploadDocument({ file, title, description })`；失败只能写入 `uploadError`，不得清理输入；成功调用 `onUploaded(response.documentId)`。描述在用户点击“添加描述（可选）”后才渲染。主操作没有有效文件时禁用，提交时显示“正在提交并创建处理任务”，并禁用取消、关闭、替换和移除。不得显示虚假的百分比进度。

- [ ] **步骤 4：更新列表页回归测试。**

将旧查询名 `选择文件` 改为 `选择要上传的知识库文件`，保留：

```tsx
await waitFor(() => expect(router.state.location.pathname).toBe('/knowledge-base/18'))
```

- [ ] **步骤 5：运行弹窗与列表回归。**

```powershell
npm --prefix nexa-rag-front test -- --run src/features/knowledge-base/components/UploadDocumentDialog.test.tsx src/features/knowledge-base/pages/KnowledgeBaseListPage.test.tsx
```

预期：自动标题、可选描述、客户端拒绝、失败保留、提交禁用和详情跳转均通过。

- [ ] **步骤 6：提交。**

```powershell
git add nexa-rag-front/src/features/knowledge-base/components/UploadDocumentDialog.tsx nexa-rag-front/src/features/knowledge-base/components/UploadDocumentDialog.test.tsx nexa-rag-front/src/features/knowledge-base/pages/KnowledgeBaseListPage.test.tsx
git commit -m "feat(knowledge-ui): 优化文档上传体验"
```

## 任务 4：全量验证

- [ ] **步骤 1：运行完整前端测试。**

```powershell
npm --prefix nexa-rag-front test -- --run
```

预期：所有 Vitest 测试通过，且不出现未处理异常。

- [ ] **步骤 2：运行生产构建和差异检查。**

```powershell
npm --prefix nexa-rag-front run build
git diff --check
```

预期：TypeScript、Vite 构建成功，`git diff --check` 无输出。

- [ ] **步骤 3：执行人工浏览器检查。**

1. 打开 `/knowledge-base` 并进入上传弹窗。
2. 验证拖拽区、格式/100MB说明、键盘选择入口和高亮反馈。
3. 选择有效 PDF，确认文件卡片、自动标题和可选描述入口。
4. 选择 `.exe` 或大于 100MB 文件，确认不发请求并显示具体原因。
5. 模拟接口失败，确认文件和元数据保留；成功后确认进入 `/knowledge-base/{documentId}`。
