import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { submitExternalDocument, uploadDocument } from '../api/document-api'
import { UploadDocumentDialog } from './UploadDocumentDialog'

vi.mock('../api/document-api', () => ({
  uploadDocument: vi.fn(),
  submitExternalDocument: vi.fn(),
}))

/** 上传文档弹窗测试：用户只负责上传与基础信息，不暴露切分等高级配置。 */
describe('UploadDocumentDialog', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('选择文件后应自动填写标题，描述栏常驻可见且不展示高级配置', async () => {
    const user = userEvent.setup()
    render(<UploadDocumentDialog open onOpenChange={vi.fn()} onUploaded={vi.fn()} />)

    await user.upload(screen.getByLabelText('选择本地文件'), new File(['内容'], '员工手册.pdf', { type: 'application/pdf' }))

    expect(screen.getByLabelText('文档标题')).toHaveValue('员工手册')
    expect(screen.getByLabelText('文档描述')).toBeInTheDocument()
    expect(screen.queryByText('切分策略')).not.toBeInTheDocument()
    expect(screen.queryByText('片段大小（字符）')).not.toBeInTheDocument()
  })

  it('外部传入初始描述时应预填文档描述', async () => {
    render(<UploadDocumentDialog open onOpenChange={vi.fn()} onUploaded={vi.fn()} initialDescription="AI 生成的描述" />)

    expect(screen.getByLabelText('文档描述')).toHaveValue('AI 生成的描述')
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

  it('上传成功应仅提交文件、标题与描述，处理配置交由后端兜底', async () => {
    vi.mocked(uploadDocument).mockResolvedValue({ documentId: 18, processId: 'p-18', status: 'QUEUED' })
    const onUploaded = vi.fn()
    const user = userEvent.setup()
    render(<UploadDocumentDialog open onOpenChange={vi.fn()} onUploaded={onUploaded} />)
    const file = new File(['内容'], '员工手册.pdf')

    await user.upload(screen.getByLabelText('选择本地文件'), file)
    await user.click(screen.getByRole('button', { name: '开始上传' }))

    await waitFor(() =>
      expect(uploadDocument).toHaveBeenCalledWith({
        file,
        title: '员工手册',
        description: '',
      })
    )
    expect(onUploaded).toHaveBeenCalledWith(18)
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
      expect(submitExternalDocument).toHaveBeenCalledWith({
        sourceType: 'FEISHU',
        sourceUrl: 'https://test.feishu.cn/docx/doxc12345',
        title: undefined,
        description: undefined,
      })
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
      expect(submitExternalDocument).toHaveBeenCalledWith({
        sourceType: 'YUQUE',
        sourceUrl: 'https://www.yuque.com/org/repo/doc-slug',
        title: undefined,
        description: undefined,
      })
    )
    expect(onUploaded).toHaveBeenCalledWith(22)
  })
})
