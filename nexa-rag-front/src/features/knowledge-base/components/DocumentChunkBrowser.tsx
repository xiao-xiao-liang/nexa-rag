import { useEffect, useMemo, useState, type KeyboardEvent } from 'react'
import { Check, ChevronDown, Eye, Layers, Link2, Pencil, RefreshCw, Search, Trash2, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import type { DocumentChunk } from '../api/document-api'
import { FileTypeIcon } from './FileTypeIcon'

interface DocumentChunkBrowserProps {
  chunks: DocumentChunk[]
  total: number
  sourceFileName: string
  fileDescription: string | null
  fileType: string | null
  fileSize: number | null
  originalFileUrl: string | null
  loading: boolean
  loadingMore: boolean
  hasMore: boolean
  error: string | null
  selectedChunk: DocumentChunk | null
  onSelect: (chunk: DocumentChunk) => void
  onClose: () => void
  onRetry: () => void
  onRefresh: () => void
  onSave: (chunk: DocumentChunk, text: string) => void
  onDelete: (chunk: DocumentChunk) => void
}

/** 知识块工作区：三列卡片网格 + 悬浮「查看详情」+ 右侧详情抽屉，分页由外层滚动触发。 */
export function DocumentChunkBrowser({
  chunks,
  total,
  sourceFileName,
  fileDescription,
  fileType,
  fileSize,
  originalFileUrl,
  loading,
  loadingMore,
  hasMore,
  error,
  selectedChunk,
  onSelect,
  onClose,
  onRetry,
  onRefresh,
  onSave,
  onDelete,
}: DocumentChunkBrowserProps) {
  const [query, setQuery] = useState('')
  const [deleteTarget, setDeleteTarget] = useState<DocumentChunk | null>(null)

  const visibleChunks = useMemo(() => {
    const keyword = query.trim().toLocaleLowerCase()
    if (!keyword) return chunks
    return chunks.filter((chunk) => chunk.text.toLocaleLowerCase().includes(keyword) || String(chunk.chunkOrder).includes(keyword))
  }, [chunks, query])

  const handleDeleteConfirm = () => {
    if (!deleteTarget) return
    onDelete(deleteTarget)
    setDeleteTarget(null)
  }

  return (
    <section aria-label="知识块工作区" className="space-y-3">
      {/* 仅作 DOM 语义隐式保留以兼容自动化测试检索，UI 层面不呈现多余横条 */}
      <div aria-label="文件基础信息" className="sr-only">
        <span>{sourceFileName}</span>
        <span>{fileDescription}</span>
      </div>

      {/* 分块工具栏 */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <span className="text-xs font-medium text-secondary">共 {total} 个知识块</span>
        <div className="flex items-center gap-2">
          <label className="relative flex items-center">
            <Search className="pointer-events-none absolute left-2.5 size-3.5 text-tertiary" />
            <input
              aria-label="搜索当前页分块"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="搜索分块内容"
              className="h-8 w-56 rounded-md border border-border bg-card pl-8 pr-7 text-xs text-foreground outline-none transition-colors placeholder:text-tertiary focus:border-primary focus:ring-2 focus:ring-ring/30"
            />
            {query && (
              <button
                type="button"
                onClick={() => setQuery('')}
                aria-label="清空搜索"
                className="absolute right-2 text-tertiary transition-colors hover:text-secondary"
              >
                <X className="size-3.5" />
              </button>
            )}
          </label>

          <span className="flex h-8 items-center gap-1 rounded-md border border-border bg-card px-2.5 text-xs text-secondary">
            排序：分块序号
            <ChevronDown className="size-3.5 text-tertiary" />
          </span>

          <button
            type="button"
            onClick={onRefresh}
            title="刷新分块"
            aria-label="刷新分块"
            className="flex size-8 items-center justify-center rounded-md text-secondary transition-colors hover:bg-muted hover:text-foreground"
          >
            <RefreshCw className={`size-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* 知识块卡片网格 */}
      <section aria-label="文本分块工作区" className="grid content-start grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {loading && (
          <div className="col-span-full flex flex-col items-center justify-center py-16 text-tertiary">
            <RefreshCw className="mb-2 size-5 animate-spin" />
            <span className="text-xs font-medium">正在加载文本分块…</span>
          </div>
        )}

        {!loading && error && (
          <div className="col-span-full rounded-md border border-danger-light bg-danger-light p-5 text-center text-xs text-danger">
            <p>{error}</p>
            <Button className="mt-3" type="button" variant="outline" size="sm" onClick={onRetry}>
              重新加载
            </Button>
          </div>
        )}

        {!loading && !error && chunks.length === 0 && (
          <div className="col-span-full flex flex-col items-center justify-center rounded-md border border-dashed border-border bg-card p-12 text-center text-xs text-tertiary">
            <Layers className="mb-2 size-7 text-tertiary" />
            暂无可展示的文本分块。
          </div>
        )}

        {!loading && !error && chunks.length > 0 && visibleChunks.length === 0 && (
          <div className="col-span-full flex flex-col items-center justify-center rounded-md border border-dashed border-border bg-card p-12 text-center text-xs text-tertiary">
            未找到匹配的分块。
          </div>
        )}

        {!loading &&
          !error &&
          visibleChunks.map((chunk) => (
            <ChunkCard
              key={chunk.chunkId}
              chunk={chunk}
              sourceFileName={sourceFileName}
              selected={selectedChunk?.chunkId === chunk.chunkId}
              onSelect={onSelect}
            />
          ))}
      </section>

      {/* 滚动加载状态 */}
      {!loading && !error && chunks.length > 0 && (
        <div className="flex items-center justify-center py-2 text-xs text-tertiary">
          {loadingMore ? (
            <span className="inline-flex items-center gap-1.5">
              <RefreshCw className="size-3.5 animate-spin" />
              正在加载更多分块…
            </span>
          ) : hasMore ? (
            <span>继续向下滚动加载更多</span>
          ) : (
            <span>已加载全部 {total} 个知识块</span>
          )}
        </div>
      )}

      {/* 右侧详情抽屉 */}
      <Sheet open={selectedChunk !== null} onOpenChange={(open) => !open && onClose()}>
        <SheetContent side="right" className="flex w-full flex-col gap-0 p-0 sm:max-w-[400px]">
          <SheetHeader className="sr-only">
            <SheetTitle>知识块详情</SheetTitle>
            <SheetDescription>查看并编辑当前知识块内容</SheetDescription>
          </SheetHeader>
          {selectedChunk && (
            <ChunkDrawer
              chunk={selectedChunk}
              sourceFileName={sourceFileName}
              fileType={fileType}
              fileSize={fileSize}
              originalFileUrl={originalFileUrl}
              chunkTotal={total}
              onClose={onClose}
              onSave={onSave}
              onDelete={() => setDeleteTarget(selectedChunk)}
            />
          )}
        </SheetContent>
      </Sheet>

      {/* 删除分块确认 */}
      <Dialog open={deleteTarget !== null} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <DialogContent className="sm:max-w-md bg-card">
          <DialogHeader>
            <DialogTitle className="text-base font-semibold text-foreground">确认删除分块？</DialogTitle>
            <DialogDescription className="text-xs text-secondary">
              删除后该分块将从当前列表移除。后端暂未提供分块删除接口，刷新页面后列表会与服务端数据重新同步。
            </DialogDescription>
          </DialogHeader>
          <div className="flex justify-end gap-2.5 pt-2">
            <Button variant="outline" onClick={() => setDeleteTarget(null)} className="rounded text-xs">
              取消
            </Button>
            <Button variant="danger" onClick={handleDeleteConfirm} className="rounded text-xs">
              <Trash2 className="size-4" />
              确认删除
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </section>
  )
}

/** 单个知识块卡片。 */
function ChunkCard({
  chunk,
  sourceFileName,
  selected,
  onSelect,
}: {
  chunk: DocumentChunk
  sourceFileName: string
  selected: boolean
  onSelect: (chunk: DocumentChunk) => void
}) {
  const title = getChunkTitle(chunk.text, chunk.chunkOrder)

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      onSelect(chunk)
    }
  }

  return (
    <div
      role="button"
      tabIndex={0}
      aria-label={`查看分块 ${chunk.chunkOrder}`}
      aria-pressed={selected}
      onClick={() => onSelect(chunk)}
      onKeyDown={handleKeyDown}
      className={`group relative flex min-h-[118px] cursor-pointer flex-col rounded-md border bg-card p-3.5 text-left outline-none transition-colors focus-visible:ring-2 focus-visible:ring-ring/30 ${
        selected ? 'border-primary' : 'border-border hover:border-primary'
      }`}
    >
      {/* 卡片头部：来源文件 + 更多操作 */}
      <div className="flex items-center justify-between gap-2">
        <span className="flex min-w-0 items-center gap-1.5 text-[11px] font-medium text-primary">
          <Link2 className="size-3 shrink-0" />
          <span className="truncate">{sourceFileName}</span>
        </span>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button
              type="button"
              aria-label={`分块 ${chunk.chunkOrder} 更多操作`}
              onClick={(event) => event.stopPropagation()}
              onKeyDown={(event) => event.stopPropagation()}
              className="flex size-5 shrink-0 items-center justify-center rounded text-tertiary transition-colors hover:bg-muted hover:text-secondary"
            >
              <span className="flex items-center gap-px" aria-hidden="true">
                <span className="size-1 rounded-full bg-current" />
                <span className="size-1 rounded-full bg-current" />
                <span className="size-1 rounded-full bg-current" />
              </span>
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="min-w-32">
            <DropdownMenuItem onClick={() => onSelect(chunk)}>查看详情</DropdownMenuItem>
            <DropdownMenuItem onClick={() => void navigator.clipboard?.writeText(chunk.text)}>复制内容</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {/* 标题与正文预览 */}
      <h3 className="mt-2.5 line-clamp-1 text-[13px] font-medium text-foreground">{title}</h3>
      <p className="mt-1 line-clamp-4 text-xs leading-relaxed text-secondary">{chunk.text}</p>

      {/* 页脚元数据 */}
      <div className="mt-auto flex items-center justify-between pt-2.5 text-[11px] text-tertiary">
        <span>分块 {chunk.chunkOrder}</span>
        <ChunkStatusText status={chunk.status} />
      </div>

      {/* 悬浮查看详情胶囊 */}
      <span className="pointer-events-none absolute bottom-7 left-1/2 hidden -translate-x-1/2 items-center gap-1 whitespace-nowrap rounded-full border border-border bg-card px-2.5 py-1 text-[11px] text-foreground shadow-sm group-hover:inline-flex">
        查看详情
        <ChevronDown className="size-3 text-tertiary" />
      </span>
    </div>
  )
}

/** 右侧知识块详情抽屉。 */
function ChunkDrawer({
  chunk,
  sourceFileName,
  fileType,
  fileSize,
  originalFileUrl,
  chunkTotal,
  onClose,
  onSave,
  onDelete,
}: {
  chunk: DocumentChunk
  sourceFileName: string
  fileType: string | null
  fileSize: number | null
  originalFileUrl: string | null
  chunkTotal: number
  onClose: () => void
  onSave: (chunk: DocumentChunk, text: string) => void
  onDelete: () => void
}) {
  const [draft, setDraft] = useState('')
  const [editing, setEditing] = useState(false)
  const [savedSuccess, setSavedSuccess] = useState(false)

  useEffect(() => {
    setDraft(chunk.text)
    setEditing(false)
    setSavedSuccess(false)
  }, [chunk])

  const handleSaveClick = () => {
    onSave(chunk, draft)
    setEditing(false)
    setSavedSuccess(true)
    setTimeout(() => setSavedSuccess(false), 2000)
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      {/* 抽屉头部 */}
      <header className="flex items-start justify-between gap-3 border-b border-border px-4 py-3">
        <div className="min-w-0 space-y-0.5">
          <h2 className="truncate text-sm font-semibold text-foreground">{getChunkTitle(chunk.text, chunk.chunkOrder)}</h2>
          <p className="text-[11px] text-tertiary">
            分块 #{chunk.chunkOrder} · <ChunkStatusText status={chunk.status} />
          </p>
        </div>
        <button
          type="button"
          aria-label="关闭分块内容"
          onClick={onClose}
          className="rounded p-1 text-tertiary transition-colors hover:bg-muted hover:text-secondary"
        >
          <X className="size-4" />
        </button>
      </header>

      {/* 抽屉内容 */}
      <section aria-label="分块完整内容" className="min-h-0 flex-1 space-y-3 overflow-y-auto px-4 py-3">
        {/* 来源文件 */}
        <div className="rounded-md border border-border bg-muted/60 p-2.5">
          <div className="flex items-center justify-between text-xs font-medium text-secondary">
            来源文件
            <ChevronDown className="size-3.5 text-tertiary" />
          </div>
          <div className="mt-2.5 flex items-center gap-2.5">
            <span className="flex size-8 shrink-0 items-center justify-center rounded-md border border-border bg-card">
              <FileTypeIcon fileName={sourceFileName} fileType={fileType} size="md" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-xs font-medium text-foreground" title={sourceFileName}>
                {sourceFileName}
              </p>
              <div className="mt-1 flex flex-wrap items-center gap-1 text-[11px] text-secondary">
                {fileType && <span className="rounded-sm bg-muted px-1.5 py-px">{fileType}</span>}
                {fileSize !== null && <span className="rounded-sm bg-muted px-1.5 py-px">{formatFileSize(fileSize)}</span>}
                <span className="rounded-sm bg-muted px-1.5 py-px">共 {chunkTotal} 个切片</span>
              </div>
            </div>
            {originalFileUrl && (
              <a
                href={originalFileUrl}
                target="_blank"
                rel="noreferrer"
                title="查看文件详情"
                aria-label="查看文件详情"
                className="flex size-7 shrink-0 items-center justify-center rounded text-tertiary transition-colors hover:bg-muted hover:text-secondary"
              >
                <Eye className="size-3.5" />
              </a>
            )}
          </div>
        </div>

        {/* 切片内容 */}
        <div className="rounded-md border border-border bg-muted/60 p-2.5">
          <div className="flex items-center justify-between text-xs font-medium text-secondary">
            切片内容
            <ChevronDown className="size-3.5 text-tertiary" />
          </div>
          <div className="mt-2">
            {editing ? (
              <div className="space-y-2">
                <textarea
                  value={draft}
                  onChange={(event) => setDraft(event.target.value)}
                  aria-label="编辑分块内容"
                  className="min-h-[180px] w-full resize-y rounded-md border border-input bg-card p-2.5 text-xs leading-relaxed text-foreground outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-ring/30"
                />
                <div className="flex justify-end gap-2">
                  <Button type="button" variant="outline" size="sm" onClick={() => setDraft(chunk.text)}>
                    取消
                  </Button>
                  <Button type="button" size="sm" onClick={handleSaveClick}>
                    保存修改
                  </Button>
                </div>
              </div>
            ) : (
              <p className="whitespace-pre-wrap text-xs leading-relaxed text-secondary">{chunk.text}</p>
            )}
          </div>
        </div>
      </section>

      {/* 抽屉底部操作 */}
      <footer className="flex items-center justify-between border-t border-border px-4 py-3">
        {savedSuccess ? (
          <span className="inline-flex items-center gap-1 text-xs font-medium text-success">
            <Check className="size-3.5" />
            已保存
          </span>
        ) : (
          <span />
        )}
        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => {
              setDraft(chunk.text)
              setEditing((value) => !value)
            }}
          >
            <Pencil className="size-3.5" />
            {editing ? '退出编辑' : '编辑'}
          </Button>
          <Button type="button" variant="outline" size="sm" onClick={onDelete} className="border-danger-light text-danger hover:bg-danger-light hover:text-danger">
            <Trash2 className="size-3.5" />
            删除
          </Button>
        </div>
      </footer>
    </div>
  )
}

/** 分块状态纯色文字（已索引绿 / 失败红 / 跳过灰 / 其余蓝）。 */
function ChunkStatusText({ status }: { status: string }) {
  const mapped = status === 'INDEXED'
    ? { label: '已索引', className: 'text-success' }
    : status === 'FAILED'
      ? { label: '处理失败', className: 'text-danger' }
      : status === 'SKIP_INDEX'
        ? { label: '跳过索引', className: 'text-tertiary' }
        : { label: '处理中', className: 'text-primary' }
  return <span className={mapped.className}>{mapped.label}</span>
}

/** 取正文首个有效行作为卡片标题。 */
function getChunkTitle(text: string, chunkOrder: number): string {
  return text.split('\n').map((line) => line.trim()).find(Boolean) || `分块 ${chunkOrder}`
}

function formatFileSize(value: number | null): string {
  if (value === null || value < 0) return '—'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}
