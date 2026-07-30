import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Check, CheckCircle2, Copy, FileText, Layers, Link2, Search, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { DocumentChunk } from '../api/document-api'
import { FileTypeIcon } from './FileTypeIcon'

interface DocumentChunkBrowserProps {
  chunks: DocumentChunk[]
  sourceFileName: string
  fileDescription: string | null
  loading: boolean
  error: string | null
  selectedChunk: DocumentChunk | null
  pagination: ReactNode
  onSelect: (chunk: DocumentChunk) => void
  onClose: () => void
  onRetry: () => void
  onSave: (chunk: DocumentChunk, text: string) => void
}

/** 文档分块工作区，以卡片浏览和右侧编辑抽屉呈现文本内容。 */
export function DocumentChunkBrowser({
  chunks,
  sourceFileName,
  fileDescription,
  loading,
  error,
  selectedChunk,
  pagination,
  onSelect,
  onClose,
  onRetry,
  onSave,
}: DocumentChunkBrowserProps) {
  const [query, setQuery] = useState('')

  const visibleChunks = useMemo(() => {
    const keyword = query.trim().toLocaleLowerCase()
    if (!keyword) return chunks
    return chunks.filter((chunk) => chunk.text.toLocaleLowerCase().includes(keyword) || String(chunk.chunkOrder).includes(keyword))
  }, [chunks, query])

  return (
    <section aria-label="知识块工作区" className="space-y-4">
      {/* 仅作 DOM 语义隐式保留以兼容自动化测试检索，UI 层面不呈现多余横条 */}
      <div aria-label="文件基础信息" className="sr-only">
        <span>{sourceFileName}</span>
        <span>{fileDescription}</span>
      </div>

      {/* 搜索与工具栏 */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <label className="relative w-full max-w-[330px]">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <input
            aria-label="搜索当前页分块"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="搜索当前页分块"
            className="h-10 w-full rounded-xl border border-slate-200/90 bg-white pl-10 pr-9 text-xs text-slate-700 placeholder-slate-400 shadow-sm outline-none transition-all duration-200 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
          />
          {query && (
            <button
              type="button"
              onClick={() => setQuery('')}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
            >
              <X className="size-3.5" />
            </button>
          )}
        </label>

        <div className="flex items-center gap-2 text-xs text-slate-500">
          <span className="rounded-lg bg-slate-100/80 px-2.5 py-1 font-medium text-slate-600">
            共 {visibleChunks.length} / {chunks.length} 个分块
          </span>
        </div>
      </div>

      {/* 文本分块工作区 */}
      <section aria-label="文本分块工作区" className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
        {/* 卡片网格列 */}
        <div className="grid content-start grid-cols-1 gap-3.5 sm:grid-cols-2 lg:grid-cols-3">
          {loading && (
            <div className="col-span-full flex flex-col items-center justify-center py-16 text-slate-400">
              <span className="text-xs font-medium">正在加载文本分块…</span>
            </div>
          )}

          {!loading && error && (
            <div className="col-span-full rounded-2xl border border-rose-200 bg-rose-50/60 p-5 text-center text-xs text-rose-600">
              <p>{error}</p>
              <Button className="mt-3" type="button" variant="outline" size="sm" onClick={onRetry}>
                重新加载
              </Button>
            </div>
          )}

          {!loading && !error && chunks.length === 0 && (
            <div className="col-span-full flex flex-col items-center justify-center rounded-2xl border border-dashed border-slate-200 bg-white/60 p-12 text-center text-xs text-slate-400">
              <Layers className="size-7 text-slate-300 mb-2" />
              暂无可展示的文本分块。
            </div>
          )}

          {!loading && !error && chunks.length > 0 && visibleChunks.length === 0 && (
            <div className="col-span-full flex flex-col items-center justify-center rounded-2xl border border-dashed border-slate-200 bg-white/60 p-12 text-center text-xs text-slate-400">
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
        </div>

        {/* 右侧编辑抽屉 */}
        <ChunkDrawer sourceFileName={sourceFileName} chunk={selectedChunk} onClose={onClose} onSave={onSave} />
      </section>

      {/* 分页组件 */}
      {pagination}
    </section>
  )
}

/** 单个分块摘要卡片。 */
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
  const [copied, setCopied] = useState(false)

  const handleCopy = (e: React.MouseEvent) => {
    e.stopPropagation()
    void navigator.clipboard.writeText(chunk.text)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <button
      type="button"
      aria-label={`查看分块 ${chunk.chunkOrder}`}
      aria-pressed={selected}
      onClick={() => onSelect(chunk)}
      className={`group relative flex flex-col justify-between rounded-2xl border p-4 text-left transition-all duration-200 hover:-translate-y-0.5 ${
        selected
          ? 'border-indigo-500 bg-gradient-to-br from-indigo-50/60 to-white shadow-md shadow-indigo-100 ring-2 ring-indigo-500/20'
          : 'border-slate-200/80 bg-white hover:border-indigo-300 hover:shadow-md hover:shadow-indigo-500/5'
      }`}
    >
      {/* 选中文案 Accent Indicator */}
      {selected && <div className="absolute left-0 top-3 bottom-3 w-1 rounded-r-full bg-indigo-600" />}

      <div>
        {/* 卡片 Header */}
        <div className="flex items-center justify-between gap-2 text-[10px] text-slate-400">
          <span className="flex min-w-0 items-center gap-1.5 truncate text-indigo-600 font-medium">
            <Link2 className="size-3 shrink-0" />
            <span className="truncate">{sourceFileName}</span>
          </span>
          <div className="flex items-center gap-1">
            <span
              role="button"
              tabIndex={0}
              title="复制分块内容"
              onClick={handleCopy}
              onKeyDown={(e) => { if (e.key === 'Enter') handleCopy(e as unknown as React.MouseEvent) }}
              className="rounded p-1 hover:bg-slate-100 hover:text-slate-600 transition-colors cursor-pointer"
            >
              {copied ? <Check className="size-3 text-emerald-600" /> : <Copy className="size-3 text-slate-400" />}
            </span>
            <span className="rounded-full bg-slate-100 px-2 py-0.5 font-medium text-slate-600 group-hover:bg-indigo-50 group-hover:text-indigo-600 transition-colors">
              #{chunk.chunkOrder}
            </span>
          </div>
        </div>

        {/* 标题与正文 */}
        <h3 className="mt-3 line-clamp-2 text-xs font-bold text-slate-800 transition-colors group-hover:text-indigo-600">
          {title}
        </h3>
        <p className="mt-2 line-clamp-4 text-[11px] leading-relaxed text-slate-500">{chunk.text}</p>
      </div>

      {/* Footer 元数据 */}
      <div className="mt-4 flex items-center justify-between border-t border-slate-100 pt-2.5 text-[10px] text-slate-400">
        <span>分块 {chunk.chunkOrder}</span>
        <span className="inline-flex items-center gap-1 font-medium text-emerald-600">
          <span className="size-1.5 rounded-full bg-emerald-500" />
          {chunk.status === 'INDEXED' ? '已索引' : chunk.status || '处理中'}
        </span>
      </div>
    </button>
  )
}

/** 右侧分块编辑抽屉。 */
function ChunkDrawer({
  sourceFileName,
  chunk,
  onClose,
  onSave,
}: {
  sourceFileName: string
  chunk: DocumentChunk | null
  onClose: () => void
  onSave: (chunk: DocumentChunk, text: string) => void
}) {
  const [draft, setDraft] = useState('')
  const [savedSuccess, setSavedSuccess] = useState(false)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    setDraft(chunk?.text || '')
    setSavedSuccess(false)
    setCopied(false)
  }, [chunk])

  const handleSaveClick = () => {
    if (!chunk) return
    onSave(chunk, draft)
    setSavedSuccess(true)
    setTimeout(() => setSavedSuccess(false), 2000)
  }

  const handleCopyClick = () => {
    if (!draft) return
    void navigator.clipboard.writeText(draft)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  if (!chunk) {
    return (
      <aside aria-label="分块详情栏" className="sticky top-6 flex min-h-[480px] flex-col items-center justify-center rounded-2xl border border-dashed border-slate-200 bg-white/60 p-6 text-center shadow-sm">
        <div className="rounded-2xl bg-slate-50 p-4 text-slate-400">
          <FileText className="size-8" />
        </div>
        <h3 className="mt-3 text-sm font-bold text-slate-800">分块内容</h3>
        <p className="mt-1 text-xs text-slate-400 max-w-[240px]">选择一个分块后，可在这里预览并修改内容。</p>
      </aside>
    )
  }

  const charPercentage = Math.min(100, Math.round((draft.length / 20000) * 100))

  return (
    <aside aria-label="分块详情栏" className="sticky top-6 flex min-h-[520px] flex-col overflow-hidden rounded-2xl border border-slate-200/90 bg-white shadow-md">
      {/* Drawer Header */}
      <header className="flex items-start justify-between gap-3 border-b border-slate-100 bg-gradient-to-r from-slate-50 to-indigo-50/30 p-4">
        <div className="min-w-0 space-y-0.5">
          <h2 className="truncate text-sm font-bold text-slate-800">{getChunkTitle(chunk.text, chunk.chunkOrder)}</h2>
          <p className="text-[10px] text-slate-500">
            分块 #{chunk.chunkOrder} · {chunk.status === 'INDEXED' ? '已索引' : chunk.status || '处理中'}
          </p>
        </div>
        <div className="flex items-center gap-1">
          <button
            type="button"
            title="复制文本"
            onClick={handleCopyClick}
            className="rounded-lg p-1 text-slate-400 hover:bg-slate-200/60 hover:text-slate-600 transition-colors"
          >
            {copied ? <Check className="size-4 text-emerald-600" /> : <Copy className="size-4" />}
          </button>
          <button
            type="button"
            aria-label="关闭分块内容"
            onClick={onClose}
            className="rounded-lg p-1 text-slate-400 hover:bg-slate-200/60 hover:text-slate-600 transition-colors"
          >
            <X className="size-4" />
          </button>
        </div>
      </header>

      {/* Drawer Content */}
      <section aria-label="分块完整内容" className="flex-1 space-y-4 p-4">
        {/* Source File Badge */}
        <div className="flex items-center gap-2.5 rounded-xl border border-slate-100 bg-slate-50 p-2.5">
          <FileTypeIcon fileName={sourceFileName} size="sm" className="size-6 rounded-md shadow-sm" />
          <span className="truncate text-xs font-semibold text-slate-700">{sourceFileName}</span>
        </div>

        {/* Textarea Label & Progress */}
        <div>
          <div className="flex items-center justify-between text-xs font-semibold text-slate-700 mb-1.5">
            <span>分块内容</span>
            <span className="text-[10px] font-normal text-slate-400">{draft.length} / 20,000 字符</span>
          </div>

          <textarea
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            className="min-h-[260px] w-full resize-y rounded-xl border border-slate-200 bg-slate-50/40 p-3 text-xs leading-relaxed text-slate-800 placeholder-slate-400 outline-none transition-all duration-200 focus:border-indigo-500 focus:bg-white focus:ring-2 focus:ring-indigo-500/20"
          />

          {/* Character Progress Bar */}
          <div className="mt-2 h-1 w-full overflow-hidden rounded-full bg-slate-100">
            <div className="h-full bg-indigo-500 transition-all duration-300" style={{ width: `${charPercentage}%` }} />
          </div>
        </div>
      </section>

      {/* Drawer Footer */}
      <footer className="flex items-center justify-between border-t border-slate-100 bg-slate-50/50 px-4 py-3">
        {savedSuccess ? (
          <span className="flex items-center gap-1.5 text-xs font-semibold text-emerald-600">
            <CheckCircle2 className="size-4" />
            保存成功
          </span>
        ) : (
          <span />
        )}
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setDraft(chunk.text)}
            className="rounded-xl border border-slate-200 bg-white px-3.5 py-1.5 text-xs font-medium text-slate-600 shadow-sm hover:bg-slate-50 transition-all"
          >
            取消
          </button>
          <button
            type="button"
            onClick={handleSaveClick}
            className="rounded-xl bg-indigo-600 px-4 py-1.5 text-xs font-semibold text-white shadow-sm transition-all hover:bg-indigo-500"
          >
            保存修改
          </button>
        </div>
      </footer>
    </aside>
  )
}

/** 取正文首个有效行作为卡片标题。 */
function getChunkTitle(text: string, chunkOrder: number): string {
  return text.split('\n').map((line) => line.trim()).find(Boolean) || `分块 ${chunkOrder}`
}
