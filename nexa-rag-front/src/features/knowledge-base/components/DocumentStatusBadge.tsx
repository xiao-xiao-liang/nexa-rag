import { statusLabel } from '../document-status'
import type { DocumentSummary } from '../api/document-api'

/** 文档处理状态徽标，统一概览与文档列表的配色。 */
export function DocumentStatusBadge({ status }: { status: DocumentSummary['status'] }) {
  const tone = status === 'INDEXED' ? 'bg-[#e9f8f0] text-[#27825c]' : status === 'FAILED' ? 'bg-[#fff0ee] text-[#bd5a50]' : 'bg-[#fff4df] text-[#a97427]'
  return <span className={`w-max rounded-md px-2 py-1 text-[10px] ${tone}`}>{statusLabel(status)}</span>
}
