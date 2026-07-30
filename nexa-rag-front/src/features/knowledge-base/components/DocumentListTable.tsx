import { useState } from 'react'
import { RefreshCw, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import type { PageVO } from '@/shared/api/types'
import { isProcessingStatus } from '../document-status'
import { DocumentStatusBadge } from './DocumentStatusBadge'
import { FileTypeIcon } from './FileTypeIcon'
import { retryDocument, type DocumentSummary } from '../api/document-api'
import type { StatusFilterType } from '../pages/KnowledgeBaseListPage'

interface DocumentListTableProps {
  page: PageVO<DocumentSummary>
  pageNum: number
  loading: boolean
  deleting: boolean
  query: string
  statusFilter?: StatusFilterType
  onView: (documentId: number) => void
  onDelete: (document: DocumentSummary) => void
  onRetryItem?: () => void
  onPrevious: () => void
  onNext: () => void
  deleteTarget: DocumentSummary | null
  onDeleteTargetChange: (document: DocumentSummary | null) => void
}

/** 完整文档列表，字号全面升级提升可读性。 */
export function DocumentListTable({
  page,
  pageNum,
  loading,
  deleting,
  query,
  statusFilter = 'ALL',
  onView,
  onDelete,
  onRetryItem,
  onPrevious,
  onNext,
  deleteTarget,
  onDeleteTargetChange,
}: DocumentListTableProps) {
  const [retryingId, setRetryingId] = useState<number | null>(null)
  const totalPages = Math.max(page.pages, 1)
  const keyword = query.trim().toLocaleLowerCase()

  const filteredRecords = page.records.filter((document) => {
    const matchKeyword = !keyword || `${document.title || ''} ${document.originalFileName || ''} ${document.fileType || ''}`.toLocaleLowerCase().includes(keyword)
    if (!matchKeyword) return false

    if (statusFilter === 'INDEXED') return document.status === 'INDEXED'
    if (statusFilter === 'PROCESSING') return isProcessingStatus(document.status)
    if (statusFilter === 'FAILED') return document.status === 'FAILED'
    return true
  })

  const handleRowRetry = async (documentId: number) => {
    if (retryingId !== null) return
    setRetryingId(documentId)
    try {
      await retryDocument(documentId)
      onRetryItem?.()
    } catch {
      // 捕获后交由父层刷新状态
    } finally {
      setRetryingId(null)
    }
  }

  return (
    <>
      <section className="overflow-x-auto rounded-2xl border border-slate-200/80 bg-white shadow-sm">
        <div className="min-w-[720px]">
          {/* 表头 Header */}
          <div className="grid grid-cols-[2.4fr_0.65fr_0.85fr_1fr] items-center gap-4 bg-slate-50/80 px-5 py-3.5 text-xs font-bold text-slate-500 uppercase tracking-wider">
            <span>文档信息</span>
            <span>类型</span>
            <span>处理状态</span>
            <span className="text-right">操作</span>
          </div>

          {loading && (
            <p className="px-5 py-14 text-center text-sm font-medium text-slate-400">正在加载文档…</p>
          )}

          {!loading && filteredRecords.length === 0 && (
            <p className="px-5 py-14 text-center text-sm font-medium text-slate-400">
              {keyword || statusFilter !== 'ALL' ? '未找到匹配的文档' : '暂无文档，上传文件后即可开始构建知识库。'}
            </p>
          )}

          {!loading &&
            filteredRecords.map((document) => (
              <article
                key={document.documentId}
                className="grid grid-cols-[2.4fr_0.65fr_0.85fr_1fr] items-center gap-4 border-t border-slate-100 px-5 py-4 text-sm text-slate-700 transition-colors hover:bg-slate-50/80"
              >
                <div className="flex min-w-0 items-center gap-3">
                  <FileTypeIcon fileName={document.originalFileName} fileType={document.fileType} />
                  <button
                    type="button"
                    onClick={() => onView(document.documentId)}
                    className="min-w-0 text-left group"
                  >
                    <b className="block truncate text-sm font-bold text-slate-900 transition-colors group-hover:text-indigo-600">
                      {document.title || document.originalFileName || '未命名文档'}
                    </b>
                    <small className="block truncate text-xs text-slate-400">
                      {document.originalFileName || '未提供原始文件名'}
                    </small>
                  </button>
                </div>

                <span className="font-semibold text-slate-700">{document.fileType || '—'}</span>

                <div>
                  <DocumentStatusBadge status={document.status} />
                </div>

                <div className="flex items-center justify-end gap-2 text-xs font-semibold">
                  {document.status === 'FAILED' && (
                    <button
                      type="button"
                      disabled={retryingId === document.documentId}
                      onClick={() => void handleRowRetry(document.documentId)}
                      className="inline-flex items-center gap-1 rounded-lg bg-rose-50 px-2.5 py-1 text-xs font-semibold text-rose-600 hover:bg-rose-100 transition-colors disabled:opacity-50"
                    >
                      <RefreshCw className={`size-3.5 ${retryingId === document.documentId ? 'animate-spin' : ''}`} />
                      重试
                    </button>
                  )}

                  <button
                    type="button"
                    onClick={() => onView(document.documentId)}
                    className="rounded-lg px-2.5 py-1 text-xs font-semibold text-indigo-600 transition-colors hover:bg-indigo-50 hover:text-indigo-700"
                  >
                    查看
                  </button>
                  <button
                    type="button"
                    aria-label={`删除 ${document.originalFileName || document.title || '文档'}`}
                    onClick={() => onDeleteTargetChange(document)}
                    className="inline-flex items-center gap-1 rounded-lg px-2.5 py-1 text-xs font-semibold text-rose-600 transition-colors hover:bg-rose-50 hover:text-rose-700"
                  >
                    <Trash2 className="size-3.5" />
                    删除
                  </button>
                </div>
              </article>
            ))}

          {/* Pagination Footer */}
          <footer className="flex items-center justify-between border-t border-slate-100 px-5 py-4 text-xs text-slate-500">
            <span className="text-slate-400 font-medium">
              显示第 {(pageNum - 1) * page.size + (page.records.length ? 1 : 0)}–{(pageNum - 1) * page.size + page.records.length} 项，共 {page.total} 项
            </span>

            <div className="flex items-center gap-1.5">
              <button
                type="button"
                onClick={onPrevious}
                disabled={pageNum <= 1 || loading}
                className="flex size-8 items-center justify-center rounded-xl border border-slate-200 bg-white font-semibold text-slate-600 shadow-sm transition-all hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
              >
                ‹
              </button>
              {paginationItems(pageNum, totalPages).map((item) => (
                <span
                  key={item}
                  className={`flex size-8 items-center justify-center rounded-xl font-bold text-xs transition-all ${
                    item === pageNum
                      ? 'bg-indigo-600 text-white shadow-sm shadow-indigo-200'
                      : 'border border-slate-200 bg-white text-slate-600 hover:bg-slate-50'
                  }`}
                >
                  {item}
                </span>
              ))}
              <button
                type="button"
                onClick={onNext}
                disabled={pageNum >= totalPages || loading}
                className="flex size-8 items-center justify-center rounded-xl border border-slate-200 bg-white font-semibold text-slate-600 shadow-sm transition-all hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
              >
                ›
              </button>
            </div>
          </footer>
        </div>
      </section>

      {/* 删除确认弹窗 Dialog */}
      <Dialog open={deleteTarget !== null} onOpenChange={(open) => !open && onDeleteTargetChange(null)}>
        <DialogContent className="rounded-2xl sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="text-base font-bold text-slate-900">确认删除文档？</DialogTitle>
            <DialogDescription className="text-xs text-slate-500">
              删除后将无法恢复该文档以及关联生成的向量索引和切分文本数据。
            </DialogDescription>
          </DialogHeader>
          <div className="flex justify-end gap-2.5 pt-2">
            <Button
              variant="outline"
              onClick={() => onDeleteTargetChange(null)}
              disabled={deleting}
              className="rounded-xl border-slate-200 text-xs font-semibold text-slate-600"
            >
              取消
            </Button>
            <Button
              onClick={() => deleteTarget && onDelete(deleteTarget)}
              disabled={deleting}
              className="rounded-xl bg-rose-600 text-xs font-semibold text-white shadow-sm hover:bg-rose-500 disabled:opacity-50"
            >
              {deleting ? (
                '删除中…'
              ) : (
                <>
                  <Trash2 className="size-4" />
                  确认删除
                </>
              )}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}

function paginationItems(pageNum: number, totalPages: number): number[] {
  if (totalPages <= 3) return Array.from({ length: totalPages }, (_, index) => index + 1)
  const start = Math.min(Math.max(pageNum - 1, 1), totalPages - 2)
  return [start, start + 1, start + 2]
}
