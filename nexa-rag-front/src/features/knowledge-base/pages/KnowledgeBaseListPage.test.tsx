import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PageVO } from '@/shared/api/types'
import type { DocumentSummary } from '../api/document-api'
import { deleteDocument, listDocuments, uploadDocument } from '../api/document-api'
import { KnowledgeBaseListPage } from './KnowledgeBaseListPage'

vi.mock('../api/document-api', () => ({
  deleteDocument: vi.fn(),
  listDocuments: vi.fn(),
  uploadDocument: vi.fn(),
}))

const documentItem = (documentId: number): DocumentSummary => ({
  documentId,
  title: '员工手册',
  originalFileName: '员工手册.pdf',
  fileType: 'PDF',
  status: 'INDEXED',
})

const page = (records: DocumentSummary[], current = 1, pages = 1, total = records.length): PageVO<DocumentSummary> => ({
  records,
  total,
  current,
  size: 20,
  pages,
})

function renderList(initialEntry = '/knowledge-base') {
  const router = createMemoryRouter([
    { path: '/knowledge-base', element: <KnowledgeBaseListPage /> },
    { path: '/knowledge-base/:documentId', element: <p>文档详情</p> },
  ], { initialEntries: [initialEntry] })
  render(<RouterProvider router={router} />)
  return router
}

describe('知识库文档列表页面', () => {
  beforeEach(() => {
    vi.mocked(listDocuments).mockResolvedValue(page([documentItem(8)]))
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('默认展示全部文档列表，概览视图可切换回列表', async () => {
    renderList()

    expect(await screen.findByRole('heading', { name: '全部文档' })).toBeInTheDocument()
    expect(await screen.findByText('员工手册')).toBeInTheDocument()
  })

  it('概览视图下可切换至全部文档表格', async () => {
    const user = userEvent.setup()
    renderList('/knowledge-base?view=overview')

    expect(await screen.findByRole('heading', { name: '知识库' })).toBeInTheDocument()
    expect(screen.getByText('默认知识库')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '查看全部文档' }))

    expect(screen.getByRole('heading', { name: '全部文档' })).toBeInTheDocument()
  })

  it('应按地址中的页码请求服务端分页列表', async () => {
    renderList('/knowledge-base?page=2')

    await waitFor(() => expect(listDocuments).toHaveBeenCalledWith(2, 20, expect.anything()))
    expect(await screen.findByText('员工手册')).toBeInTheDocument()
  })

  it('列表失败后应支持重新加载', async () => {
    vi.mocked(listDocuments).mockRejectedValueOnce(new Error('列表加载失败')).mockResolvedValueOnce(page([]))
    const user = userEvent.setup()
    renderList()

    await user.click(await screen.findByRole('button', { name: '重新加载' }))

    await waitFor(() => expect(listDocuments).toHaveBeenCalledTimes(2))
  })

  it('上传成功后应跳转到文档详情', async () => {
    vi.mocked(uploadDocument).mockResolvedValue({ documentId: 18, processId: 'p-18', status: 'QUEUED' })
    const user = userEvent.setup()
    const router = renderList()

    await user.click(await screen.findByRole('button', { name: '上传文档' }))
    await user.upload(screen.getByLabelText('选择本地文件'), new File(['内容'], '员工手册.pdf', { type: 'application/pdf' }))
    const submitButton = screen.getByRole('button', { name: '开始上传' })
    await waitFor(() => expect(submitButton).toBeEnabled())
    await user.click(submitButton)

    await waitFor(() => expect(router.state.location.pathname).toBe('/knowledge-base/18'))
  })

  it('删除当前页最后一条记录后应回退上一页', async () => {
    vi.mocked(listDocuments)
      .mockResolvedValueOnce(page([documentItem(21)], 2, 2, 21))
      .mockResolvedValueOnce(page([documentItem(1)], 1, 2, 21))
    vi.mocked(deleteDocument).mockResolvedValue(true)
    const user = userEvent.setup()
    renderList('/knowledge-base?page=2')

    await user.click(await screen.findByRole('button', { name: '更多操作' }))
    await user.click(await screen.findByRole('menuitem', { name: '删除' }))
    await user.click(screen.getByRole('button', { name: '确认删除' }))

    await waitFor(() => expect(listDocuments).toHaveBeenLastCalledWith(1, 20, expect.anything()))
  })

  it('勾选文档后可批量删除', async () => {
    vi.mocked(listDocuments).mockResolvedValue(page([documentItem(8), documentItem(9)]))
    vi.mocked(deleteDocument).mockResolvedValue(true)
    const user = userEvent.setup()
    renderList()

    await user.click(await screen.findByRole('checkbox', { name: '全选当前页' }))
    await user.click(screen.getByRole('button', { name: '删除选中' }))
    await user.click(screen.getByRole('button', { name: '确认删除' }))

    await waitFor(() => expect(deleteDocument).toHaveBeenCalledTimes(2))
  })
})
