/** 文档处理状态。 */
export type DocumentStatus = 'UPLOADED' | 'QUEUED' | 'PARSING' | 'PARSED' | 'CHUNKING' | 'CHUNKED' | 'INDEXING' | 'INDEXED' | 'FAILED'

const statusLabels: Record<DocumentStatus, string> = {
  UPLOADED: '已上传',
  QUEUED: '排队中',
  PARSING: '解析中',
  PARSED: '解析完成',
  CHUNKING: '切分中',
  CHUNKED: '切分完成',
  INDEXING: '索引写入中',
  INDEXED: '已索引',
  FAILED: '处理失败',
}

const processingStatuses = new Set<DocumentStatus>(['QUEUED', 'PARSING', 'CHUNKING', 'INDEXING'])

/** 判断文档是否处于需要前端轮询的处理阶段。 */
export function isProcessingStatus(status: DocumentStatus): boolean {
  return processingStatuses.has(status)
}

/** 判断文档是否处于前端停止轮询的终态。 */
export function isTerminalStatus(status: DocumentStatus): boolean {
  return status === 'INDEXED' || status === 'FAILED'
}

/** 获取文档状态的中文展示文案。 */
export function statusLabel(status: DocumentStatus): string {
  return statusLabels[status]
}
