import type { ReactNode } from 'react'
import { X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import type { DocumentChunk } from '../api/document-api'

interface DocumentChunkBrowserProps {
  chunks: DocumentChunk[]
  loading: boolean
  error: string | null
  selectedChunk: DocumentChunk | null
  pagination: ReactNode
  onSelect: (chunk: DocumentChunk) => void
  onClose: () => void
  onRetry: () => void
}

/** 文档文本分块的主从浏览组件，左侧选择分块，右侧阅读完整内容。 */
export function DocumentChunkBrowser({ chunks, loading, error, selectedChunk, pagination, onSelect, onClose, onRetry }: DocumentChunkBrowserProps) {
  return <div className="mt-4 grid gap-4 lg:grid-cols-[20rem_minmax(0,1fr)]">
    <div className="min-w-0 rounded-xl border bg-slate-50/60 p-3">
      <div className="max-h-[32rem] space-y-2 overflow-y-auto pr-1">
        {/* 1. 左侧仅展示列表状态和紧凑摘要，避免完整文本撑高页面。 */}
        {loading && <p className="px-1 py-2 text-sm text-muted-foreground">正在加载文本分块…</p>}
        {!loading && error && <div className="rounded-lg bg-red-50 p-3 text-sm text-red-700"><p>{error}</p><Button className="mt-2" type="button" variant="outline" size="sm" onClick={onRetry}>重新加载</Button></div>}
        {!loading && !error && chunks.length === 0 && <p className="px-1 py-2 text-sm text-muted-foreground">暂无可展示的文本分块。</p>}
        {!loading && !error && chunks.map((chunk) => <ChunkCard key={chunk.chunkId} chunk={chunk} selected={selectedChunk?.chunkId === chunk.chunkId} onSelect={onSelect} />)}
      </div>
      {pagination}
    </div>
    {/* 2. 未选择时不渲染右侧面板，保留用户要求的空白阅读区。 */}
    {selectedChunk && <section aria-label="分块完整内容" className="min-w-0 rounded-xl border bg-white shadow-sm"><header className="flex items-center justify-between border-b px-4 py-3"><span className="text-sm font-semibold">分块 {selectedChunk.chunkOrder}</span><Button type="button" variant="ghost" size="icon" aria-label="关闭分块内容" onClick={onClose}><X className="size-4" /></Button></header><div className="max-h-[32rem] overflow-y-auto whitespace-pre-wrap px-4 py-4 text-sm leading-6 text-slate-800">{selectedChunk.text}</div></section>}
  </div>
}

/** 分块摘要卡片，仅负责选择状态和两行文本预览。 */
function ChunkCard({ chunk, selected, onSelect }: { chunk: DocumentChunk; selected: boolean; onSelect: (chunk: DocumentChunk) => void }) {
  return <button type="button" aria-label={`查看分块 ${chunk.chunkOrder}`} aria-pressed={selected} className={cn('w-full rounded-lg border p-3 text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-300', selected ? 'border-blue-500 bg-blue-50' : 'border-slate-200 bg-white hover:border-blue-200 hover:bg-blue-50/50')} onClick={() => onSelect(chunk)}><span className="text-xs font-medium text-muted-foreground">分块 {chunk.chunkOrder}</span><span className="mt-1 block line-clamp-2 text-sm leading-5 text-slate-800">{chunk.text}</span></button>
}
