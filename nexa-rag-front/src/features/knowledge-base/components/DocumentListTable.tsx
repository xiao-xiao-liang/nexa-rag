import { MoreHorizontal, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import type { PageVO } from '@/shared/api/types'
import { DocumentStatusBadge } from './DocumentStatusBadge'
import type { DocumentSummary } from '../api/document-api'

interface DocumentListTableProps {
  page: PageVO<DocumentSummary>
  pageNum: number
  loading: boolean
  deleting: boolean
  query: string
  onView: (documentId: number) => void
  onDelete: (document: DocumentSummary) => void
  onPrevious: () => void
  onNext: () => void
  deleteTarget: DocumentSummary | null
  onDeleteTargetChange: (document: DocumentSummary | null) => void
}

/** 完整文档列表，以原型的表格、操作区和分页结构呈现服务端数据。 */
export function DocumentListTable({ page, pageNum, loading, deleting, query, onView, onDelete, onPrevious, onNext, deleteTarget, onDeleteTargetChange }: DocumentListTableProps) {
  const totalPages = Math.max(page.pages, 1)
  const keyword = query.trim().toLocaleLowerCase()
  const records = !keyword ? page.records : page.records.filter((document) => `${document.title || ''} ${document.originalFileName || ''} ${document.fileType || ''}`.toLocaleLowerCase().includes(keyword))

  return <>
    <section className="mt-3 overflow-x-auto rounded-xl border border-[#e1e6ee] bg-white"><div className="min-w-[680px]"><div className="grid grid-cols-[2.4fr_0.65fr_0.85fr_1fr] items-center gap-[14px] bg-[#fafbfc] px-[17px] py-[15px] text-[10px] font-semibold text-[#9ea7b4]"><span>文档</span><span>类型</span><span>处理状态</span><span className="text-right">操作</span></div>{loading && <p className="px-5 py-12 text-center text-sm text-[#8e98a7]">正在加载文档…</p>}{!loading && records.length === 0 && <p className="px-5 py-14 text-center text-sm text-[#8e98a7]">{keyword ? '未找到匹配的文档' : '暂无文档，上传文件后即可开始构建知识库。'}</p>}{!loading && records.map((document) => <article key={document.documentId} className="grid grid-cols-[2.4fr_0.65fr_0.85fr_1fr] items-center gap-[14px] border-t border-[#edf0f4] px-[17px] py-[15px] text-xs text-[#69768c] hover:bg-[#fcfcff]"><div className="flex min-w-0 items-center gap-2.5"><span aria-hidden="true" className="size-[15px] shrink-0 rounded border border-[#d7dde8]" /><span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-[#eef0ff] text-[9px] font-bold text-[#5b61cc]">{document.fileType || '文件'}</span><button type="button" onClick={() => onView(document.documentId)} className="min-w-0 text-left"><b className="block truncate text-xs font-medium text-[#3d4a60]">{document.title || document.originalFileName || '未命名文档'}</b><small className="mt-1 block truncate text-[10px] text-[#9aa3b1]">{document.originalFileName || '未提供原始文件名'}</small></button></div><span>{document.fileType || '—'}</span><DocumentStatusBadge status={document.status} /><div className="flex justify-end gap-2 text-[11px] text-[#5b61c8]"><button type="button" onClick={() => onView(document.documentId)} className="hover:text-[#4549b5]">查看</button><button type="button" aria-label={`删除 ${document.originalFileName || document.title || '文档'}`} onClick={() => onDeleteTargetChange(document)} className="inline-flex items-center hover:text-[#bd5252]"><MoreHorizontal className="size-4" /></button></div></article>)}<footer className="flex items-center justify-between border-t border-[#edf0f4] px-4 py-3 text-[11px] text-[#818c9e]"><span>显示第 {(pageNum - 1) * page.size + (page.records.length ? 1 : 0)}–{(pageNum - 1) * page.size + page.records.length} 项，共 {page.total} 项</span><div className="flex items-center gap-2"><button type="button" onClick={onPrevious} disabled={pageNum <= 1 || loading} className="flex size-7 items-center justify-center rounded-md border border-[#e0e5ee] bg-white disabled:cursor-not-allowed disabled:opacity-40">‹</button>{paginationItems(pageNum, totalPages).map((item) => <span key={item} className={`flex size-7 items-center justify-center rounded-md border text-[11px] ${item === pageNum ? 'border-[#5b5ed2] bg-[#5b5ed2] text-white' : 'border-[#e0e5ee] bg-white text-[#68758b]'}`}>{item}</span>)}<button type="button" onClick={onNext} disabled={pageNum >= totalPages || loading} className="flex size-7 items-center justify-center rounded-md border border-[#e0e5ee] bg-white disabled:cursor-not-allowed disabled:opacity-40">›</button></div></footer></div></section>
    <Dialog open={deleteTarget !== null} onOpenChange={(open) => !open && onDeleteTargetChange(null)}><DialogContent><DialogHeader><DialogTitle>确认删除文档？</DialogTitle><DialogDescription>删除后将无法恢复该文档及其已生成的处理数据。</DialogDescription></DialogHeader><div className="flex justify-end gap-2"><Button variant="outline" onClick={() => onDeleteTargetChange(null)} disabled={deleting}>取消</Button><Button onClick={() => deleteTarget && onDelete(deleteTarget)} disabled={deleting} className="bg-[#b95552] hover:bg-[#9e4543]">{deleting ? '删除中…' : <><Trash2 className="size-4" />确认删除</>}</Button></div></DialogContent></Dialog>
  </>
}

function paginationItems(pageNum: number, totalPages: number): number[] {
  if (totalPages <= 3) return Array.from({ length: totalPages }, (_, index) => index + 1)
  const start = Math.min(Math.max(pageNum - 1, 1), totalPages - 2)
  return [start, start + 1, start + 2]
}
