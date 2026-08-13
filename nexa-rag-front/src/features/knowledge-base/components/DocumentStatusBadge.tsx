import { Tag } from '@/components/ui/tag'
import { statusLabel } from '../document-status'
import type { DocumentSummary } from '../api/document-api'

/** 文档处理状态徽标，统一概览与文档列表的配色。 */
export function DocumentStatusBadge({ status }: { status: DocumentSummary['status'] }) {
  const variant = status === 'INDEXED' ? 'success' : status === 'FAILED' ? 'danger' : 'warning'
  return <Tag variant={variant}>{statusLabel(status)}</Tag>
}
