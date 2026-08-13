import { statusLabel } from '../document-status'
import type { DocumentSummary } from '../api/document-api'

/** 文档处理状态：按 HTML 定稿仅用字体颜色区分（成功绿 / 处理中蓝 / 失败红）。 */
export function DocumentStatusBadge({ status }: { status: DocumentSummary['status'] }) {
  const color = status === 'INDEXED' ? 'text-success' : status === 'FAILED' ? 'text-danger' : 'text-primary'
  return <span className={color}>{statusLabel(status)}</span>
}
