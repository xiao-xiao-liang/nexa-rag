import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getDocument, getDocumentChunks, getDocumentProcessStatus, processDocument, retryDocument } from '../api/document-api'
import { DocumentDetailPage } from './DocumentDetailPage'

vi.mock('../api/document-api', () => ({
  getDocument: vi.fn(),
  getDocumentChunks: vi.fn(),
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

function renderDetail(path = '/knowledge-base/8') {
  render(<MemoryRouter initialEntries={[path]}><Routes><Route path="/knowledge-base/:documentId" element={<DocumentDetailPage />} /><Route path="/knowledge-base" element={<p>知识库文档</p>} /></Routes></MemoryRouter>)
}

describe('文档详情页面', () => {
  beforeEach(() => {
    vi.mocked(getDocument).mockResolvedValue(detail())
    vi.mocked(getDocumentProcessStatus).mockResolvedValue({ documentId: 8, processId: null, status: 'UPLOADED', messageStatus: null, consumedTimes: 0, failureStage: null, failureReason: null })
    vi.mocked(getDocumentChunks).mockResolvedValue({ records: [], total: 0, current: 1, size: 20, pages: 0 })
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

    await waitFor(() => expect(processDocument).toHaveBeenCalledWith(8, expect.anything()))
  })

  it('失败文档应显示失败原因并调用专用重试接口', async () => {
    vi.mocked(getDocument).mockResolvedValue(detail('FAILED'))
    vi.mocked(getDocumentProcessStatus).mockResolvedValue({ documentId: 8, processId: 'p-8', status: 'FAILED', messageStatus: null, consumedTimes: 1, failureStage: 'INDEXING', failureReason: '向量写入失败' })
    vi.mocked(retryDocument).mockResolvedValue({ documentId: 8, processId: 'p-8-retry', status: 'QUEUED', messageStatus: null, consumedTimes: 2, failureStage: null, failureReason: null })
    const user = userEvent.setup()
    renderDetail()

    expect(await screen.findByText(/向量写入失败/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '重新处理' }))

    expect(retryDocument).toHaveBeenCalledWith(8, expect.anything())
  })

  it('仅已索引文档应加载文本分块且不展示文件地址', async () => {
    vi.mocked(getDocument).mockResolvedValue(detail('INDEXED'))
    vi.mocked(getDocumentProcessStatus).mockResolvedValue({ documentId: 8, processId: 'p-8', status: 'INDEXED', messageStatus: null, consumedTimes: 1, failureStage: null, failureReason: null })
    vi.mocked(getDocumentChunks).mockResolvedValue({ records: [{ chunkId: 'c-1', documentId: 8, chunkOrder: 1, text: '员工手册第一段', status: 'INDEXED' }], total: 1, current: 1, size: 20, pages: 1 })
    renderDetail()

    expect(await screen.findByText('员工手册第一段')).toBeInTheDocument()
    expect(getDocumentChunks).toHaveBeenCalledWith(8, 1, 20, expect.anything())
    expect(screen.queryByText('https://example.com/original')).not.toBeInTheDocument()
    expect(screen.queryByText('https://example.com/parsed')).not.toBeInTheDocument()
  })

  it('非法文档地址应提供返回列表操作', () => {
    renderDetail('/knowledge-base/invalid')

    expect(screen.getByText('文档地址无效')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '返回文档列表' })).toHaveAttribute('href', '/knowledge-base')
  })
})
