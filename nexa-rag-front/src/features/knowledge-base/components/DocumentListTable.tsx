import { useEffect, useMemo, useState } from 'react'
import { MoreHorizontal, Pin, RefreshCw, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Pagination } from '@/components/ui/pagination'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { PageVO } from '@/shared/api/types'
import { EmptyState } from '@/components/ui/empty-state'
import { isProcessingStatus } from '../document-status'
import { DocumentStatusBadge } from './DocumentStatusBadge'
import { FileTypeIcon } from './FileTypeIcon'
import { retryDocument, type DocumentSummary } from '../api/document-api'
import type { StatusFilterType } from '../pages/KnowledgeBaseListPage'
import { cn } from '@/lib/utils'

const PINNED_DOCUMENTS_KEY = 'nexa-rag.pinned-documents'

interface DocumentListTableProps {
  page: PageVO<DocumentSummary>
  pageNum: number
  loading: boolean
  deleting: boolean
  query: string
  statusFilter?: StatusFilterType
  onView: (documentId: number | string) => void
  onDelete: (document: DocumentSummary) => void
  onBatchDelete: (documents: DocumentSummary[]) => void
  onRetryItem?: () => void
  onPrevious: () => void
  onNext: () => void
  deleteTarget: DocumentSummary | null
  onDeleteTargetChange: (document: DocumentSummary | null) => void
}

/** 飞书风格完整文档列表：多选、置顶、创建人、相对时间与 ⋯ 更多操作。 */
export function DocumentListTable({
  page,
  pageNum,
  loading,
  deleting,
  query,
  statusFilter = 'ALL',
  onView,
  onDelete,
  onBatchDelete,
  onRetryItem,
  onPrevious,
  onNext,
  deleteTarget,
  onDeleteTargetChange,
}: DocumentListTableProps) {
  const [retryingId, setRetryingId] = useState<number | string | null>(null)
  const [pinnedIds, setPinnedIds] = useState<string[]>(() => readPinnedIds())
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [batchDeleteOpen, setBatchDeleteOpen] = useState(false)
  const totalPages = Math.max(page.pages, 1)
  const keyword = query.trim().toLocaleLowerCase()

  useEffect(() => {
    localStorage.setItem(PINNED_DOCUMENTS_KEY, JSON.stringify(pinnedIds))
  }, [pinnedIds])

  const filteredRecords = useMemo(() => {
    const records = page.records.filter((document) => {
      const matchKeyword = !keyword || `${document.title || ''} ${document.originalFileName || ''} ${document.fileType || ''}`.toLocaleLowerCase().includes(keyword)
      if (!matchKeyword) return false
      if (statusFilter === 'INDEXED') return document.status === 'INDEXED'
      if (statusFilter === 'PROCESSING') return isProcessingStatus(document.status)
      if (statusFilter === 'FAILED') return document.status === 'FAILED'
      return true
    })
    return [...records].sort((left, right) => Number(isPinned(right, pinnedIds)) - Number(isPinned(left, pinnedIds)))
  }, [keyword, page.records, pinnedIds, statusFilter])

  const pageSelectedCount = page.records.filter((document) => selectedIds.has(String(document.documentId))).length
  const allPageSelected = page.records.length > 0 && pageSelectedCount === page.records.length

  const toggleRow = (documentId: number | string) => {
    setSelectedIds((current) => {
      const next = new Set(current)
      const key = String(documentId)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  const togglePage = () => {
    setSelectedIds((current) => {
      const next = new Set(current)
      page.records.forEach((document) => {
        const key = String(document.documentId)
        if (allPageSelected) next.delete(key)
        else next.add(key)
      })
      return next
    })
  }

  const handleBatchDelete = () => {
    const targets = page.records.filter((document) => selectedIds.has(String(document.documentId)))
    onBatchDelete(targets)
    setBatchDeleteOpen(false)
    setSelectedIds(new Set())
  }

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

  const batchTargets = page.records.filter((document) => selectedIds.has(String(document.documentId)))

  return (
    <>
      {/* 批量操作栏 */}
      {selectedIds.size > 0 && (
        <div className="mb-2 flex flex-wrap items-center justify-between gap-2 rounded-md border border-primary-light bg-primary-light/40 px-3 py-2">
          <span className="text-xs font-medium text-primary">已选择 {selectedIds.size} 个文档</span>
          <div className="flex items-center gap-2">
            <Button type="button" variant="outline" size="sm" onClick={() => setSelectedIds(new Set())} className="rounded text-xs">
              取消选择
            </Button>
            <Button type="button" variant="danger" size="sm" onClick={() => setBatchDeleteOpen(true)} className="rounded text-xs">
              <Trash2 className="size-3.5" />
              删除选中
            </Button>
          </div>
        </div>
      )}

      <section className="overflow-hidden rounded-lg border border-border bg-card">
        <div className="min-w-[860px]">
          <Table className="text-[13px]">
            <TableHeader>
              <TableRow>
                <TableHead className="w-10">
                  <input
                    type="checkbox"
                    aria-label="全选当前页"
                    checked={allPageSelected}
                    ref={(element) => { if (element) element.indeterminate = pageSelectedCount > 0 && !allPageSelected }}
                    onChange={togglePage}
                    className="size-3.5 cursor-pointer accent-[#3370ff]"
                  />
                </TableHead>
                <TableHead>文档</TableHead>
                <TableHead>类型</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>创建人</TableHead>
                <TableHead>更新时间</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading && (
                <TableRow>
                  <TableCell colSpan={7} className="py-14 text-center text-sm text-tertiary">
                    正在加载文档…
                  </TableCell>
                </TableRow>
              )}

              {!loading && filteredRecords.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7}>
                    <EmptyState
                      title={keyword || statusFilter !== 'ALL' ? '未找到匹配的文档' : '暂无文档'}
                      description={keyword || statusFilter !== 'ALL' ? undefined : '上传文件后即可开始构建知识库。'}
                    />
                  </TableCell>
                </TableRow>
              )}

              {!loading &&
                filteredRecords.map((document) => {
                  const pinned = isPinned(document, pinnedIds)
                  const selected = selectedIds.has(String(document.documentId))
                  return (
                    <TableRow key={document.documentId} className="group">
                      <TableCell>
                        <input
                          type="checkbox"
                          aria-label={`选择 ${document.originalFileName || document.title || '文档'}`}
                          checked={selected}
                          onChange={() => toggleRow(document.documentId)}
                          className="size-3.5 cursor-pointer accent-[#3370ff]"
                        />
                      </TableCell>
                      <TableCell>
                        <div className="flex min-w-0 items-center gap-2.5">
                          <FileTypeIcon fileName={document.originalFileName} fileType={document.fileType} />
                          <button type="button" onClick={() => onView(document.documentId)} className="min-w-0 text-left">
                            <span className="block truncate text-foreground transition-colors hover:text-primary">
                              {document.title || document.originalFileName || '未命名文档'}
                            </span>
                          </button>
                          <button
                            type="button"
                            aria-label={pinned ? `取消置顶 ${document.originalFileName || document.title || '文档'}` : `置顶 ${document.originalFileName || document.title || '文档'}`}
                            onClick={(event) => {
                              event.stopPropagation()
                              togglePin(document.documentId, setPinnedIds)
                            }}
                            className={cn(
                              'flex size-6 shrink-0 items-center justify-center rounded text-tertiary transition-all hover:bg-muted hover:text-primary',
                              pinned ? 'opacity-100 text-primary' : 'opacity-0 group-hover:opacity-100',
                            )}
                          >
                            <Pin className={cn('size-3.5', pinned && 'fill-current')} />
                          </button>
                        </div>
                      </TableCell>

                      <TableCell className="text-secondary">{document.fileType || '—'}</TableCell>
                      <TableCell><DocumentStatusBadge status={document.status} /></TableCell>
                      <TableCell>
                        {document.createBy ? (
                          <span className="flex items-center gap-2">
                            <span className="flex size-5 items-center justify-center rounded-full bg-tertiary/20 text-[10px] font-semibold text-secondary">
                              {document.createBy.slice(0, 1)}
                            </span>
                            <span className="truncate text-secondary">{document.createBy}</span>
                          </span>
                        ) : (
                          <span className="text-tertiary">—</span>
                        )}
                      </TableCell>
                      <TableCell className="text-tertiary">{formatRelativeTime(document.updatedTime)}</TableCell>

                      <TableCell className="text-right">
                        <div className="flex items-center justify-end gap-1">
                          {document.status === 'FAILED' && (
                            <button
                              type="button"
                              disabled={retryingId === document.documentId}
                              onClick={() => void handleRowRetry(document.documentId)}
                              className="inline-flex items-center gap-1 rounded bg-danger-light px-2 py-1 text-xs text-danger transition-colors hover:bg-danger-light/70 disabled:opacity-50"
                            >
                              <RefreshCw className={`size-3.5 ${retryingId === document.documentId ? 'animate-spin' : ''}`} />
                              重试
                            </button>
                          )}
                          <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                              <button
                                type="button"
                                aria-label="更多操作"
                                className="flex size-7 items-center justify-center rounded text-tertiary transition-colors hover:bg-muted hover:text-primary"
                              >
                                <MoreHorizontal className="size-4" />
                              </button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end" className="min-w-32">
                              <DropdownMenuItem onClick={() => onView(document.documentId)} className="cursor-pointer text-xs">
                                查看
                              </DropdownMenuItem>
                              <DropdownMenuSeparator />
                              <DropdownMenuItem
                                onClick={() => onDeleteTargetChange(document)}
                                className="cursor-pointer text-xs text-danger focus:bg-danger-light focus:text-danger"
                              >
                                <Trash2 className="size-3.5 text-danger" />
                                删除
                              </DropdownMenuItem>
                            </DropdownMenuContent>
                          </DropdownMenu>
                        </div>
                      </TableCell>
                    </TableRow>
                  )
                })}
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

      {/* 批量删除确认 */}
      <Dialog open={batchDeleteOpen} onOpenChange={(open) => !open && setBatchDeleteOpen(false)}>
        <DialogContent className="sm:max-w-md bg-card">
          <DialogHeader>
            <DialogTitle className="text-base font-semibold text-foreground">确认删除选中文档？</DialogTitle>
            <DialogDescription className="text-xs text-secondary">
              将删除选中的 {batchTargets.length} 个文档及其关联的向量索引和切分文本数据，删除后无法恢复。
            </DialogDescription>
          </DialogHeader>
          <div className="flex justify-end gap-2.5 pt-2">
            <Button variant="outline" onClick={() => setBatchDeleteOpen(false)} disabled={deleting} className="rounded text-xs">
              取消
            </Button>
            <Button variant="danger" onClick={handleBatchDelete} disabled={deleting} className="rounded text-xs">
              <Trash2 className="size-4" />
              {deleting ? '删除中…' : '确认删除'}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      {/* 单个删除确认弹窗 */}
      <Dialog open={deleteTarget !== null} onOpenChange={(open) => !open && onDeleteTargetChange(null)}>
        <DialogContent className="sm:max-w-md bg-card">
          <DialogHeader>
            <DialogTitle className="text-base font-semibold text-foreground">确认删除文档？</DialogTitle>
            <DialogDescription className="text-xs text-secondary">
              删除后将无法恢复该文档以及关联生成的向量索引和切分文本数据。
            </DialogDescription>
          </DialogHeader>
          <div className="flex justify-end gap-2.5 pt-2">
            <Button variant="outline" onClick={() => onDeleteTargetChange(null)} disabled={deleting} className="rounded text-xs">
              取消
            </Button>
            <Button variant="danger" onClick={() => deleteTarget && onDelete(deleteTarget)} disabled={deleting} className="rounded text-xs">
              <Trash2 className="size-4" />
              {deleting ? '删除中…' : '确认删除'}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}

function isPinned(document: DocumentSummary, pinnedIds: string[]): boolean {
  return pinnedIds.includes(String(document.documentId))
}

function togglePin(
  documentId: number | string,
  setPinnedIds: (updater: (current: string[]) => string[]) => void,
) {
  const key = String(documentId)
  setPinnedIds((current) => (current.includes(key) ? current.filter((id) => id !== key) : [...current, key]))
}

function readPinnedIds(): string[] {
  try {
    const value = localStorage.getItem(PINNED_DOCUMENTS_KEY)
    if (!value) return []
    const parsed = JSON.parse(value) as unknown
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : []
  } catch {
    return []
  }
}

/** 飞书风格相对时间：今天 HH:mm / 昨天 / M月D日 / YYYY年M月D日。 */
function formatRelativeTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  if (date.toDateString() === now.toDateString()) return `今天 ${pad(date.getHours())}:${pad(date.getMinutes())}`
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (date.toDateString() === yesterday.toDateString()) return '昨天'
  if (date.getFullYear() === now.getFullYear()) return `${date.getMonth() + 1}月${date.getDate()}日`
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}
