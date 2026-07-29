import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { FileText, Link2, Search, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
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
  onSave: (chunk: DocumentChunk, text: string) => void
}

/** 文档分块工作区，以卡片浏览和右侧编辑抽屉呈现文本内容。 */
export function DocumentChunkBrowser({ chunks, sourceFileName, loading, error, selectedChunk, pagination, onSelect, onClose, onRetry, onSave }: DocumentChunkBrowserProps) {
  const [query, setQuery] = useState('')
  const visibleChunks = useMemo(() => {
    const keyword = query.trim().toLocaleLowerCase()
    if (!keyword) return chunks
    return chunks.filter((chunk) => chunk.text.toLocaleLowerCase().includes(keyword) || String(chunk.chunkOrder).includes(keyword))
  }, [chunks, query])

  return <>
    <div className="mt-6 flex flex-wrap items-center gap-2"><label className="relative min-w-[220px] flex-1"><Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-[#9ea7b4]" /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索当前页分块" className="h-10 w-full rounded-lg border border-[#e0e5ee] bg-white pl-9 pr-3 text-[11px] text-[#536075] outline-none transition focus:border-[#7475df] focus:ring-2 focus:ring-[#e8e9ff]" /></label><button type="button" className="h-10 rounded-lg border border-[#e0e5ee] bg-white px-3 text-[11px] text-[#69768b]">分块顺序</button></div>
    <section aria-label="文本分块工作区" className="mt-3 grid gap-[14px] xl:grid-cols-[minmax(0,1fr)_320px]"><div className="grid content-start grid-cols-1 gap-3 sm:grid-cols-2 2xl:grid-cols-3">{loading && <p className="col-span-full py-12 text-center text-sm text-[#8792a3]">正在加载文本分块…</p>}{!loading && error && <div className="col-span-full rounded-xl border border-[#f3cfca] bg-[#fff4f2] p-4 text-sm text-[#b6574d]"><p>{error}</p><Button className="mt-3" type="button" variant="outline" size="sm" onClick={onRetry}>重新加载</Button></div>}{!loading && !error && chunks.length === 0 && <p className="col-span-full rounded-xl border border-dashed border-[#dfe5ef] bg-white px-4 py-14 text-center text-sm text-[#8792a3]">暂无可展示的文本分块。</p>}{!loading && !error && chunks.length > 0 && visibleChunks.length === 0 && <p className="col-span-full rounded-xl border border-dashed border-[#dfe5ef] bg-white px-4 py-14 text-center text-sm text-[#8792a3]">未找到匹配的分块。</p>}{!loading && !error && visibleChunks.map((chunk) => <ChunkCard key={chunk.chunkId} chunk={chunk} sourceFileName={sourceFileName} selected={selectedChunk?.chunkId === chunk.chunkId} onSelect={onSelect} />)}</div><ChunkDrawer sourceFileName={sourceFileName} chunk={selectedChunk} onClose={onClose} onSave={onSave} /></section>
    {pagination}
  </>
}

/** 单个分块摘要卡片。 */
function ChunkCard({ chunk, sourceFileName, selected, onSelect }: { chunk: DocumentChunk; sourceFileName: string; selected: boolean; onSelect: (chunk: DocumentChunk) => void }) {
  return <button type="button" aria-label={`查看分块 ${chunk.chunkOrder}`} aria-pressed={selected} onClick={() => onSelect(chunk)} className={`min-h-[187px] rounded-xl border p-[14px] text-left transition ${selected ? 'border-[#6a6ce0] bg-[#f7f7ff] shadow-[0_0_0_2px_#e6e7ff]' : 'border-[#e0e6ef] bg-white hover:border-[#bfc5f2] hover:shadow-[0_7px_18px_rgba(73,80,163,0.05)]'}`}><span className="flex items-center justify-between gap-2 text-[10px] text-[#6870d8]"><span className="flex min-w-0 items-center gap-1 truncate"><Link2 className="size-3 shrink-0" />{sourceFileName}</span><span className="text-sm text-[#77839a]">···</span></span><h3 className="mt-4 line-clamp-2 text-[13px] font-semibold text-[#3e4a61]">{getChunkTitle(chunk.text, chunk.chunkOrder)}</h3><p className="mt-2 line-clamp-4 text-[11px] leading-[1.55] text-[#8290a2]">{chunk.text}</p><span className="mt-4 flex justify-between text-[9px] text-[#a0a9b7]"><span>分块 {chunk.chunkOrder}</span><span>{chunk.status === 'INDEXED' ? '已索引' : chunk.status || '处理中'}</span></span></button>
}

/** 右侧分块编辑抽屉。 */
function ChunkDrawer({ sourceFileName, chunk, onClose, onSave }: { sourceFileName: string; chunk: DocumentChunk | null; onClose: () => void; onSave: (chunk: DocumentChunk, text: string) => void }) {
  const [draft, setDraft] = useState('')
  useEffect(() => setDraft(chunk?.text || ''), [chunk])

  if (!chunk) return <aside aria-label="分块详情栏" className="min-h-[506px] rounded-xl border border-dashed border-[#dfe5ef] bg-white p-5"><p className="text-sm font-semibold text-[#3e4a61]">分块内容</p><p className="mt-2 text-[11px] leading-5 text-[#8792a3]">选择一个分块后，可在这里预览并修改内容。</p></aside>

  return <aside aria-label="分块详情栏" className="sticky top-4 flex min-h-[506px] flex-col overflow-hidden rounded-xl border border-[#dfe5ef] bg-white"><header className="flex items-start justify-between gap-3 border-b border-[#edf0f4] p-[17px]"><div className="min-w-0"><h2 className="truncate text-sm font-semibold text-[#3e4a61]">{getChunkTitle(chunk.text, chunk.chunkOrder)}</h2><p className="mt-1 text-[10px] text-[#8792a3]">分块 {chunk.chunkOrder} · {chunk.status === 'INDEXED' ? '已索引' : chunk.status || '处理中'}</p></div><button type="button" aria-label="关闭分块内容" onClick={onClose} className="text-lg leading-none text-[#7e8999]">×</button></header><div className="flex-1 p-[15px]"><section className="rounded-lg bg-[#f7f8fc] p-2.5"><b className="block text-[9px] text-[#66748c]">来源文件</b><div className="mt-2 flex min-w-0 items-center gap-2 text-[11px] text-[#3e4b61]"><span className="flex size-6 shrink-0 items-center justify-center rounded-md bg-[#fff0ef] text-[8px] font-bold text-[#d66362]">PDF</span><span className="truncate">{sourceFileName}</span></div></section><label className="mt-4 block text-[10px] font-semibold text-[#647188]">分块内容</label><textarea value={draft} onChange={(event) => setDraft(event.target.value)} className="mt-2 min-h-[230px] w-full resize-y rounded-lg border border-[#d9e0ea] bg-[#fcfcfd] p-2.5 text-[11px] leading-[1.65] text-[#506078] outline-none transition focus:border-[#7475df] focus:ring-2 focus:ring-[#e8e9ff]" /><p className="mt-2 text-right text-[9px] text-[#9aa4b3]">{draft.length} / 20,000 字符</p></div><footer className="flex justify-end gap-2 border-t border-[#edf0f4] px-[15px] py-[13px]"><button type="button" onClick={() => setDraft(chunk.text)} className="rounded-lg border border-[#dde3ed] bg-white px-3 py-2 text-[10px] text-[#69768b]">取消</button><button type="button" onClick={() => onSave(chunk, draft)} className="rounded-lg bg-[#5b5ed2] px-3 py-2 text-[10px] font-semibold text-white hover:bg-[#4f52c5]">保存修改</button></footer></aside>
}

/** 取正文首个有效行作为卡片标题。 */
function getChunkTitle(text: string, chunkOrder: number): string { return text.split('\n').map((line) => line.trim()).find(Boolean) || `分块 ${chunkOrder}` }
