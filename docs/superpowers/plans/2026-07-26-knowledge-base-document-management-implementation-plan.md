# 知识库文档管理一期实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 RAG 对话前端中增加可路由的知识库文档管理，完成上传、服务端分页、处理状态观察、失败重试、分块检查和删除。

**Architecture:** 使用 `react-router-dom` 负责 `/chat`、`/knowledge-base`、`/knowledge-base/:documentId` 路由，并以 `AppShell` 提供窄全局侧栏。文档 API 客户端独立于页面，页面通过 AbortController 和请求序号防止晚到响应写入；详情页只在处理中每 5 秒轮询处理状态，终态和离开页面立即停止。

**Tech Stack:** React 19、TypeScript、Vite、Tailwind CSS 4、shadcn/ui 风格组件、Lucide、React Router、Vitest、Testing Library。

---

## 文件结构

- `nexa-rag-front/src/App.tsx`：只挂载路由。
- `nexa-rag-front/src/app/router.tsx`：声明浏览器路由和根重定向。
- `nexa-rag-front/src/app/AppShell.tsx`：窄全局侧栏与 `Outlet`。
- `nexa-rag-front/src/features/chat/ChatWorkspace.tsx`：从当前 `App.tsx` 迁出的对话工作台。
- `nexa-rag-front/src/features/knowledge-base/api/document-api.ts`：文档 REST 类型与请求函数。
- `nexa-rag-front/src/features/knowledge-base/document-status.ts`：状态文案、处理中和终态判定。
- `nexa-rag-front/src/components/ui/input.tsx`：上传标题使用的单行输入组件。
- `nexa-rag-front/src/features/knowledge-base/components/UploadDocumentDialog.tsx`：只收集文件、标题和描述的上传弹窗。
- `nexa-rag-front/src/features/knowledge-base/components/DocumentListTable.tsx`：文档列表、分页和删除确认。
- `nexa-rag-front/src/features/knowledge-base/hooks/useDocumentStatusPolling.ts`：详情页状态轮询生命周期。
- `nexa-rag-front/src/features/knowledge-base/pages/KnowledgeBaseListPage.tsx`：文档管理页面。
- `nexa-rag-front/src/features/knowledge-base/pages/DocumentDetailPage.tsx`：文档详情、处理操作和分块分页。

## 任务 1：引入路由与全局模块外壳

**文件：**

- 修改：`nexa-rag-front/package.json`
- 修改：`nexa-rag-front/package-lock.json`
- 修改：`nexa-rag-front/src/App.tsx`
- 新增：`nexa-rag-front/src/app/router.tsx`
- 新增：`nexa-rag-front/src/app/AppShell.tsx`
- 新增：`nexa-rag-front/src/features/chat/ChatWorkspace.tsx`
- 新增：`nexa-rag-front/src/features/knowledge-base/pages/KnowledgeBaseListPage.tsx`
- 新增：`nexa-rag-front/src/features/knowledge-base/pages/DocumentDetailPage.tsx`
- 新增：`nexa-rag-front/src/app/router.test.tsx`

- [ ] **步骤 1：增加 Router 依赖和路由失败测试。**

    ```powershell
    npm --prefix nexa-rag-front install react-router-dom
    ```

    在 `router.test.tsx` 使用 `createMemoryRouter` 断言：`/` 重定向到 `/chat`；`/chat` 显示现有“开始一段 RAG 对话”；`/knowledge-base` 暂时显示由测试桩提供的“知识库文档”。

    ```tsx
    it('根路径应重定向到对话路由', async () => {
      const router = createMemoryRouter(routes, { initialEntries: ['/'] })
      render(<RouterProvider router={router} />)
      await waitFor(() => expect(router.state.location.pathname).toBe('/chat'))
    })
    ```

- [ ] **步骤 2：运行测试，确认其因路由模块不存在而失败。**

    ```powershell
    npm --prefix nexa-rag-front test -- --run src/app/router.test.tsx
    ```

    预期：失败信息包含 `Cannot find module './router'` 或缺少 `react-router-dom`。

- [ ] **步骤 3：实现 `AppShell`、路由和对话工作台迁移。**

    将当前 `App.tsx` 中的 RAG 对话组件完整迁入 `ChatWorkspace.tsx`，保留其会话加载、流式生成、取消、重试和 Agent 本地元数据行为。将其根元素从 `main` 调整为可嵌入 `Outlet` 的 `section`。

    ```tsx
    // src/app/router.tsx
    export const routes: RouteObject[] = [{
      path: '/', element: <AppShell />, children: [
        { index: true, element: <Navigate to="/chat" replace /> },
        { path: 'chat', element: <ChatWorkspace /> },
        { path: 'knowledge-base', element: <KnowledgeBaseListPage /> },
        { path: 'knowledge-base/:documentId', element: <DocumentDetailPage /> },
      ],
    }]
    export const router = createBrowserRouter(routes)
    ```

    ```tsx
    // src/app/AppShell.tsx
    const navigation = [
      { to: '/chat', label: '对话', icon: MessageSquare },
      { to: '/knowledge-base', label: '知识库', icon: LibraryBig },
    ]
    export function AppShell() {
      return <div className="flex h-dvh bg-background">
        <nav aria-label="全局导航" className="flex w-14 shrink-0 flex-col items-center border-r bg-slate-950 py-3">
          {navigation.map(({ to, label, icon: Icon }) => <NavLink key={to} to={to} aria-label={label}>{({ isActive }) => <Icon className={cn('size-5', isActive && 'text-white')} />}</NavLink>)}
        </nav>
        <Outlet />
      </div>
    }
    ```

    `App.tsx` 最终仅为：

    ```tsx
    export default function App() {
      return <RouterProvider router={router} />
    }
    ```

    暂时创建分别导出 `知识库文档`、`文档详情` 标题的两个页面桩，以使路由在本任务可验证；任务 3 和任务 4 会在相同文件中替换为完整页面。

- [ ] **步骤 4：运行路由与现有对话测试。**

    ```powershell
    npm --prefix nexa-rag-front test -- --run src/app/router.test.tsx src/App.test.tsx
    ```

    预期：路由断言和既有 RAG 对话测试全部通过。

- [ ] **步骤 5：提交路由骨架。**

    ```powershell
    git add nexa-rag-front/package.json nexa-rag-front/package-lock.json nexa-rag-front/src/App.tsx nexa-rag-front/src/app nexa-rag-front/src/features/chat/ChatWorkspace.tsx nexa-rag-front/src/features/knowledge-base/pages/KnowledgeBaseListPage.tsx nexa-rag-front/src/features/knowledge-base/pages/DocumentDetailPage.tsx
    git commit -m "feat(knowledge-ui): 增加中台路由与全局导航"
    ```

## 任务 2：建立文档接口客户端与状态工具

**文件：**

- 新增：`nexa-rag-front/src/features/knowledge-base/api/document-api.ts`
- 新增：`nexa-rag-front/src/features/knowledge-base/api/document-api.test.ts`
- 新增：`nexa-rag-front/src/features/knowledge-base/document-status.ts`
- 新增：`nexa-rag-front/src/features/knowledge-base/document-status.test.ts`

- [ ] **步骤 1：写 API 和状态工具失败测试。**

    覆盖以下契约：

    ```tsx
    expect(fetchMock).toHaveBeenCalledWith('/api/documents?pageNum=2&pageSize=20', undefined)
    expect(formData.get('file')).toBe(file)
    expect(formData.get('request')).toBeInstanceOf(Blob)
    expect(isProcessingStatus('PARSING')).toBe(true)
    expect(isTerminalStatus('INDEXED')).toBe(true)
    expect(statusLabel('FAILED')).toBe('处理失败')
    ```

    `uploadDocument` 测试必须断言：请求使用 `FormData`，`request` part 是仅包含 `title`、`description` 的 JSON Blob，且没有 `splitConfig`、`parseConfig`、`indexConfig`。同时覆盖 `GET /api/documents/{id}`、`GET /process-status`、`GET /chunks?pageNum&pageSize`、`POST /process`、`POST /retry` 和 `DELETE /api/documents/{id}`。

- [ ] **步骤 2：运行测试，确认其失败。**

    ```powershell
    npm --prefix nexa-rag-front test -- --run src/features/knowledge-base/api/document-api.test.ts src/features/knowledge-base/document-status.test.ts
    ```

    预期：失败信息包含缺少 `document-api` 和 `document-status` 模块。

- [ ] **步骤 3：实现文档请求和状态映射。**

    ```ts
    export type DocumentStatus = 'UPLOADED' | 'QUEUED' | 'PARSING' | 'PARSED' | 'CHUNKING' | 'CHUNKED' | 'INDEXING' | 'INDEXED' | 'FAILED'

    export interface DocumentSummary { documentId: number; title: string; originalFileName: string; fileType: string; status: DocumentStatus }
    export interface DocumentProcessStatus { documentId: number; processId: string | null; status: DocumentStatus; messageStatus: string | null; consumedTimes: number | null; failureStage: string | null; failureReason: string | null }

    export function listDocuments(pageNum = 1, pageSize = 20, signal?: AbortSignal) {
      return request<PageVO<DocumentSummary>>(`/api/documents?pageNum=${pageNum}&pageSize=${pageSize}`, signal ? { signal } : undefined)
    }
    ```

    ```ts
    export function uploadDocument(input: UploadDocumentInput, signal?: AbortSignal) {
      const body = new FormData()
      body.append('file', input.file)
      body.append('request', new Blob([JSON.stringify({ title: input.title || null, description: input.description || null })], { type: 'application/json' }))
      return request<UploadDocumentResponse>('/api/documents/upload', { method: 'POST', body, signal })
    }
    ```

    对 `processDocument` 使用 `{ method: 'POST' }`，对 `retryDocument` 使用 `{ method: 'POST' }`，对删除使用 `{ method: 'DELETE' }`。所有 ID 必须使用 `encodeURIComponent(String(documentId))`。

    ```ts
    export const PROCESSING_DOCUMENT_STATUSES = new Set<DocumentStatus>(['QUEUED', 'PARSING', 'CHUNKING', 'INDEXING'])
    export function isProcessingStatus(status: DocumentStatus) { return PROCESSING_DOCUMENT_STATUSES.has(status) }
    export function isTerminalStatus(status: DocumentStatus) { return status === 'INDEXED' || status === 'FAILED' }
    ```

- [ ] **步骤 4：运行 API 与状态测试。**

    ```powershell
    npm --prefix nexa-rag-front test -- --run src/features/knowledge-base/api/document-api.test.ts src/features/knowledge-base/document-status.test.ts
    ```

    预期：所有新增测试通过。

- [ ] **步骤 5：提交接口客户端。**

    ```powershell
    git add nexa-rag-front/src/features/knowledge-base/api nexa-rag-front/src/features/knowledge-base/document-status.ts nexa-rag-front/src/features/knowledge-base/document-status.test.ts
    git commit -m "feat(knowledge-ui): 接入文档管理接口"
    ```

## 任务 3：实现文档列表、上传与删除

**文件：**

- 新增：`nexa-rag-front/src/features/knowledge-base/components/UploadDocumentDialog.tsx`
- 新增：`nexa-rag-front/src/features/knowledge-base/components/DocumentListTable.tsx`
- 新增：`nexa-rag-front/src/components/ui/input.tsx`
- 修改：`nexa-rag-front/src/features/knowledge-base/pages/KnowledgeBaseListPage.tsx`
- 新增：`nexa-rag-front/src/features/knowledge-base/pages/KnowledgeBaseListPage.test.tsx`
- 修改：`nexa-rag-front/src/app/router.tsx`

- [ ] **步骤 1：写列表页失败测试。**

    用 `vi.mock('../api/document-api')` 覆盖 API 客户端，并覆盖：服务端分页请求、列表失败后的重试、上传成功后的路由跳转、删除二次确认和删除当前页最后一条记录后的页码回退。

    ```tsx
    it('上传成功后应跳转到文档详情', async () => {
      vi.mocked(uploadDocument).mockResolvedValue({ documentId: 18, processId: 'p-18', status: 'QUEUED' })
      renderList('/knowledge-base')
      await user.upload(screen.getByLabelText('选择文件'), new File(['内容'], '员工手册.pdf', { type: 'application/pdf' }))
      await user.click(screen.getByRole('button', { name: '开始上传' }))
      await waitFor(() => expect(router.state.location.pathname).toBe('/knowledge-base/18'))
    })
    ```

    ```tsx
    it('删除当前页最后一条记录后应回退上一页', async () => {
      vi.mocked(listDocuments)
        .mockResolvedValueOnce(page([document(21)], 2, 20, 21))
        .mockResolvedValueOnce(page([document(1)], 1, 20, 20))
      vi.mocked(deleteDocument).mockResolvedValue(true)
      renderList('/knowledge-base?page=2')
      await userEvent.click(await screen.findByRole('button', { name: '删除 员工手册.pdf' }))
      await userEvent.click(screen.getByRole('button', { name: '确认删除' }))
      await waitFor(() => expect(listDocuments).toHaveBeenLastCalledWith(1, 20, expect.anything()))
    })
    ```

- [ ] **步骤 2：运行测试，确认其失败。**

    ```powershell
    npm --prefix nexa-rag-front test -- --run src/features/knowledge-base/pages/KnowledgeBaseListPage.test.tsx
    ```

    预期：失败信息包含缺少页面或组件模块。

- [ ] **步骤 3：实现上传弹窗与列表页面。**

    先新增 `components/ui/input.tsx`，沿用当前 `textarea.tsx` 的 `cn`、边框、焦点环和 `forwardRef` 模式：

    ```tsx
    export const Input = React.forwardRef<HTMLInputElement, React.ComponentProps<'input'>>(
      ({ className, type = 'text', ...props }, ref) => <input ref={ref} type={type}
        className={cn('flex h-10 w-full rounded-xl border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring', className)}
        {...props} />,
    )
    Input.displayName = 'Input'
    ```

    `UploadDocumentDialog` 的表单只包含以下控件：

    ```tsx
    <input id="document-file" aria-label="选择文件" type="file" required onChange={onFileChange} />
    <Input aria-label="文档标题" maxLength={256} value={title} onChange={(event) => setTitle(event.target.value)} />
    <Textarea aria-label="文档描述" maxLength={1024} value={description} onChange={(event) => setDescription(event.target.value)} />
    ```

    提交时禁用重复点击；上传失败在弹窗内展示 `ApiError.message` 并保留输入；成功后调用 `onUploaded(documentId)`。

    `KnowledgeBaseListPage` 维护 `pageNum`、`page`、`loading`、`error`、`deleteTarget`，每次页码变化时创建 AbortController；仅在请求仍是最新请求时写入状态。

    ```tsx
    const deleteAndReload = async (documentId: number) => {
      await deleteDocument(documentId)
      const nextPage = page.records.length === 1 && pageNum > 1 ? pageNum - 1 : pageNum
      setPageNum(nextPage)
      if (nextPage === pageNum) await loadPage(nextPage)
    }
    ```

    `DocumentListTable` 用状态徽标渲染 `statusLabel`，单击文档标题或“查看详情”导航到 `/knowledge-base/${documentId}`；删除使用现有 `Dialog` 组件二次确认。分页仅提供上一页、下一页和“第 X / Y 页”，不出现处理状态筛选或搜索控件。

- [ ] **步骤 4：运行列表页测试。**

    ```powershell
    npm --prefix nexa-rag-front test -- --run src/features/knowledge-base/pages/KnowledgeBaseListPage.test.tsx
    ```

    预期：上传、服务端分页、失败重试、删除确认和空页回退测试通过。

- [ ] **步骤 5：提交列表页。**

    ```powershell
    git add nexa-rag-front/src/components/ui/input.tsx nexa-rag-front/src/features/knowledge-base/components/UploadDocumentDialog.tsx nexa-rag-front/src/features/knowledge-base/components/DocumentListTable.tsx nexa-rag-front/src/features/knowledge-base/pages/KnowledgeBaseListPage.tsx nexa-rag-front/src/features/knowledge-base/pages/KnowledgeBaseListPage.test.tsx nexa-rag-front/src/app/router.tsx
    git commit -m "feat(knowledge-ui): 实现文档上传与列表管理"
    ```

## 任务 4：实现文档详情、状态轮询和分块浏览

**文件：**

- 新增：`nexa-rag-front/src/features/knowledge-base/hooks/useDocumentStatusPolling.ts`
- 新增：`nexa-rag-front/src/features/knowledge-base/hooks/useDocumentStatusPolling.test.tsx`
- 修改：`nexa-rag-front/src/features/knowledge-base/pages/DocumentDetailPage.tsx`
- 新增：`nexa-rag-front/src/features/knowledge-base/pages/DocumentDetailPage.test.tsx`
- 修改：`nexa-rag-front/src/app/router.tsx`

- [ ] **步骤 1：写轮询与详情页失败测试。**

    使用 `vi.useFakeTimers()` 验证：处理中每 5 秒请求一次状态，`INDEXED` 或 `FAILED` 停止定时器，卸载时清理定时器和 AbortController。页面测试覆盖 `UPLOADED` 的开始处理、`FAILED` 的重新处理、失败原因、仅 `INDEXED` 加载分块、分块分页和每个区域独立重试。

    ```tsx
    it('处理中应每五秒轮询且进入终态后停止', async () => {
      renderHook(() => useDocumentStatusPolling(8, 'PARSING', onStatus))
      await vi.advanceTimersByTimeAsync(5_000)
      expect(getDocumentProcessStatus).toHaveBeenCalledTimes(1)
      onStatus({ ...processingStatus, status: 'INDEXED' })
      await vi.advanceTimersByTimeAsync(10_000)
      expect(getDocumentProcessStatus).toHaveBeenCalledTimes(1)
    })
    ```

    ```tsx
    it('失败文档应显示原因并调用专用重试接口', async () => {
      vi.mocked(getDocumentProcessStatus).mockResolvedValue({ ...failedStatus, failureStage: 'INDEXING', failureReason: '向量写入失败' })
      renderDetail('/knowledge-base/8')
      expect(await screen.findByText('向量写入失败')).toBeInTheDocument()
      await userEvent.click(screen.getByRole('button', { name: '重新处理' }))
      expect(retryDocument).toHaveBeenCalledWith(8, expect.anything())
    })
    ```

- [ ] **步骤 2：运行测试，确认其失败。**

    ```powershell
    npm --prefix nexa-rag-front test -- --run src/features/knowledge-base/hooks/useDocumentStatusPolling.test.tsx src/features/knowledge-base/pages/DocumentDetailPage.test.tsx
    ```

    预期：失败信息包含缺少轮询 Hook 或详情页面模块。

- [ ] **步骤 3：实现轮询 Hook。**

    ```ts
    export function useDocumentStatusPolling(
      documentId: number | null,
      status: DocumentStatus | null,
      onStatus: (value: DocumentProcessStatus) => void,
      onError: (error: Error) => void,
    ) {
      useEffect(() => {
        if (documentId === null || status === null || !isProcessingStatus(status)) return
        const controller = new AbortController()
        const poll = async () => {
          try { onStatus(await getDocumentProcessStatus(documentId, controller.signal)) }
          catch (error) { if ((error as { name?: string }).name !== 'AbortError') onError(error instanceof Error ? error : new Error('状态查询失败')) }
        }
        void poll()
        const timer = window.setInterval(() => void poll(), 5_000)
        return () => { controller.abort(); window.clearInterval(timer) }
      }, [documentId, status, onStatus, onError])
    }
    ```

    Hook 必须在进入处理中时先立即执行一次状态查询，再开始间隔轮询；`onError` 由页面展示，不得清空最近有效状态。

- [ ] **步骤 4：实现详情页。**

    `DocumentDetailPage` 从 `useParams` 读取 `documentId`，非法或缺失 ID 显示“文档地址无效”并提供返回列表操作。使用独立状态保存 `detailError`、`processError`、`chunksError`，每个错误块提供只重试所属请求的按钮。

    ```tsx
    {document.status === 'UPLOADED' && <Button onClick={submitProcess}>开始处理</Button>}
    {document.status === 'FAILED' && <Button onClick={retryProcess}>重新处理</Button>}
    {document.status === 'INDEXED' && <ChunkList documentId={document.documentId} />}
    {document.status !== 'INDEXED' && <p>文档索引完成后可查看文本分块。</p>}
    ```

    处理操作成功后，用返回的 `DocumentProcessStatus` 更新页面状态并立刻启动轮询。分块请求使用 `pageNum=1&pageSize=20`；上一页、下一页仅在服务端页信息允许时出现。详情中只展示 `title`、`description`、`originalFileName`、`fileType`、`fileSize`、状态与处理信息，禁止渲染 `originalFileUrl`、`parsedFileUrl`。

- [ ] **步骤 5：运行详情与轮询测试。**

    ```powershell
    npm --prefix nexa-rag-front test -- --run src/features/knowledge-base/hooks/useDocumentStatusPolling.test.tsx src/features/knowledge-base/pages/DocumentDetailPage.test.tsx
    ```

    预期：轮询、终态停止、离开清理、处理操作、失败显示、分块条件加载、独立重试和分页测试通过。

- [ ] **步骤 6：提交详情页。**

    ```powershell
    git add nexa-rag-front/src/features/knowledge-base/hooks nexa-rag-front/src/features/knowledge-base/pages/DocumentDetailPage.tsx nexa-rag-front/src/features/knowledge-base/pages/DocumentDetailPage.test.tsx nexa-rag-front/src/app/router.tsx
    git commit -m "feat(knowledge-ui): 实现文档处理详情与分块浏览"
    ```

## 任务 5：全量验证与使用说明

**文件：**

- 修改：`README.md`
- 修改：`nexa-rag-front/src/App.test.tsx`

- [ ] **步骤 1：补充端到端组件级回归测试。**

    在 `App.test.tsx` 增加：从全局“知识库”导航进入列表、上传后跳转详情、使用浏览器返回回到列表的最小链路。测试使用已 mock 的文档 API，断言不出现“处理状态筛选”“查看原文件”和高级处理配置字段。

- [ ] **步骤 2：更新 README 前端说明。**

    在 `README.md` 的 `nexa-rag-front` 章节补充三条说明：

    ```markdown
    - 对话入口为 `/chat`，知识库文档入口为 `/knowledge-base`。
    - 文档上传一期只提交文件、标题和描述，处理配置使用后端默认值。
    - 详情页仅对处理中状态每 5 秒轮询；原文件预览、状态筛选和高级配置见 TODO。
    ```

- [ ] **步骤 3：运行最终验证。**

    ```powershell
    npm --prefix nexa-rag-front test -- --run
    npm --prefix nexa-rag-front run build
    git diff --check
    ```

    预期：Vitest 全部通过、TypeScript 和 Vite 生产构建成功、`git diff --check` 无输出。

- [ ] **步骤 4：提交联调说明。**

    ```powershell
    git add README.md nexa-rag-front/src/App.test.tsx
    git commit -m "docs(knowledge-ui): 补充知识库前端使用说明"
    ```

## 实施完成后端到端检查

在已启动后端的环境中执行以下人工检查：

1. 访问 `/chat`，确认会话列表和流式对话仍可用。
2. 从窄全局侧栏进入 `/knowledge-base`，上传一个小型 PDF 或 Markdown 文件。
3. 确认浏览器自动进入 `/knowledge-base/{documentId}`，详情每 5 秒更新处理中状态。
4. 对失败文档确认显示失败阶段、失败原因及“重新处理”；对已索引文档确认分块可分页读取。
5. 删除文档，确认二次确认、列表刷新和页码回退正确。
