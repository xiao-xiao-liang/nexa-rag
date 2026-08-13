import { useState } from 'react'
import { RefreshCw, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Pagination } from '@/components/ui/pagination'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
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
  onView: (documentId: number | string) => void
  onDelete: (document: DocumentSummary) => void
  onRetryItem?: () => void
  onPrevious: () => void
  onNext: () => void
  deleteTarget: DocumentSummary | null
  onDeleteTargetChange: (document: DocumentSummary | null) => void
}

/** 飞书风格完整文档列表。 */
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
  const [retryingId, setRetryingId] = useState<number | string | null>(null)
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

  const handleRowRetry = async (documentId: number | string) => {
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
      <section className="overflow-hidden rounded-lg border border-border bg-card">
        <div className="min-w-[720px]">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>文档</TableHead>
                <TableHead>类型</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>更新时间</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading && (
                <TableRow>
                  <TableCell colSpan={5} className="py-14 text-center text-sm text-tertiary">
                    正在加载文档…
                  </TableCell>
                </TableRow>
              )}

              {!loading && filteredRecords.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} className="py-14 text-center text-sm text-tertiary">
                    {keyword || statusFilter !== 'ALL' ? '未找到匹配的文档' : '暂无文档，上传文件后即可开始构建知识库。'}
                  </TableCell>
                </TableRow>
              )}

              {!loading &&
                filteredRecords.map((document) => (
                  <TableRow key={document.documentId}>
                    <TableCell>
                      <div className="flex min-w-0 items-center gap-3">
                        <FileTypeIcon fileName={document.originalFileName} fileType={document.fileType} />
                        <button
                          type="button"
                          onClick={() => onView(document.documentId)}
                          className="min-w-0 text-left group"
                        >
                          <b className="block truncate text-sm font-semibold text-foreground transition-colors group-hover:text-primary">
                            {document.title || document.originalFileName || '未命名文档'}
                          </b>
                          <small className="block truncate text-xs text-tertiary">
                            {document.originalFileName || '未提供原始文件名'}
                          </small>
                        </button>
                      </div>
                    </TableCell>

                    <TableCell className="text-secondary">{document.fileType || '—'}</TableCell>

                    <TableCell>
                      <DocumentStatusBadge status={document.status} />
                    </TableCell>

                    <TableCell className="text-tertiary">{formatUpdateTime(document.updatedTime)}</TableCell>

                    <TableCell className="text-right">
                      <div className="flex items-center justify-end gap-2 text-xs font-medium">
                        {document.status === 'FAILED' && (
                          <button
                            type="button"
                            disabled={retryingId === document.documentId}
                            onClick={() => void handleRowRetry(document.documentId)}
                            className="inline-flex items-center gap-1 rounded bg-danger-light px-2.5 py-1 text-danger transition-colors hover:bg-danger-light/70 disabled:opacity-50"
                          >
                            <RefreshCw className={`size-3.5 ${retryingId === document.documentId ? 'animate-spin' : ''}`} />
                            重试
                          </button>
                        )}

                        <button
                          type="button"
                          onClick={() => onView(document.documentId)}
                          className="rounded px-2.5 py-1 text-primary transition-colors hover:bg-primary-light"
                        >
                          查看
                        </button>
                        <button
                          type="button"
                          aria-label={`删除 ${document.originalFileName || document.title || '文档'}`}
                          onClick={() => onDeleteTargetChange(document)}
                          className="inline-flex items-center gap-1 rounded px-2.5 py-1 text-danger transition-colors hover:bg-danger-light"
                        >
                          <Trash2 className="size-3.5" />
                          删除
                        </button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
            </TableBody>
          </Table>

          {!loading && filteredRecords.length > 0 && (
            <div className="border-t border-border px-4 py-3">
              <Pagination
                total={page.total}
                current={pageNum}
                totalPages={totalPages}
                totalLabel="个文档"
                onPageChange={(nextPage) => (nextPage > pageNum ? onNext() : onPrevious())}
              />
            </div>
          )}
        </div>
      </section>

      {/* 删除确认弹窗 Dialog */}
      <Dialog open={deleteTarget !== null} onOpenChange={(open) => !open && onDeleteTargetChange(null)}>
        <DialogContent className="sm:max-w-md bg-card">
          <DialogHeader>
            <DialogTitle className="text-base font-semibold text-foreground">确认删除文档？</DialogTitle>
            <DialogDescription className="text-xs text-secondary">
              删除后将无法恢复该文档以及关联生成的向量索引和切分文本数据。
            </DialogDescription>
          </DialogHeader>
          <div className="flex justify-end gap-2.5 pt-2">
            <Button
              variant="outline"
              onClick={() => onDeleteTargetChange(null)}
              disabled={deleting}
              className="rounded text-xs"
            >
              取消
            </Button>
            <Button
              variant="danger"
              onClick={() => deleteTarget && onDelete(deleteTarget)}
              disabled={deleting}
              className="rounded text-xs"
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

function formatUpdateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
