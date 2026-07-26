import { useCallback, useEffect, useRef, useState } from 'react'
import { ArrowLeft, FileText, RefreshCw } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import type { PageVO } from '@/shared/api/types'
import { getDocument, getDocumentChunks, getDocumentProcessStatus, processDocument, retryDocument, type DocumentChunk, type DocumentDetail, type DocumentProcessStatus } from '../api/document-api'
import { statusLabel, type DocumentStatus } from '../document-status'
import { useDocumentStatusPolling } from '../hooks/useDocumentStatusPolling'
import { DocumentChunkBrowser } from '../components/DocumentChunkBrowser'

const CHUNK_PAGE_SIZE = 20
const EMPTY_CHUNKS: PageVO<DocumentChunk> = { records: [], total: 0, current: 1, size: CHUNK_PAGE_SIZE, pages: 0 }

/** 知识库文档详情页面，以知识块工作区展示已索引文档。 */
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
      // 1. 处理状态独立请求，错误只影响处理状态区域。
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
      // 1. 仅加载工作区所需元数据，不展示对象存储文件地址。
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
      // 1. 文本分块使用服务端分页，避免一次性加载过多文本。
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
    // 1. 详情和处理状态分别加载，确保每个区域可独立重试。
    void loadDetail(documentId)
    void loadProcessStatus(documentId)
    // 2. 离开页面时取消所有未完成请求。
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
      // 1. 按状态提交开始处理或重新处理请求。
      const response = action === 'process' ? await processDocument(documentId, controller.signal) : await retryDocument(documentId, controller.signal)
      // 2. 先同步返回状态，再由轮询更新后续进度。
      setProcessStatus(response)
      setDocument((current) => current ? { ...current, status: response.status } : current)
    } catch (error) {
      setProcessError(error instanceof Error ? error.message : '提交处理任务失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  const handleChunkPageChange = (nextPage: number) => {
    // 1. 翻页前清空当前选择，避免右侧保留上一页分块内容。
    setSelectedChunk(null)
    // 2. 更新页码后由现有副作用请求服务端目标页。
    setChunkPage(nextPage)
  }

  if (documentId === null) return <InvalidDocumentAddress />

  return <section className="min-w-0 flex-1 overflow-y-auto bg-slate-50">
    {detailError && <div className="p-6 md:px-8"><ErrorPanel message={detailError} onRetry={() => void loadDetail(documentId)} /></div>}
    {detailLoading && !document && <div className="flex min-h-80 items-center justify-center text-sm text-muted-foreground">正在加载文档详情…</div>}
    {document && <>
      <header className="border-b bg-white px-6 py-5 md:px-8">
        <Link to="/knowledge-base" className="inline-flex w-fit items-center gap-2 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />返回文档列表</Link>
        <div className="mt-5 flex flex-wrap items-start justify-between gap-5">
          <div className="flex min-w-0 items-start gap-3"><span className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-blue-50 text-blue-600"><FileText className="size-5" /></span><div className="min-w-0"><h1 className="truncate text-xl font-semibold text-slate-950">{document.title || document.originalFileName || '未命名文档'}</h1><div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground"><span>{document.originalFileName || '未提供原始文件名'}</span><span>{document.fileType || '未知类型'}</span><span>{formatFileSize(document.fileSize)}</span><span>{chunks.total} 个知识块</span></div></div></div>
          <div className="flex flex-wrap items-center gap-3"><span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-700">{currentStatus ? statusLabel(currentStatus) : '状态未知'}</span>{currentStatus === 'UPLOADED' && <Button onClick={() => void submitProcess('process')} disabled={submitting}>{submitting ? '提交中…' : '开始处理'}</Button>}{currentStatus === 'FAILED' && <Button onClick={() => void submitProcess('retry')} disabled={submitting}>{submitting ? '提交中…' : '重新处理'}</Button>}</div>
        </div>
        {(document.description || processStatus?.messageStatus || processError || currentStatus === 'FAILED') && <div className="mt-4 border-t pt-4 text-sm"><p className="text-muted-foreground">{document.description || '未填写文档描述'}</p>{processStatus?.messageStatus && <p className="mt-2 text-muted-foreground">任务信息：{processStatus.messageStatus}</p>}{currentStatus === 'FAILED' && <p className="mt-2 text-red-700">失败阶段：{processStatus?.failureStage || '未知'}；失败原因：{processStatus?.failureReason || '后端未返回具体原因'}</p>}{processError && <ErrorPanel message={processError} onRetry={() => void loadProcessStatus(documentId)} />}</div>}
      </header>
      <section aria-label="知识块工作区" className="px-6 py-6 md:px-8">
        {currentStatus !== 'INDEXED' && <p className="text-sm text-muted-foreground">文档索引完成后可查看文本分块。</p>}
        {currentStatus === 'INDEXED' && <DocumentChunkBrowser chunks={chunks.records} sourceFileName={document.originalFileName || document.title || '未命名文档'} loading={chunksLoading} error={chunksError} selectedChunk={selectedChunk} pagination={<ChunkPagination page={chunks} pageNum={chunkPage} onChange={handleChunkPageChange} />} onSelect={setSelectedChunk} onClose={() => setSelectedChunk(null)} onRetry={() => void loadChunks(documentId, chunkPage)} />}
      </section>
    </>}
  </section>
}

/** 非法文档地址提示。 */
function InvalidDocumentAddress() { return <section className="flex min-w-0 flex-1 items-center justify-center p-6"><div className="text-center"><h1 className="text-xl font-semibold">文档地址无效</h1><Link to="/knowledge-base" className="mt-4 inline-flex text-sm text-blue-600 hover:underline">返回文档列表</Link></div></section> }

/** 独立区域的可重试错误提示。 */
function ErrorPanel({ message, onRetry }: { message: string; onRetry: () => void }) { return <div className="mt-3 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"><span>{message}</span><Button variant="outline" size="sm" onClick={onRetry}><RefreshCw className="size-3.5" />重新加载</Button></div> }

/** 已索引文档的文本分块分页控件。 */
function ChunkPagination({ page, pageNum, onChange }: { page: PageVO<DocumentChunk>; pageNum: number; onChange: (pageNum: number) => void }) { const totalPages = Math.max(page.pages, 1); return <div className="mt-5 flex items-center justify-end gap-3 text-sm text-muted-foreground"><Button variant="outline" size="sm" disabled={pageNum <= 1} onClick={() => onChange(pageNum - 1)}>上一页</Button><span>第 {pageNum} / {totalPages} 页</span><Button variant="outline" size="sm" disabled={pageNum >= totalPages} onClick={() => onChange(pageNum + 1)}>下一页</Button></div> }

function parseDocumentId(value: string | undefined): number | null { const parsed = Number(value); return Number.isInteger(parsed) && parsed > 0 ? parsed : null }
function formatFileSize(value: number | null): string { if (value === null || value < 0) return '—'; if (value < 1024) return `${value} B`; if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`; return `${(value / (1024 * 1024)).toFixed(1)} MB` }
