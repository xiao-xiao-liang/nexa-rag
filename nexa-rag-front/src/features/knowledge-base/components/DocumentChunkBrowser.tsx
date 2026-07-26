import { useMemo, useState, type ReactNode } from 'react'
import { FileText, Search, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import type { DocumentChunk } from '../api/document-api'

interface DocumentChunkBrowserProps {
  chunks: DocumentChunk[]
  sourceFileName: string
  loading: boolean
  error: string | null
  selectedChunk: DocumentChunk | null
  pagination: ReactNode
  onSelect: (chunk: DocumentChunk) => void
  onClose: () => void
  onRetry: () => void
}

/** 文档文本分块的工作台浏览组件，提供卡片网格、筛选和固定详情栏。 */
export function DocumentChunkBrowser({ chunks, sourceFileName, loading, error, selectedChunk, pagination, onSelect, onClose, onRetry }: DocumentChunkBrowserProps) {
  const [query, setQuery] = useState('')
  const visibleChunks = useMemo(() => {
    const keyword = query.trim().toLocaleLowerCase()
    if (!keyword) return chunks
    return chunks.filter((chunk) => chunk.text.toLocaleLowerCase().includes(keyword) || String(chunk.chunkOrder).includes(keyword))
  }, [chunks, query])

  return <div className="mt-5 grid min-h-[34rem] xl:grid-cols-[minmax(0,1fr)_22rem]">
    <section className="min-w-0 border-y border-l bg-white p-5 xl:border-y-0">
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <div><h3 className="font-semibold text-slate-900">知识块</h3><p className="mt-1 text-xs text-muted-foreground">当前页 {chunks.length} 个知识块</p></div>
        <label className="relative block w-full sm:w-72"><Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" /><Input aria-label="搜索文本分块" value={query} onChange={(event) => setQuery(event.target.value)} className="h-9 rounded-lg pl-9" placeholder="搜索知识块" /></label>
      </div>
      {/* 1. 主区以卡片网格展示分块摘要，便于快速扫描和连续选择。 */}
      <div className="grid max-h-[36rem] grid-cols-1 gap-4 overflow-y-auto pr-1 sm:grid-cols-2 lg:grid-cols-3">
        {loading && <p className="col-span-full px-1 py-2 text-sm text-muted-foreground">正在加载文本分块…</p>}
        {!loading && error && <div className="col-span-full rounded-lg bg-red-50 p-3 text-sm text-red-700"><p>{error}</p><Button className="mt-2" type="button" variant="outline" size="sm" onClick={onRetry}>重新加载</Button></div>}
        {!loading && !error && chunks.length === 0 && <p className="col-span-full px-1 py-2 text-sm text-muted-foreground">暂无可展示的文本分块。</p>}
        {!loading && !error && chunks.length > 0 && visibleChunks.length === 0 && <p className="col-span-full px-1 py-2 text-sm text-muted-foreground">未找到匹配的知识块。</p>}
        {!loading && !error && visibleChunks.map((chunk) => <ChunkCard key={chunk.chunkId} chunk={chunk} sourceFileName={sourceFileName} selected={selectedChunk?.chunkId === chunk.chunkId} onSelect={onSelect} />)}
      </div>
      {pagination}
    </section>
    {/* 2. 详情栏始终占位，未选择时不渲染任何内容，保持工作台阅读空间稳定。 */}
    <aside aria-label="分块详情栏" className="min-h-72 border-x border-b bg-white xl:min-h-full xl:border-b-0 xl:border-l xl:border-r-0">
      {selectedChunk && <section aria-label="分块完整内容" className="flex h-full min-h-72 flex-col"><header className="flex items-start justify-between gap-3 border-b px-5 py-5"><div className="min-w-0"><p className="text-base font-semibold text-slate-900">{getChunkTitle(selectedChunk.text, selectedChunk.chunkOrder)}</p><p className="mt-1 text-xs text-muted-foreground">分块 {selectedChunk.chunkOrder}</p></div><Button type="button" variant="ghost" size="icon" aria-label="关闭分块内容" onClick={onClose}><X className="size-4" /></Button></header><div className="min-h-0 flex-1 space-y-5 overflow-y-auto p-5"><section className="rounded-xl bg-slate-50 p-3"><p className="text-xs font-medium text-slate-600">来源文件</p><div className="mt-2 flex min-w-0 items-center gap-2 text-sm text-slate-900"><FileText className="size-4 shrink-0 text-blue-600" /><span className="truncate">{sourceFileName}</span></div></section><section><p className="text-sm font-semibold text-slate-900">切片内容</p><div className="mt-3 whitespace-pre-wrap text-sm leading-7 text-slate-700">{selectedChunk.text}</div></section></div></section>}
    </aside>
  </div>
}

/** 分块摘要卡片，仅负责展示来源、标题、摘要和选中态。 */
function ChunkCard({ chunk, sourceFileName, selected, onSelect }: { chunk: DocumentChunk; sourceFileName: string; selected: boolean; onSelect: (chunk: DocumentChunk) => void }) {
  return <button type="button" aria-label={`查看分块 ${chunk.chunkOrder}`} aria-pressed={selected} className={cn('min-h-52 w-full rounded-xl border p-4 text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-300', selected ? 'border-blue-500 bg-blue-50' : 'border-slate-200 bg-white hover:border-blue-200 hover:bg-blue-50/50')} onClick={() => onSelect(chunk)}><span className="flex items-center gap-1.5 truncate text-xs font-medium text-blue-600"><FileText className="size-3.5 shrink-0" />{sourceFileName}</span><span className="mt-4 block line-clamp-2 text-base font-semibold leading-6 text-slate-900">{getChunkTitle(chunk.text, chunk.chunkOrder)}</span><span className="mt-2 block line-clamp-3 text-sm leading-5 text-slate-600">{chunk.text}</span><span className="mt-4 block text-xs text-muted-foreground">分块 {chunk.chunkOrder}</span></button>
}

/** 从分块正文提取用于卡片和详情标题的首个有效文本行。 */
function getChunkTitle(text: string, chunkOrder: number): string {
  const firstLine = text.split('\n').map((line) => line.trim()).find(Boolean)
  return firstLine || `分块 ${chunkOrder}`
}
