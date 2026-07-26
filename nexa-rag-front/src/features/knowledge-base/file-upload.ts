/** 知识库单文件上传的客户端大小限制，与后端 Multipart 配置保持一致。 */
export const MAX_DOCUMENT_FILE_SIZE_BYTES = 100 * 1024 * 1024

const SUPPORTED_EXTENSIONS = new Set([
  'pdf', 'doc', 'docx', 'xls', 'xlsx', 'csv', 'ppt', 'pptx', 'md', 'markdown', 'txt',
])

/** 获取文件扩展名，小写且不带点号。 */
export function getFileExtension(fileName: string): string {
  const index = fileName.lastIndexOf('.')
  return index > -1 ? fileName.slice(index + 1).toLowerCase() : ''
}

/** 获取面向用户的文件类型名称。 */
export function getFileTypeLabel(fileName: string): string {
  const extension = getFileExtension(fileName)
  if (extension === 'pdf') return 'PDF'
  if (extension === 'doc' || extension === 'docx') return 'Word'
  if (extension === 'xls' || extension === 'xlsx' || extension === 'csv') return 'Excel/CSV'
  if (extension === 'ppt' || extension === 'pptx') return 'PPT'
  if (extension === 'md' || extension === 'markdown') return 'Markdown'
  if (extension === 'txt') return 'TXT'
  return '未知格式'
}

/** 根据文件名推导默认文档标题。 */
export function deriveDocumentTitle(fileName: string): string {
  const index = fileName.lastIndexOf('.')
  return index > 0 ? fileName.slice(0, index) : fileName
}

/** 将字节数转换为可读的文件大小。 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/** 在发起请求前校验文件格式和大小，缩短用户反馈路径。 */
export function validateUploadFile(file: File): string | null {
  const extension = getFileExtension(file.name)
  // 1. 先校验后端支持的文件类型，避免无效文件进入上传请求。
  if (!extension || !SUPPORTED_EXTENSIONS.has(extension)) {
    return `暂不支持 ${extension ? `.${extension}` : '该'} 格式，请选择 PDF、Word、Excel/CSV、PPT、Markdown 或 TXT 文件。`
  }
  // 2. 再按后端 Multipart 限制校验单文件大小。
  if (file.size > MAX_DOCUMENT_FILE_SIZE_BYTES) {
    return '文件大小超过 100MB 限制，请选择更小的文件。'
  }
  return null
}
