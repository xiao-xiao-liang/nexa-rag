import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getDocument, getDocumentChunks, getDocumentOverview, getDocumentProcessStatus, processDocument, retryDocument, type DocumentChunk } from '../api/document-api'
import { DocumentDetailPage } from './DocumentDetailPage'

vi.mock('../api/document-api', () => ({
  getDocument: vi.fn(),
  getDocumentChunks: vi.fn(),
  getDocumentOverview: vi.fn(),
  getDocumentProcessStatus: vi.fn(),
  processDocument: vi.fn(),
  retryDocument: vi.fn(),
}))

const detail = (status: 'UPLOADED' | 'FAILED' | 'INDEXED' = 'UPLOADED') => ({
  documentId: 8,
  title: '员工手册',
  description: '内部制度说明',
  originalFileName: '员工手册.pdf',
  fileType: 'PDF',
  fileSize: 1024,
  status,
  originalFileUrl: 'https://example.com/original',
  parsedFileUrl: 'https://example.com/parsed',
  processConfigJson: null,
})

const indexedStatus = { documentId: 8, processId: 'p-8', status: 'INDEXED' as const, messageStatus: null, consumedTimes: 1, failureStage: null, failureReason: null }

const overview = {
  documentId: '8',
  title: '员工手册',
  description: '内部制度说明',
  originalFileName: '员工手册.pdf',
  fileType: 'PDF',
  fileSize: 1024,
  status: 'INDEXED' as const,
  sourceType: 'LOCAL',
  sourceUrl: null,
  processConfigJson: JSON.stringify({
    splitConfig: {
      splitStrategy: 'PARENT_MARKDOWN',
      chunkSize: 500,
      chunkOverlap: 50,
      markdown: { titleLevel: 3, stripHeaders: false, preserveCodeBlock: true, createParentForOversized: true },
    },
    parseConfig: { enableOcr: true, enableImageDescription: false },
    indexConfig: { enabled: true, vectorEnabled: true, keywordEnabled: true },
  }),
  createTime: '2026-08-13T10:00:00',
  updateTime: '2026-08-13T11:00:00',
  chunkStatistics: { total: 12, indexed: 10, failed: 1, skipped: 0, pending: 1 },
}

function chunkPage(records: DocumentChunk[], current = 1, pages = 1) {
  return { records, total: records.length, current, size: 20, pages }
}

function renderDetail(path = '/knowledge-base/8') {
  render(<MemoryRouter initialEntries={[path]}><Routes><Route path="/knowledge-base/:documentId" element={<DocumentDetailPage />} /><Route path="/knowledge-base" element={<p>知识库文档</p>} /></Routes></MemoryRouter>)
}

describe('文档详情页面', () => {
  beforeEach(() => {
    vi.mocked(getDocument).mockResolvedValue(detail())
    vi.mocked(getDocumentProcessStatus).mockResolvedValue({ documentId: 8, processId: null, status: 'UPLOADED', messageStatus: null, consumedTimes: 0, failureStage: null, failureReason: null })
    vi.mocked(getDocumentChunks).mockResolvedValue({ records: [], total: 0, current: 1, size: 20, pages: 0 })
    vi.mocked(getDocumentOverview).mockResolvedValue(overview)
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('已上传文档应显示开始处理操作', async () => {
    vi.mocked(processDocument).mockResolvedValue({ documentId: 8, processId: 'p-8', status: 'QUEUED', messageStatus: null, consumedTimes: 0, failureStage: null, failureReason: null })
    const user = userEvent.setup()
    renderDetail()

    await user.click(await screen.findByRole('button', { name: '开始处理' }))

    await waitFor(() => expect(processDocument).toHaveBeenCalledWith('8', expect.anything()))
  })

  it('失败文档应显示失败原因并调用专用重试接口', async () => {
    vi.mocked(getDocument).mockResolvedValue(detail('FAILED'))
    vi.mocked(getDocumentProcessStatus).mockResolvedValue({ documentId: 8, processId: 'p-8', status: 'FAILED', messageStatus: null, consumedTimes: 1, failureStage: 'INDEXING', failureReason: '向量写入失败' })
    vi.mocked(retryDocument).mockResolvedValue({ documentId: 8, processId: 'p-8-retry', status: 'QUEUED', messageStatus: null, consumedTimes: 2, failureStage: null, failureReason: null })
    const user = userEvent.setup()
    renderDetail()

    expect(await screen.findByText(/向量写入失败/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '重新处理' }))

    expect(retryDocument).toHaveBeenCalledWith('8', expect.anything())
  })

  it('仅已索引文档应加载文本分块且不展示文件地址', async () => {
    vi.mocked(getDocument).mockResolvedValue(detail('INDEXED'))
    vi.mocked(getDocumentProcessStatus).mockResolvedValue(indexedStatus)
    vi.mocked(getDocumentChunks).mockResolvedValue({ records: [{ chunkId: 'c-1', documentId: 8, chunkOrder: 1, text: '员工手册第一段', status: 'INDEXED' }], total: 1, current: 1, size: 20, pages: 1 })
    const user = userEvent.setup()
    renderDetail()

    const chunkCard = await screen.findByRole('button', { name: '查看分块 1' })
    expect(screen.queryByRole('region', { name: '分块完整内容' })).not.toBeInTheDocument()
    await user.click(chunkCard)
    expect(screen.getByRole('region', { name: '分块完整内容' })).toHaveTextContent('员工手册第一段')
    expect(getDocumentChunks).toHaveBeenCalledWith('8', 1, 20, expect.anything())
    expect(screen.queryByText('https://example.com/original')).not.toBeInTheDocument()
    expect(screen.queryByText('https://example.com/parsed')).not.toBeInTheDocument()
  })

  it('点击知识块卡片应打开整页右侧抽屉，关闭后收起', async () => {
    vi.mocked(getDocument).mockResolvedValue(detail('INDEXED'))
    vi.mocked(getDocumentProcessStatus).mockResolvedValue(indexedStatus)
    vi.mocked(getDocumentChunks).mockResolvedValue({ records: [{ chunkId: 'c-1', documentId: 8, chunkOrder: 1, text: '第一页内容', status: 'INDEXED' }], total: 1, current: 1, size: 20, pages: 1 })
    const user = userEvent.setup()
    renderDetail()

    expect(screen.queryByRole('region', { name: '分块完整内容' })).not.toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: '查看分块 1' }))
    expect(screen.getByRole('complementary', { name: '知识块详情抽屉' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: '分块完整内容' })).toHaveTextContent('第一页内容')
    expect(screen.getByText('来源：员工手册.pdf')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '关闭分块内容' }))
    expect(screen.queryByRole('region', { name: '分块完整内容' })).not.toBeInTheDocument()
  })

  it('抽屉内编辑保存后应就地更新预览内容', async () => {
    vi.mocked(getDocument).mockResolvedValue(detail('INDEXED'))
    vi.mocked(getDocumentProcessStatus).mockResolvedValue(indexedStatus)
    vi.mocked(getDocumentChunks).mockResolvedValue({ records: [{ chunkId: 'c-1', documentId: 8, chunkOrder: 1, text: '原始内容', status: 'INDEXED' }], total: 1, current: 1, size: 20, pages: 1 })
    const user = userEvent.setup()
    renderDetail()

    await user.click(await screen.findByRole('button', { name: '查看分块 1' }))
    await user.click(screen.getByRole('button', { name: '编辑' }))
    const editor = screen.getByLabelText('编辑分块内容')
    await user.clear(editor)
    await user.type(editor, '修改后的内容')
    await user.click(screen.getByRole('button', { name: '保存修改' }))

    expect(screen.getByRole('region', { name: '分块完整内容' })).toHaveTextContent('修改后的内容')
    expect(screen.getByText('已保存')).toBeInTheDocument()
  })

  it('文档概览应展示处理结果 KPI、配置分组与文档信息', async () => {
    vi.mocked(getDocument).mockResolvedValue(detail('INDEXED'))
    vi.mocked(getDocumentProcessStatus).mockResolvedValue(indexedStatus)
    const user = userEvent.setup()
    renderDetail()

    await user.click(await screen.findByRole('button', { name: '文档概览' }))

    expect(await screen.findByText('父子 Markdown')).toBeInTheDocument()
    expect(screen.getByText('500 字符 · 重叠 50 · H3')).toBeInTheDocument()
    expect(screen.getByText('处理结果')).toBeInTheDocument()
    expect(screen.getByText('总分块')).toBeInTheDocument()
    expect(screen.getByText('12')).toBeInTheDocument()
    expect(screen.getByText('10')).toBeInTheDocument()
    expect(screen.getByText('本地文件')).toBeInTheDocument()
    expect(screen.getByText('2026-08-13 10:00')).toBeInTheDocument()
    expect(screen.getByText('内部制度说明')).toBeInTheDocument()
  })

  it('概览中可查看完整处理配置 JSON', async () => {
    vi.mocked(getDocument).mockResolvedValue(detail('INDEXED'))
    vi.mocked(getDocumentProcessStatus).mockResolvedValue(indexedStatus)
    const user = userEvent.setup()
    renderDetail()

    await user.click(await screen.findByRole('button', { name: '文档概览' }))
    await user.click(await screen.findByRole('button', { name: '查看完整配置' }))

    expect(screen.getByRole('dialog')).toHaveTextContent('PARENT_MARKDOWN')
    expect(screen.getByRole('button', { name: '复制' })).toBeInTheDocument()
  })

  it('已索引文档应将知识块放入工作区布局', async () => {
    vi.mocked(getDocument).mockResolvedValue(detail('INDEXED'))
    vi.mocked(getDocumentProcessStatus).mockResolvedValue(indexedStatus)
    vi.mocked(getDocumentChunks).mockResolvedValue(chunkPage([]))
    renderDetail()

    expect(await screen.findByRole('region', { name: '知识块工作区' })).toBeInTheDocument()
  })

  it('滚动接近底部时应追加加载下一页分块', async () => {
    vi.mocked(getDocument).mockResolvedValue(detail('INDEXED'))
    vi.mocked(getDocumentProcessStatus).mockResolvedValue(indexedStatus)
    vi.mocked(getDocumentChunks)
      .mockResolvedValueOnce(chunkPage([{ chunkId: 'c-1', documentId: 8, chunkOrder: 1, text: '第一页内容', status: 'INDEXED' }], 1, 2))
      .mockResolvedValueOnce(chunkPage([{ chunkId: 'c-2', documentId: 8, chunkOrder: 2, text: '第二页内容', status: 'INDEXED' }], 2, 2))
    const user = userEvent.setup()
    renderDetail()

    await user.click(await screen.findByRole('button', { name: '查看分块 1' }))
    expect(screen.getByRole('region', { name: '分块完整内容' })).toHaveTextContent('第一页内容')
    await user.click(screen.getByRole('button', { name: '关闭分块内容' }))

    const container = screen.getByTestId('document-detail-scroll')
    Object.defineProperty(container, 'scrollHeight', { value: 2000, configurable: true })
    Object.defineProperty(container, 'clientHeight', { value: 800, configurable: true })
    Object.defineProperty(container, 'scrollTop', { value: 1600, configurable: true })
    fireEvent.scroll(container)

    await waitFor(() => expect(getDocumentChunks).toHaveBeenLastCalledWith('8', 2, 20, expect.anything()))
    expect(await screen.findByRole('button', { name: '查看分块 2' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: '分块完整内容' })).not.toBeInTheDocument()
  })

  it('非法文档地址应提供返回列表操作', () => {
    renderDetail('/knowledge-base/invalid')

    expect(screen.getByText('文档地址无效')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '返回文档列表' })).toHaveAttribute('href', '/knowledge-base')
  })
})
