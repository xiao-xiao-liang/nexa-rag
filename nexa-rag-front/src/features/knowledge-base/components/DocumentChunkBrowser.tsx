import type { ReactNode } from 'react'
import { X } from 'lucide-react'
import { Button } from '@/components/ui/button'
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

/** 文档文本分块的网格详情浏览组件，网格选择分块，右侧阅读完整内容。 */
export function DocumentChunkBrowser({ chunks, sourceFileName, loading, error, selectedChunk, pagination, onSelect, onClose, onRetry }: DocumentChunkBrowserProps) {
  return <div className="mt-4 grid gap-4 xl:grid-cols-[minmax(0,1fr)_22rem]">
    <div className="min-w-0">
      {/* 1. 网格仅展示紧凑摘要，避免完整文本撑高页面。 */}
      <div className="grid max-h-[32rem] grid-cols-1 gap-3 overflow-y-auto pr-1 sm:grid-cols-2 lg:grid-cols-3">
        {loading && <p className="px-1 py-2 text-sm text-muted-foreground">正在加载文本分块…</p>}
        {!loading && error && <div className="rounded-lg bg-red-50 p-3 text-sm text-red-700"><p>{error}</p><Button className="mt-2" type="button" variant="outline" size="sm" onClick={onRetry}>重新加载</Button></div>}
        {!loading && !error && chunks.length === 0 && <p className="px-1 py-2 text-sm text-muted-foreground">暂无可展示的文本分块。</p>}
        {!loading && !error && chunks.map((chunk) => <ChunkCard key={chunk.chunkId} chunk={chunk} sourceFileName={sourceFileName} selected={selectedChunk?.chunkId === chunk.chunkId} onSelect={onSelect} />)}
      </div>
      {pagination}
    </div>
    {/* 2. 未选择时不渲染右侧内容，保留用户要求的空白详情区域。 */}
    {selectedChunk && <section aria-label="分块完整内容" className="min-w-0 rounded-xl border bg-white shadow-sm"><header className="flex items-start justify-between gap-3 border-b px-4 py-3"><div><p className="text-sm font-semibold">分块 {selectedChunk.chunkOrder}</p><p className="mt-1 truncate text-xs text-muted-foreground">{sourceFileName}</p></div><Button type="button" variant="ghost" size="icon" aria-label="关闭分块内容" onClick={onClose}><X className="size-4" /></Button></header><div className="max-h-[32rem] overflow-y-auto whitespace-pre-wrap px-4 py-4 text-sm leading-6 text-slate-800">{selectedChunk.text}</div></section>}
  </div>
}

/** 分块摘要卡片，仅负责选择状态和两行文本预览。 */
function ChunkCard({ chunk, sourceFileName, selected, onSelect }: { chunk: DocumentChunk; sourceFileName: string; selected: boolean; onSelect: (chunk: DocumentChunk) => void }) {
  return <button type="button" aria-label={`查看分块 ${chunk.chunkOrder}`} aria-pressed={selected} className={cn('min-h-40 w-full rounded-xl border p-4 text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-300', selected ? 'border-blue-500 bg-blue-50' : 'border-slate-200 bg-white hover:border-blue-200 hover:bg-blue-50/50')} onClick={() => onSelect(chunk)}><span className="block truncate text-xs font-medium text-blue-600">{sourceFileName}</span><span className="mt-3 block text-sm font-semibold text-slate-900">分块 {chunk.chunkOrder}</span><span className="mt-2 block line-clamp-3 text-sm leading-5 text-slate-600">{chunk.text}</span></button>
}
