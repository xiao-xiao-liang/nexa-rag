import { describe, expect, it } from 'vitest'
import { MAX_DOCUMENT_FILE_SIZE_BYTES, deriveDocumentTitle, formatFileSize, validateUploadFile } from './file-upload'

/** 知识库上传文件规则测试。 */
describe('知识库上传文件规则', () => {
  it('应识别支持格式并拒绝未知格式', () => {
    expect(validateUploadFile(new File(['x'], '员工手册.PDF'))).toBeNull()
    expect(validateUploadFile(new File(['x'], '脚本.exe'))).toBe('暂不支持 .exe 格式，请选择 PDF、Word、Excel/CSV、PPT、Markdown 或 TXT 文件。')
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
