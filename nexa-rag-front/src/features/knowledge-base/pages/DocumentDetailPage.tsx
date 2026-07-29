import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { ArrowLeft, FileText, RefreshCw } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import type { PageVO } from '@/shared/api/types'
import { DocumentStatusBadge } from '../components/DocumentStatusBadge'
import { getDocument, getDocumentChunks, getDocumentProcessStatus, processDocument, retryDocument, type DocumentChunk, type DocumentDetail, type DocumentProcessStatus } from '../api/document-api'
import { type DocumentStatus } from '../document-status'
import { useDocumentStatusPolling } from '../hooks/useDocumentStatusPolling'
import { DocumentChunkBrowser } from '../components/DocumentChunkBrowser'

const CHUNK_PAGE_SIZE = 20
const EMPTY_CHUNKS: PageVO<DocumentChunk> = { records: [], total: 0, current: 1, size: CHUNK_PAGE_SIZE, pages: 0 }

/** 文档分块工作区，按分块展示原型组织文档信息与右侧编辑抽屉。 */
export function DocumentDetailPage() {
  const { documentId: documentIdParam } = useParams()
  const documentId = parseDocumentId(documentIdParam)
  const [document, setDocument] = useState<DocumentDetail | null>(null)
  const [processStatus, setProcessStatus] = useState<DocumentProcessStatus | null>(null)
  const [detailLoading, setDetailLoading] = useState(true)
  const [detailError, setDetailError] = useState<string | null>(null)
  const [processError, setProcessError] = useState<string | null>(null)
  const [chunks, setChunks] = useState<PageVO<DocumentChunk>>(EMPTY_CHUNKS)
  const [chunkPage, setChunkPage] = useState(1)
  const [chunksLoading, setChunksLoading] = useState(false)
  const [chunksError, setChunksError] = useState<string | null>(null)
  const [selectedChunk, setSelectedChunk] = useState<DocumentChunk | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const detailControllerRef = useRef<AbortController | null>(null)
  const processControllerRef = useRef<AbortController | null>(null)
  const chunksControllerRef = useRef<AbortController | null>(null)
  const currentStatus: DocumentStatus | null = processStatus?.status ?? document?.status ?? null

  const loadProcessStatus = useCallback(async (targetDocumentId: number) => {
    processControllerRef.current?.abort()
    const controller = new AbortController()
    processControllerRef.current = controller
    setProcessError(null)
    try {
      // 1. 状态独立加载，使重新处理反馈不影响内容工作区。
      const response = await getDocumentProcessStatus(targetDocumentId, controller.signal)
      if (!controller.signal.aborted) setProcessStatus(response)
    } catch (error) {
      if ((error as { name?: string }).name !== 'AbortError') setProcessError(error instanceof Error ? error.message : '处理状态加载失败，请稍后重试')
    }
  }, [])

  const loadDetail = useCallback(async (targetDocumentId: number) => {
    detailControllerRef.current?.abort()
    const controller = new AbortController()
    detailControllerRef.current = controller
    setDetailLoading(true)
    setDetailError(null)
    try {
      // 1. 加载当前文档的文件元数据，供工作区头部使用。
      const response = await getDocument(targetDocumentId, controller.signal)
      if (!controller.signal.aborted) setDocument(response)
    } catch (error) {
      if ((error as { name?: string }).name !== 'AbortError') setDetailError(error instanceof Error ? error.message : '文档详情加载失败，请稍后重试')
    } finally {
      if (!controller.signal.aborted) setDetailLoading(false)
    }
  }, [])

  const loadChunks = useCallback(async (targetDocumentId: number, targetPage: number) => {
    chunksControllerRef.current?.abort()
    const controller = new AbortController()
    chunksControllerRef.current = controller
    setChunksLoading(true)
    setChunksError(null)
    try {
      // 1. 分块按服务端分页读取，控制大文件的页面负载。
      const response = await getDocumentChunks(targetDocumentId, targetPage, CHUNK_PAGE_SIZE, controller.signal)
      if (!controller.signal.aborted) setChunks(response)
    } catch (error) {
      if ((error as { name?: string }).name !== 'AbortError') setChunksError(error instanceof Error ? error.message : '文本分块加载失败，请稍后重试')
    } finally {
      if (!controller.signal.aborted) setChunksLoading(false)
    }
  }, [])

  useEffect(() => {
    if (documentId === null) return
    // 1. 并行加载文档基础信息和处理状态。
    void loadDetail(documentId)
    void loadProcessStatus(documentId)
    // 2. 页面离开时取消未完成请求。
    return () => { detailControllerRef.current?.abort(); processControllerRef.current?.abort(); chunksControllerRef.current?.abort() }
  }, [documentId, loadDetail, loadProcessStatus])

  useEffect(() => {
    if (documentId !== null && currentStatus === 'INDEXED') void loadChunks(documentId, chunkPage)
  }, [chunkPage, currentStatus, documentId, loadChunks])

  const onPolledStatus = useCallback((value: DocumentProcessStatus) => setProcessStatus(value), [])
  const onPollingError = useCallback((error: Error) => setProcessError(error.message), [])
  useDocumentStatusPolling(documentId, currentStatus, onPolledStatus, onPollingError)

  const submitProcess = async (action: 'process' | 'retry') => {
    if (documentId === null || submitting) return
    const controller = new AbortController()
    setSubmitting(true)
    setProcessError(null)
    try {
      // 1. 根据当前状态发起初次处理或失败重试。
      const response = action === 'process' ? await processDocument(documentId, controller.signal) : await retryDocument(documentId, controller.signal)
      // 2. 先同步返回状态，后续状态继续由轮询更新。
      setProcessStatus(response)
      setDocument((current) => current ? { ...current, status: response.status } : current)
    } catch (error) {
      setProcessError(error instanceof Error ? error.message : '提交处理任务失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  const handleChunkPageChange = (nextPage: number) => {
    // 1. 翻页时清空当前选择，避免显示上一页分块。
    setSelectedChunk(null)
    // 2. 更新页码后由读取副作用加载目标页。
    setChunkPage(nextPage)
  }

  const handleChunkSave = (chunk: DocumentChunk, text: string) => {
    // 1. 当前后端未开放分块更新接口，先保留本次会话内的编辑结果。
    const updatedChunk = { ...chunk, text }
    // 2. 同步卡片与抽屉的展示内容，保证保存后立即可见。
    setChunks((current) => ({ ...current, records: current.records.map((item) => item.chunkId === chunk.chunkId ? updatedChunk : item) }))
    setSelectedChunk(updatedChunk)
  }

  if (documentId === null) return <InvalidDocumentAddress />

  return <section className="min-h-full min-w-0 overflow-y-auto bg-[#f7f8fb] px-5 py-7 sm:px-8 lg:px-[38px]">
    {detailError && <LoadError message={detailError} onRetry={() => void loadDetail(documentId)} />}
    {detailLoading && !document && <div className="flex min-h-80 items-center justify-center text-sm text-[#8792a3]">正在加载文档详情…</div>}
    {document && <div className="mx-auto w-full max-w-[1240px]"><div className="flex items-center gap-1.5 text-[11px] text-[#8a95a8]"><Link to="/knowledge-base" className="hover:text-[#5b5ed2]">知识库</Link><span>/</span><Link to="/knowledge-base?view=documents" className="hover:text-[#5b5ed2]">默认知识库</Link><span>/</span><span>全部文档</span></div><header className="mt-2 flex flex-wrap items-start justify-between gap-4"><div className="flex min-w-0 items-center gap-3"><span className="flex size-[37px] shrink-0 items-center justify-center rounded-[10px] bg-[#eef0ff] text-[10px] font-bold text-[#5b61cc]">{document.fileType || '文件'}</span><div className="min-w-0"><h1 className="truncate text-[22px] font-semibold tracking-[-0.035em] text-[#2f2d38]">{document.title || document.originalFileName || '未命名文档'}</h1><p className="mt-1 text-[11px] text-[#8490a1]">{document.originalFileName || '未提供原始文件名'}</p></div></div><div className="flex items-center gap-2"><Link to="/knowledge-base?view=documents" className="inline-flex h-9 items-center gap-1 rounded-lg border border-[#dee4ee] bg-white px-3 text-[11px] font-semibold text-[#66748a]"><ArrowLeft className="size-3.5" />返回文档</Link>{currentStatus === 'UPLOADED' && <Button onClick={() => void submitProcess('process')} disabled={submitting} className="h-9 rounded-lg bg-[#5b5ed2] px-3 text-[11px] hover:bg-[#4f52c5]">{submitting ? '提交中…' : '开始处理'}</Button>}{currentStatus === 'FAILED' && <Button onClick={() => void submitProcess('retry')} disabled={submitting} className="h-9 rounded-lg bg-[#5b5ed2] px-3 text-[11px] hover:bg-[#4f52c5]">{submitting ? '提交中…' : '重新处理'}</Button>}</div></header><section className="mt-5 grid gap-4 rounded-[10px] border border-[#e0e6ee] bg-white p-4 sm:grid-cols-2 lg:grid-cols-4"><InfoItem label="文档类型">{document.fileType || '—'}</InfoItem><InfoItem label="文件大小">{formatFileSize(document.fileSize)}</InfoItem><InfoItem label="处理状态">{currentStatus ? <DocumentStatusBadge status={currentStatus} /> : '状态未知'}</InfoItem><InfoItem label="文本分块">{chunks.total} 个</InfoItem></section><nav className="mt-6 flex gap-6 border-b border-[#e3e7ef]"><span className="pb-2.5 text-xs text-[#8a95a6]">文档概览</span><span className="border-b-2 border-[#5b5ed2] pb-2.5 text-xs font-semibold text-[#5359c7]">文本分块</span></nav>{(document.description || processStatus?.messageStatus || currentStatus === 'FAILED' || processError) && <section className="mt-4 rounded-lg border border-[#e0e6ee] bg-white px-4 py-3 text-xs text-[#728097]"><p>{document.description || '文档处理状态与分块内容将在此工作区持续更新。'}</p>{processStatus?.messageStatus && <p className="mt-2">任务信息：{processStatus.messageStatus}</p>}{currentStatus === 'FAILED' && <p className="mt-2 text-[#bd5252]">失败阶段：{processStatus?.failureStage || '未知'}；失败原因：{processStatus?.failureReason || '后端未返回具体原因'}</p>}{processError && <p className="mt-2 text-[#bd5252]">{processError}</p>}</section>}{currentStatus !== 'INDEXED' ? <section className="mt-6 rounded-xl border border-[#e0e6ee] bg-white px-5 py-12 text-center"><FileText className="mx-auto size-7 text-[#9aa4b3]" /><h2 className="mt-3 text-sm font-semibold text-[#3e4a61]">分块尚未就绪</h2><p className="mt-2 text-xs text-[#8290a2]">文档索引完成后，可在此查看并管理文本分块。</p></section> : <DocumentChunkBrowser chunks={chunks.records} sourceFileName={document.originalFileName || document.title || '未命名文档'} loading={chunksLoading} error={chunksError} selectedChunk={selectedChunk} pagination={<ChunkPagination page={chunks} pageNum={chunkPage} onChange={handleChunkPageChange} />} onSelect={setSelectedChunk} onClose={() => setSelectedChunk(null)} onRetry={() => void loadChunks(documentId, chunkPage)} onSave={handleChunkSave} />}</div>}
  </section>
}

/** 文档元信息字段。 */
function InfoItem({ label, children }: { label: string; children: ReactNode }) { return <div className="text-[11px]"><b className="mb-1 block text-[9px] font-semibold tracking-[0.04em] text-[#9aa4b3]">{label}</b><span className="text-[#536075]">{children}</span></div> }

/** 无效地址提示。 */
function InvalidDocumentAddress() { return <section className="flex min-h-full min-w-0 flex-1 items-center justify-center bg-[#f7f8fb] p-6"><div className="text-center"><h1 className="text-xl font-semibold text-[#3e4a61]">文档地址无效</h1><Link to="/knowledge-base" className="mt-4 inline-flex text-sm text-[#5b5ed2]">返回知识库</Link></div></section> }

/** 详情请求失败提示。 */
function LoadError({ message, onRetry }: { message: string; onRetry: () => void }) { return <div className="mx-auto mb-5 flex w-full max-w-[1240px] flex-wrap items-center justify-between gap-3 rounded-xl border border-[#f3cfca] bg-[#fff4f2] px-4 py-3 text-sm text-[#b6574d]"><span>{message}</span><button type="button" onClick={onRetry} className="inline-flex items-center gap-1.5 rounded-lg bg-white px-3 py-2 text-xs text-[#a85049]"><RefreshCw className="size-3.5" />重新加载</button></div> }

/** 分块分页控件。 */
function ChunkPagination({ page, pageNum, onChange }: { page: PageVO<DocumentChunk>; pageNum: number; onChange: (pageNum: number) => void }) { const totalPages = Math.max(page.pages, 1); return <div className="mt-5 flex items-center justify-end gap-3 text-[11px] text-[#818c9e]"><button type="button" disabled={pageNum <= 1} onClick={() => onChange(pageNum - 1)} className="rounded-lg border border-[#e0e5ee] bg-white px-3 py-2 disabled:cursor-not-allowed disabled:opacity-40">上一页</button><span>第 {pageNum} / {totalPages} 页</span><button type="button" disabled={pageNum >= totalPages} onClick={() => onChange(pageNum + 1)} className="rounded-lg border border-[#e0e5ee] bg-white px-3 py-2 disabled:cursor-not-allowed disabled:opacity-40">下一页</button></div> }

function parseDocumentId(value: string | undefined): number | null { const parsed = Number(value); return Number.isInteger(parsed) && parsed > 0 ? parsed : null }
function formatFileSize(value: number | null): string { if (value === null || value < 0) return '—'; if (value < 1024) return `${value} B`; if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`; return `${(value / (1024 * 1024)).toFixed(1)} MB` }
