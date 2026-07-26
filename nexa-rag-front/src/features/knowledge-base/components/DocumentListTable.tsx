import { FileText, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import type { PageVO } from '@/shared/api/types'
import { statusLabel } from '../document-status'
import type { DocumentSummary } from '../api/document-api'

interface DocumentListTableProps {
  page: PageVO<DocumentSummary>
  pageNum: number
  loading: boolean
  deleting: boolean
  onView: (documentId: number) => void
  onDelete: (document: DocumentSummary) => void
  onPrevious: () => void
  onNext: () => void
  deleteTarget: DocumentSummary | null
  onDeleteTargetChange: (document: DocumentSummary | null) => void
}

/** 知识库文档服务端分页表格与删除确认操作。 */
export function DocumentListTable({
  page, pageNum, loading, deleting, onView, onDelete, onPrevious, onNext, deleteTarget, onDeleteTargetChange,
}: DocumentListTableProps) {
  const totalPages = Math.max(page.pages, 1)
  const canPrevious = pageNum > 1
  const canNext = pageNum < totalPages

  return (
    <>
      <div className="overflow-hidden rounded-2xl border bg-card shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[680px] text-left text-sm">
            <thead className="bg-muted/70 text-muted-foreground">
              <tr><th className="px-5 py-3 font-medium">文档</th><th className="px-5 py-3 font-medium">类型</th><th className="px-5 py-3 font-medium">处理状态</th><th className="px-5 py-3 text-right font-medium">操作</th></tr>
            </thead>
            <tbody className="divide-y">
              {page.records.map((document) => (
                <tr key={document.documentId} className="hover:bg-muted/40">
                  <td className="px-5 py-4">
                    <button type="button" className="flex items-center gap-3 text-left" onClick={() => onView(document.documentId)}>
                      <span className="flex size-9 items-center justify-center rounded-lg bg-blue-50 text-blue-600"><FileText className="size-4" /></span>
                      <span><span className="block font-medium text-foreground">{document.title || document.originalFileName || '未命名文档'}</span><span className="block text-xs text-muted-foreground">{document.originalFileName || '未提供原始文件名'}</span></span>
                    </button>
                  </td>
                  <td className="px-5 py-4 text-muted-foreground">{document.fileType || '—'}</td>
                  <td className="px-5 py-4"><span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-700">{statusLabel(document.status)}</span></td>
                  <td className="px-5 py-4"><div className="flex justify-end gap-1"><Button variant="ghost" size="sm" onClick={() => onView(document.documentId)}>查看详情</Button><Button variant="ghost" size="icon" aria-label={`删除 ${document.originalFileName || document.title || '文档'}`} onClick={() => onDeleteTargetChange(document)}><Trash2 className="size-4 text-red-600" /></Button></div></td>
                </tr>
              ))}
              {!loading && page.records.length === 0 && <tr><td colSpan={4} className="px-5 py-14 text-center text-muted-foreground">暂无文档，上传文件后即可开始构建知识库。</td></tr>}
            </tbody>
          </table>
        </div>
        <div className="flex items-center justify-between border-t px-5 py-3 text-sm text-muted-foreground">
          <span>共 {page.total} 个文档</span>
          <div className="flex items-center gap-3"><Button variant="outline" size="sm" disabled={!canPrevious || loading} onClick={onPrevious}>上一页</Button><span>第 {pageNum} / {totalPages} 页</span><Button variant="outline" size="sm" disabled={!canNext || loading} onClick={onNext}>下一页</Button></div>
        </div>
      </div>
      <Dialog open={deleteTarget !== null} onOpenChange={(open) => !open && onDeleteTargetChange(null)}>
        <DialogContent>
          <DialogHeader><DialogTitle>确认删除文档？</DialogTitle><DialogDescription>删除后将无法恢复该文档及其已生成的处理数据。</DialogDescription></DialogHeader>
          <div className="flex justify-end gap-2"><Button variant="outline" onClick={() => onDeleteTargetChange(null)} disabled={deleting}>取消</Button><Button onClick={() => deleteTarget && onDelete(deleteTarget)} disabled={deleting}>{deleting ? '删除中…' : '确认删除'}</Button></div>
        </DialogContent>
      </Dialog>
    </>
  )
}
