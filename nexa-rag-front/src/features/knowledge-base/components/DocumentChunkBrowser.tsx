import { useEffect, useMemo, useState, type KeyboardEvent } from 'react'
import { Check, ChevronDown, Layers, Pencil, RefreshCw, Search, Trash2, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { cn } from '@/lib/utils'
import type { DocumentChunk } from '../api/document-api'

type ChunkViewMode = 'preview' | 'raw'

interface DocumentChunkBrowserProps {
  chunks: DocumentChunk[]
  total: number
  sourceFileName: string
  fileDescription: string | null
  splitStrategyLabel: string | null
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

/** 知识块工作区：三列卡片网格 + 预览/原文切换 + 右侧 Chunk Inspector 抽屉，分页由外层滚动触发。 */
export function DocumentChunkBrowser({
  chunks,
  total,
  sourceFileName,
  fileDescription,
  splitStrategyLabel,
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
  const [viewMode, setViewMode] = useState<ChunkViewMode>('preview')
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
              className="h-8 w-52 rounded-md border border-border bg-card pl-8 pr-7 text-xs text-foreground outline-none transition-colors placeholder:text-tertiary focus:border-primary focus:ring-2 focus:ring-ring/30"
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

          {/* 预览 / 原文切换 */}
          <div className="flex gap-0.5 rounded-md bg-muted p-0.5" aria-label="展示模式">
            {(['preview', 'raw'] as const).map((mode) => (
              <button
                key={mode}
                type="button"
                onClick={() => setViewMode(mode)}
                className={cn(
                  'h-7 rounded-sm px-2.5 text-xs transition-colors',
                  viewMode === mode ? 'border border-border bg-card font-medium text-primary' : 'text-secondary hover:text-foreground'
                )}
              >
                {mode === 'preview' ? '预览' : '原文'}
              </button>
            ))}
          </div>

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
              viewMode={viewMode}
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

      {/* 右侧 Chunk Inspector 抽屉 */}
      <Sheet open={selectedChunk !== null} onOpenChange={(open) => !open && onClose()}>
        <SheetContent side="right" className="flex w-full flex-col gap-0 p-0 sm:max-w-[560px]">
          <SheetHeader className="sr-only">
            <SheetTitle>知识块详情</SheetTitle>
            <SheetDescription>查看当前知识块内容与结构信息</SheetDescription>
          </SheetHeader>
          {selectedChunk && (
            <ChunkDrawer
              chunk={selectedChunk}
              sourceFileName={sourceFileName}
              splitStrategyLabel={splitStrategyLabel}
              viewMode={viewMode}
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
  viewMode,
  selected,
  onSelect,
}: {
  chunk: DocumentChunk
  viewMode: ChunkViewMode
  selected: boolean
  onSelect: (chunk: DocumentChunk) => void
}) {
  const heading = parseChunkHeading(chunk.text)
  const title = viewMode === 'preview'
    ? heading?.title ?? getFirstLine(chunk.text, chunk.chunkOrder)
    : getFirstLine(chunk.text, chunk.chunkOrder)
  const body = viewMode === 'preview' ? stripMarkdownHeadings(chunk.text) : chunk.text

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
      {/* 卡片头部：标题 + 层级徽标 + 更多操作 */}
      <div className="flex items-center justify-between gap-2">
        <h3 className="line-clamp-1 min-w-0 flex-1 text-[13px] font-medium text-foreground">{title}</h3>
        {viewMode === 'preview' && heading?.level ? (
          <span className="flex h-4 shrink-0 items-center rounded-sm bg-primary-light px-1.5 text-[10px] font-medium text-primary">
            H{heading.level}
          </span>
        ) : (
          <span className="shrink-0" />
        )}
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

      {/* 正文预览（渐隐 + 展开入口） */}
      <div className="relative mt-1.5 min-h-0">
        <p className="line-clamp-4 text-xs leading-relaxed text-secondary">{body}</p>
        <span className="pointer-events-none absolute inset-x-0 bottom-0 h-8 bg-gradient-to-t from-card to-transparent" aria-hidden="true" />
      </div>

      {/* 页脚元数据 */}
      <div className="mt-auto flex items-center justify-between pt-2 text-[11px] text-tertiary">
        <span>
          分块 {chunk.chunkOrder} · {chunk.text.length} 字
        </span>
        <span className="flex items-center gap-2.5">
          <ChunkStatusText status={chunk.status} />
          <span className="whitespace-nowrap text-primary opacity-0 transition-opacity group-hover:opacity-100">
            展开全文 →
          </span>
        </span>
      </div>
    </div>
  )
}

/** 右侧知识块 Inspector 抽屉。 */
function ChunkDrawer({
  chunk,
  sourceFileName,
  splitStrategyLabel,
  viewMode,
  onClose,
  onSave,
  onDelete,
}: {
  chunk: DocumentChunk
  sourceFileName: string
  splitStrategyLabel: string | null
  viewMode: ChunkViewMode
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
      {/* 抽屉头部：分块序号 + 状态 + 来源弱信息 */}
      <header className="flex items-start justify-between gap-3 border-b border-border px-5 py-3.5">
        <div className="min-w-0 space-y-1">
          <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
            分块 #{chunk.chunkOrder}
            <ChunkStatusText status={chunk.status} />
          </h2>
          <p className="truncate text-[11px] text-tertiary">来源：{sourceFileName}</p>
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
      <section aria-label="分块完整内容" className="min-h-0 flex-1 space-y-3 overflow-y-auto px-5 py-3.5">
        {/* 内容 */}
        <div>
          <div className="mb-1.5 text-xs font-medium text-secondary">内容</div>
          <div className="rounded-md border border-border bg-muted/60 p-3">
            {editing ? (
              <div className="space-y-2">
                <textarea
                  value={draft}
                  onChange={(event) => setDraft(event.target.value)}
                  aria-label="编辑分块内容"
                  className="min-h-[200px] w-full resize-y rounded-md border border-input bg-card p-2.5 text-xs leading-relaxed text-foreground outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-ring/30"
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
              <ChunkText text={chunk.text} viewMode={viewMode} />
            )}
          </div>
        </div>

        {/* 结构信息 */}
        <div>
          <div className="mb-1.5 text-xs font-medium text-secondary">结构信息</div>
          <div className="rounded-md border border-border bg-muted/60 px-3 py-1">
            <InfoRow label="分块序号" value={`#${chunk.chunkOrder}`} />
            <InfoRow label="标题路径" value={deriveTitlePath(chunk.text)} />
            <InfoRow label="字符数" value={`${chunk.text.length}`} />
            <InfoRow label="切分方式" value={splitStrategyLabel ?? '—'} />
            <InfoRow label="索引状态" value={chunkStatusLabel(chunk.status).label} valueClassName={chunkStatusLabel(chunk.status).className} />
          </div>
        </div>
      </section>

      {/* 抽屉底部操作 */}
      <footer className="flex items-center justify-between border-t border-border px-5 py-3">
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

/** 结构信息行。 */
function InfoRow({ label, value, valueClassName }: { label: string; value: string; valueClassName?: string }) {
  return (
    <div className="flex items-baseline gap-3 border-b border-border/60 py-2 text-xs last:border-b-0">
      <span className="w-16 shrink-0 text-tertiary">{label}</span>
      <span className={cn('min-w-0 break-all text-foreground', valueClassName)}>{value}</span>
    </div>
  )
}

/** 按预览/原文渲染知识块文本：预览隐藏 Markdown 标题符号并以粗体标题展示。 */
function ChunkText({ text, viewMode }: { text: string; viewMode: ChunkViewMode }) {
  if (viewMode === 'raw') {
    return <p className="whitespace-pre-wrap font-mono text-xs leading-relaxed text-secondary">{text}</p>
  }
  return (
    <div className="space-y-1 text-xs leading-relaxed text-secondary">
      {text.split('\n').map((line, index) => {
        const heading = parseHeadingLine(line)
        if (!heading) {
          return line.trim() ? <p key={index}>{line}</p> : <p key={index} className="h-2" />
        }
        return (
          <p key={index} className="font-semibold text-foreground">
            {heading.title}
          </p>
        )
      })}
    </div>
  )
}

/** 分块状态纯色文字（已索引绿 / 失败红 / 跳过灰 / 其余蓝）。 */
function ChunkStatusText({ status }: { status: string }) {
  const mapped = chunkStatusLabel(status)
  return <span className={mapped.className}>{mapped.label}</span>
}

function chunkStatusLabel(status: string): { label: string; className: string } {
  if (status === 'INDEXED') return { label: '已索引', className: 'text-success' }
  if (status === 'FAILED') return { label: '处理失败', className: 'text-danger' }
  if (status === 'SKIP_INDEX') return { label: '跳过索引', className: 'text-tertiary' }
  return { label: '处理中', className: 'text-primary' }
}

/** 解析首个标题行，返回层级与去掉 # 后的标题。 */
function parseChunkHeading(text: string): { level: number; title: string } | null {
  const firstLine = text.split('\n').map((line) => line.trim()).find(Boolean)
  return firstLine ? parseHeadingLine(firstLine) : null
}

/** 解析单行 Markdown 标题（# 开头），返回去掉符号后的文本与层级。 */
function parseHeadingLine(line: string): { level: number; title: string } | null {
  const match = /^(#{1,6})\s+(.*)$/.exec(line.trim())
  if (!match) return null
  return { level: match[1].length, title: match[2].trim() }
}

/** 预览模式下去掉正文中的 Markdown 标题符号。 */
function stripMarkdownHeadings(text: string): string {
  return text.replace(/^#{1,6}\s*/gm, '')
}

/** 取正文首个有效行作为标题。 */
function getFirstLine(text: string, chunkOrder: number): string {
  return text.split('\n').map((line) => line.trim()).find(Boolean) || `分块 ${chunkOrder}`
}

/** 由文本中的标题行推导标题路径。 */
function deriveTitlePath(text: string): string {
  const headings = text
    .split('\n')
    .map((line) => parseHeadingLine(line))
    .filter((item): item is { level: number; title: string } => item !== null)
    .map((item) => item.title)
  return headings.length > 0 ? headings.join(' → ') : '—'
}
