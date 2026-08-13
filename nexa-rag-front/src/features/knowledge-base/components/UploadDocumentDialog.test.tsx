import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { submitExternalDocument, uploadDocument } from '../api/document-api'
import { UploadDocumentDialog } from './UploadDocumentDialog'

vi.mock('../api/document-api', () => ({
  uploadDocument: vi.fn(),
  submitExternalDocument: vi.fn(),
}))

const markdownSplitConfig = expect.objectContaining({
  splitStrategy: 'PARENT_MARKDOWN',
  chunkSize: 500,
  chunkOverlap: 50,
  markdown: expect.objectContaining({
    titleLevel: 3,
    stripHeaders: false,
    preserveCodeBlock: true,
    createParentForOversized: true,
  }),
})

/** 上传文档弹窗测试。 */
describe('UploadDocumentDialog', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('选择文件后应自动填写标题，描述栏常驻可见', async () => {
    const user = userEvent.setup()
    render(<UploadDocumentDialog open onOpenChange={vi.fn()} onUploaded={vi.fn()} />)

    await user.upload(screen.getByLabelText('选择本地文件'), new File(['内容'], '员工手册.pdf', { type: 'application/pdf' }))

    expect(screen.getByLabelText('文档标题')).toHaveValue('员工手册')
    expect(screen.getByLabelText('文档描述')).toBeInTheDocument()
  })

  it('选择不支持的文件时应给出错误且禁止提交', async () => {
    const user = userEvent.setup({ applyAccept: false })
    render(<UploadDocumentDialog open onOpenChange={vi.fn()} onUploaded={vi.fn()} />)

    await user.upload(screen.getByLabelText('选择本地文件'), new File(['内容'], '脚本.exe'))

    expect(screen.getByRole('alert')).toHaveTextContent('暂不支持 .exe 格式')
    expect(screen.getByRole('button', { name: '开始上传' })).toBeDisabled()
    expect(uploadDocument).not.toHaveBeenCalled()
  })

  it('上传失败后应保留用户已填写的内容以便重试', async () => {
    vi.mocked(uploadDocument).mockRejectedValueOnce(new Error('服务暂时不可用'))
    const user = userEvent.setup()
    render(<UploadDocumentDialog open onOpenChange={vi.fn()} onUploaded={vi.fn()} />)

    await user.upload(screen.getByLabelText('选择本地文件'), new File(['内容'], '员工手册.pdf'))
    await user.type(screen.getByLabelText('文档描述'), '新员工入职参考')
    await user.click(screen.getByRole('button', { name: '开始上传' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('服务暂时不可用')
    expect(screen.getByText('员工手册.pdf')).toBeInTheDocument()
    expect(screen.getByLabelText('文档标题')).toHaveValue('员工手册')
    expect(screen.getByLabelText('文档描述')).toHaveValue('新员工入职参考')
  })

  it('上传成功后应默认以 Markdown 层级切分策略提交并通知调用方', async () => {
    vi.mocked(uploadDocument).mockResolvedValue({ documentId: 18, processId: 'p-18', status: 'QUEUED' })
    const onUploaded = vi.fn()
    const user = userEvent.setup()
    render(<UploadDocumentDialog open onOpenChange={vi.fn()} onUploaded={onUploaded} />)
    const file = new File(['内容'], '员工手册.pdf')

    await user.upload(screen.getByLabelText('选择本地文件'), file)
    await user.click(screen.getByRole('button', { name: '开始上传' }))

    await waitFor(() =>
      expect(uploadDocument).toHaveBeenCalledWith(
        expect.objectContaining({
          file,
          title: '员工手册',
          description: '',
          splitConfig: markdownSplitConfig,
          parseConfig: { enableOcr: true, enableImageDescription: false },
          indexConfig: { enabled: true, vectorEnabled: true, keywordEnabled: true },
        })
      )
    )
    expect(onUploaded).toHaveBeenCalledWith(18)
  })

  it('切换正则策略时应展示专属参数并按正则配置提交', async () => {
    vi.mocked(uploadDocument).mockResolvedValue({ documentId: 19, processId: 'p-19', status: 'QUEUED' })
    const user = userEvent.setup()
    render(<UploadDocumentDialog open onOpenChange={vi.fn()} onUploaded={vi.fn()} />)

    await user.upload(screen.getByLabelText('选择本地文件'), new File(['内容'], '员工手册.txt'))
    await user.click(screen.getByRole('button', { name: /正则文本/ }))
    expect(screen.getByLabelText('分隔符')).toBeInTheDocument()
    expect(screen.getByLabelText('正则表达式')).toBeInTheDocument()
    await user.type(screen.getByLabelText('分隔符'), '；')
    await user.click(screen.getByRole('button', { name: '开始上传' }))

    await waitFor(() =>
      expect(uploadDocument).toHaveBeenCalledWith(
        expect.objectContaining({
          splitConfig: expect.objectContaining({
            splitStrategy: 'REGEX_TEXT',
            regex: expect.objectContaining({ separator: '；', keepSeparator: false }),
          }),
        })
      )
    )
  })

  it('重叠大小不小于片段大小时应阻止提交', async () => {
    const user = userEvent.setup()
    render(<UploadDocumentDialog open onOpenChange={vi.fn()} onUploaded={vi.fn()} />)

    await user.upload(screen.getByLabelText('选择本地文件'), new File(['内容'], '员工手册.pdf'))
    await user.clear(screen.getByLabelText('片段大小'))
    await user.type(screen.getByLabelText('片段大小'), '100')
    await user.clear(screen.getByLabelText('重叠大小'))
    await user.type(screen.getByLabelText('重叠大小'), '200')
    await user.click(screen.getByRole('button', { name: '开始上传' }))

    expect(screen.getByRole('alert')).toHaveTextContent('片段重叠大小必须小于片段大小')
    expect(uploadDocument).not.toHaveBeenCalled()
  })

  it('选择飞书文档来源并输入 URL 时应成功提交外部文档接口', async () => {
    vi.mocked(submitExternalDocument).mockResolvedValue({ documentId: 20, processId: 'p-20', status: 'QUEUED' })
    const onUploaded = vi.fn()
    const user = userEvent.setup()
    render(<UploadDocumentDialog open onOpenChange={vi.fn()} onUploaded={onUploaded} />)

    await user.click(screen.getByRole('button', { name: '飞书文档' }))
    expect(screen.getByLabelText('飞书文档 URL')).toBeInTheDocument()

    await user.type(screen.getByLabelText('飞书文档 URL'), 'https://test.feishu.cn/docx/doxc12345')
    await user.click(screen.getByRole('button', { name: '开始上传' }))

    await waitFor(() =>
      expect(submitExternalDocument).toHaveBeenCalledWith(
        expect.objectContaining({
          sourceType: 'FEISHU',
          sourceUrl: 'https://test.feishu.cn/docx/doxc12345',
          splitConfig: markdownSplitConfig,
          parseConfig: { enableOcr: false, enableImageDescription: false },
          indexConfig: { enabled: true, vectorEnabled: true, keywordEnabled: true },
        })
      )
    )
    expect(onUploaded).toHaveBeenCalledWith(20)
  })

  it('选择语雀文档来源并输入 URL 时应成功提交外部文档接口', async () => {
    vi.mocked(submitExternalDocument).mockResolvedValue({ documentId: 22, processId: 'p-22', status: 'QUEUED' })
    const onUploaded = vi.fn()
    const user = userEvent.setup()
    render(<UploadDocumentDialog open onOpenChange={vi.fn()} onUploaded={onUploaded} />)

    await user.click(screen.getByRole('button', { name: '语雀文档' }))
    expect(screen.getByLabelText('语雀文档 URL')).toBeInTheDocument()

    await user.type(screen.getByLabelText('语雀文档 URL'), 'https://www.yuque.com/org/repo/doc-slug')
    await user.click(screen.getByRole('button', { name: '开始上传' }))

    await waitFor(() =>
      expect(submitExternalDocument).toHaveBeenCalledWith(
        expect.objectContaining({
          sourceType: 'YUQUE',
          sourceUrl: 'https://www.yuque.com/org/repo/doc-slug',
          splitConfig: markdownSplitConfig,
          parseConfig: { enableOcr: false, enableImageDescription: false },
          indexConfig: { enabled: true, vectorEnabled: true, keywordEnabled: true },
        })
      )
    )
    expect(onUploaded).toHaveBeenCalledWith(22)
  })
})
