# 文本分块主从浏览改版 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将文档详情页的文本分块改为“左侧卡片选择、右侧完整阅读”的主从式浏览体验。

**Architecture:** 新增 `DocumentChunkBrowser` 组件，专注于左侧分块卡片、右侧可关闭内容面板和分块展示状态。`DocumentDetailPage` 继续负责文档分块请求、服务端分页和选中状态，在翻页时显式清空选择，避免跨页内容残留。

**Tech Stack:** React 19、TypeScript、Vite、Tailwind CSS、shadcn 风格 Button、Vitest、React Testing Library。

---

## 文件结构

- 新增 `nexa-rag-front/src/features/knowledge-base/components/DocumentChunkBrowser.tsx`：渲染双栏分块工作区、卡片摘要与可关闭阅读面板。
- 新增 `nexa-rag-front/src/features/knowledge-base/components/DocumentChunkBrowser.test.tsx`：验证选择、关闭、选中态和左侧状态反馈。
- 修改 `nexa-rag-front/src/features/knowledge-base/pages/DocumentDetailPage.tsx:1-142`：保存已选分块，接入浏览组件，并在翻页时清空选择。
- 修改 `nexa-rag-front/src/features/knowledge-base/pages/DocumentDetailPage.test.tsx:68-78`：将现有分块断言更新为点击后展示完整内容，并新增翻页清空选择回归测试。

### Task 1: 创建分块主从浏览组件及其行为测试

**Files:**

- Create: `nexa-rag-front/src/features/knowledge-base/components/DocumentChunkBrowser.tsx`
- Create: `nexa-rag-front/src/features/knowledge-base/components/DocumentChunkBrowser.test.tsx`

- [ ] **Step 1: 编写选择与关闭行为的失败测试**

在测试文件中创建带本地状态的测试宿主组件，让点击卡片实际更新 `selectedChunk`，而不是断言模拟函数。使用两条分块记录验证初始右栏不存在、点击后显示完整文本、关闭后阅读区消失：

```tsx
function ChunkBrowserHarness() {
  const [selectedChunk, setSelectedChunk] = useState<DocumentChunk | null>(null)
  return <DocumentChunkBrowser
    chunks={[{ chunkId: 'c-1', documentId: 8, chunkOrder: 1, text: '第一段完整内容', status: 'INDEXED' }, { chunkId: 'c-2', documentId: 8, chunkOrder: 2, text: '第二段完整内容', status: 'INDEXED' }]}
    selectedChunk={selectedChunk}
    loading={false}
    error={null}
    onSelect={setSelectedChunk}
    onClose={() => setSelectedChunk(null)}
    onRetry={vi.fn()}
    pagination={null}
  />
}

it('点击分块卡片后展示完整内容，并可关闭阅读区', async () => {
  const user = userEvent.setup()
  render(<ChunkBrowserHarness />)

  expect(screen.queryByRole('region', { name: '分块完整内容' })).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: '查看分块 2' }))
  expect(screen.getByRole('region', { name: '分块完整内容' })).toHaveTextContent('第二段完整内容')
  await user.click(screen.getByRole('button', { name: '关闭分块内容' }))
  expect(screen.queryByRole('region', { name: '分块完整内容' })).not.toBeInTheDocument()
})
```

- [ ] **Step 2: 运行测试，确认因组件尚不存在而失败**

Run: `npm test -- --run src/features/knowledge-base/components/DocumentChunkBrowser.test.tsx`

Expected: FAIL，提示无法解析 `./DocumentChunkBrowser`。

- [ ] **Step 3: 实现最小双栏浏览组件**

在 `DocumentChunkBrowser.tsx` 定义以下受控接口；`pagination` 由页面传入，以复用现有服务端分页控件：

```tsx
interface DocumentChunkBrowserProps {
  chunks: DocumentChunk[]
  loading: boolean
  error: string | null
  selectedChunk: DocumentChunk | null
  pagination: ReactNode
  onSelect: (chunk: DocumentChunk) => void
  onClose: () => void
  onRetry: () => void
}
```

按以下结构实现：

```tsx
<div className="mt-4 grid gap-4 lg:grid-cols-[20rem_minmax(0,1fr)]">
  <div className="min-w-0 rounded-xl border bg-slate-50/60 p-3">
    <div className="max-h-[32rem] space-y-2 overflow-y-auto pr-1">
      {chunks.map((chunk) => <button
        key={chunk.chunkId}
        type="button"
        aria-label={`查看分块 ${chunk.chunkOrder}`}
        aria-pressed={selectedChunk?.chunkId === chunk.chunkId}
        onClick={() => onSelect(chunk)}
      >
        <span>分块 {chunk.chunkOrder}</span>
        <span className="line-clamp-2">{chunk.text}</span>
      </button>)}
    </div>
    {pagination}
  </div>
  {selectedChunk && <section aria-label="分块完整内容">
    <header><span>分块 {selectedChunk.chunkOrder}</span><Button aria-label="关闭分块内容" type="button" variant="ghost" size="icon" onClick={onClose}><X /></Button></header>
    <div className="max-h-[32rem] overflow-y-auto whitespace-pre-wrap">{selectedChunk.text}</div>
  </section>}
</div>
```

使用 `cn` 为选中卡片添加 `border-blue-500 bg-blue-50`，未选中卡片添加中性边框与悬停样式；加载、错误和空列表仅渲染在左栏。为组件、关键辅助渲染分支添加简体中文注释。

- [ ] **Step 4: 补充分块卡片与左侧状态测试**

在同一测试文件增加以下断言：

```tsx
it('选中卡片应暴露选中态，摘要最多占两行样式', async () => {
  const user = userEvent.setup()
  render(<ChunkBrowserHarness />)
  const card = screen.getByRole('button', { name: '查看分块 1' })
  expect(card).toHaveAttribute('aria-pressed', 'false')
  expect(card.querySelector('.line-clamp-2')).toHaveTextContent('第一段完整内容')
  await user.click(card)
  expect(card).toHaveAttribute('aria-pressed', 'true')
})
```

另行渲染 `loading`、`error` 和空 `chunks` 状态，验证“正在加载文本分块…”，“重新加载”以及“暂无可展示的文本分块。”均仅在列表组件中出现。

- [ ] **Step 5: 运行组件测试，确认通过**

Run: `npm test -- --run src/features/knowledge-base/components/DocumentChunkBrowser.test.tsx`

Expected: PASS，所有新增组件测试通过。

- [ ] **Step 6: 提交组件与测试**

```bash
git add nexa-rag-front/src/features/knowledge-base/components/DocumentChunkBrowser.tsx nexa-rag-front/src/features/knowledge-base/components/DocumentChunkBrowser.test.tsx
git commit -m "feat(knowledge-ui): 增加分块主从浏览组件"
```

### Task 2: 在文档详情页接入选中状态与分页清理

**Files:**

- Modify: `nexa-rag-front/src/features/knowledge-base/pages/DocumentDetailPage.tsx:1-142`
- Modify: `nexa-rag-front/src/features/knowledge-base/pages/DocumentDetailPage.test.tsx:68-78`

- [ ] **Step 1: 编写详情页的失败回归测试**

将“仅已索引文档应加载文本分块且不展示文件地址”调整为先验证卡片存在且完整内容尚未出现，再点击卡片验证右栏内容。新增翻页测试：第一页选中分块后点击“下一页”，断言右栏消失并请求第二页。

```tsx
it('切换分块页码时应清空已选分块', async () => {
  vi.mocked(getDocument).mockResolvedValue(detail('INDEXED'))
  vi.mocked(getDocumentProcessStatus).mockResolvedValue(indexedStatus)
  vi.mocked(getDocumentChunks)
    .mockResolvedValueOnce(chunkPage([{ chunkId: 'c-1', documentId: 8, chunkOrder: 1, text: '第一页内容', status: 'INDEXED' }], 1, 2))
    .mockResolvedValueOnce(chunkPage([{ chunkId: 'c-2', documentId: 8, chunkOrder: 2, text: '第二页内容', status: 'INDEXED' }], 2, 2))
  const user = userEvent.setup()
  renderDetail()

  await user.click(await screen.findByRole('button', { name: '查看分块 1' }))
  expect(screen.getByRole('region', { name: '分块完整内容' })).toHaveTextContent('第一页内容')
  await user.click(screen.getByRole('button', { name: '下一页' }))
  await waitFor(() => expect(getDocumentChunks).toHaveBeenLastCalledWith(8, 2, 20, expect.anything()))
  expect(screen.queryByRole('region', { name: '分块完整内容' })).not.toBeInTheDocument()
})
```

在测试顶部增加 `indexedStatus` 和 `chunkPage` 辅助数据，确保 `chunkPage` 返回完整 `PageVO<DocumentChunk>`：`records`、`total`、`current`、`size: 20`、`pages`。

- [ ] **Step 2: 运行详情页测试，确认新断言失败**

Run: `npm test -- --run src/features/knowledge-base/pages/DocumentDetailPage.test.tsx`

Expected: FAIL，找不到“查看分块 1”卡片或“分块完整内容”阅读区。

- [ ] **Step 3: 接入浏览组件和选择状态**

在页面状态区新增：

```tsx
const [selectedChunk, setSelectedChunk] = useState<DocumentChunk | null>(null)
```

增加分页变更函数，并仅将该函数传给 `ChunkPagination`：

```tsx
const handleChunkPageChange = (nextPage: number) => {
  // 1. 翻页前清空当前选择，避免右侧保留上一页分块内容。
  setSelectedChunk(null)
  // 2. 更新页码后由现有副作用请求服务端目标页。
  setChunkPage(nextPage)
}
```

将原先 `chunks.records.map` 的纵向全文卡片替换为：

```tsx
<DocumentChunkBrowser
  chunks={chunks.records}
  loading={chunksLoading}
  error={chunksError}
  selectedChunk={selectedChunk}
  onSelect={setSelectedChunk}
  onClose={() => setSelectedChunk(null)}
  onRetry={() => void loadChunks(documentId, chunkPage)}
  pagination={<ChunkPagination page={chunks} pageNum={chunkPage} onChange={handleChunkPageChange} />}
/>
```

保留 `currentStatus !== 'INDEXED'` 的提示、原有请求取消逻辑和处理状态逻辑；不改变 API 调用、分页大小或对象存储地址隐藏规则。

- [ ] **Step 4: 运行详情页测试，确认通过**

Run: `npm test -- --run src/features/knowledge-base/pages/DocumentDetailPage.test.tsx`

Expected: PASS，原有四个测试与新增分页清理测试均通过。

- [ ] **Step 5: 执行针对性构建检查**

Run: `npm run build`

Working directory: `nexa-rag-front`

Expected: TypeScript 编译成功，Vite 生产构建完成。

- [ ] **Step 6: 提交页面接入与回归测试**

```bash
git add nexa-rag-front/src/features/knowledge-base/pages/DocumentDetailPage.tsx nexa-rag-front/src/features/knowledge-base/pages/DocumentDetailPage.test.tsx
git commit -m "feat(knowledge-ui): 改造文本分块浏览体验"
```

### Task 3: 完整回归与视觉验证

**Files:**

- Modify: 无

- [ ] **Step 1: 运行完整前端测试集**

Run: `npm test -- --run`

Working directory: `nexa-rag-front`

Expected: 所有测试文件和测试用例通过。

- [ ] **Step 2: 运行生产构建**

Run: `npm run build`

Working directory: `nexa-rag-front`

Expected: `tsc -b && vite build` 退出码为 0。

- [ ] **Step 3: 浏览器验证主从状态**

在本地已索引文档详情页检查以下状态：

1. 初始左栏出现紧凑卡片，右侧不显示占位文案或内容面板。
2. 点击任一卡片后，右侧显示其完整文本和关闭按钮。
3. 点击关闭按钮后，右侧恢复为空白。
4. 切换分页后，右侧保持空白，左侧更新为新页卡片。

- [ ] **Step 4: 检查最终差异**

Run: `git diff --check && git status --short --branch`

Expected: 无空白字符错误；仅存在本计划的前端实现与测试提交。
