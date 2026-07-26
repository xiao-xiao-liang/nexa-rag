import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { FileDropzone } from './FileDropzone'

/** 文档拖拽上传区测试。 */
describe('FileDropzone', () => {
  afterEach(() => {
    cleanup()
  })

  it('拖入文件后应通知调用方并给出拖入反馈', () => {
    const onFileChange = vi.fn()
    render(<FileDropzone file={null} disabled={false} error={null} onFileChange={onFileChange} onRemove={vi.fn()} />)
    const dropzone = screen.getByRole('button', { name: '选择要上传的知识库文件' })
    const file = new File(['x'], '员工手册.pdf')

    fireEvent.dragEnter(dropzone, { dataTransfer: { files: [] } })
    expect(dropzone).toHaveAttribute('data-dragging', 'true')
    fireEvent.drop(dropzone, { dataTransfer: { files: [file] } })

    expect(onFileChange).toHaveBeenCalledWith(file)
    expect(dropzone).not.toHaveAttribute('data-dragging')
  })

  it('已选文件应展示卡片并支持移除', async () => {
    const onRemove = vi.fn()
    const user = userEvent.setup()
    render(<FileDropzone file={new File(['x'], '员工手册.pdf')} disabled={false} error={null} onFileChange={vi.fn()} onRemove={onRemove} />)

    expect(screen.getByText('员工手册.pdf')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '移除 员工手册.pdf' }))

    expect(onRemove).toHaveBeenCalledOnce()
  })

  it('应支持键盘打开原生文件选择并在禁用时阻止操作', async () => {
    const onFileChange = vi.fn()
    render(<FileDropzone file={null} disabled={true} error={null} onFileChange={onFileChange} onRemove={vi.fn()} />)
    const dropzone = screen.getByRole('button', { name: '选择要上传的知识库文件' })

    fireEvent.keyDown(dropzone, { key: 'Enter' })
    fireEvent.drop(dropzone, { dataTransfer: { files: [new File(['x'], '员工手册.pdf')] } })

    expect(dropzone).toHaveAttribute('aria-disabled', 'true')
    expect(onFileChange).not.toHaveBeenCalled()
  })
})
