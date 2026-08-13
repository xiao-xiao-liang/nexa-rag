import { useCallback, useEffect, useRef, useState } from 'react'
import { ArrowLeft, ChevronRight, Clock, Database, Download, ExternalLink, FileText, HardDrive, Info, Layers, RefreshCw, Sparkles } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import type { PageVO } from '@/shared/api/types'
import { DocumentStatusBadge } from '../components/DocumentStatusBadge'
import { FileTypeIcon } from '../components/FileTypeIcon'
import { getDocument, getDocumentChunks, getDocumentProcessStatus, processDocument, retryDocument, type DocumentChunk, type DocumentDetail, type DocumentProcessStatus } from '../api/document-api'
import { isProcessingStatus, type DocumentStatus } from '../document-status'
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

  const loadProcessStatus = useCallback(async (targetDocumentId: string | number) => {
    processControllerRef.current?.abort()
    const controller = new AbortController()
    processControllerRef.current = controller
    setProcessError(null)
    try {
      const response = await getDocumentProcessStatus(targetDocumentId, controller.signal)
      if (!controller.signal.aborted) setProcessStatus(response)
    } catch (error) {
      if ((error as { name?: string }).name !== 'AbortError') setProcessError(error instanceof Error ? error.message : '处理状态加载失败，请稍后重试')
    }
  }, [])

  const loadDetail = useCallback(async (targetDocumentId: string | number) => {
    detailControllerRef.current?.abort()
    const controller = new AbortController()
    detailControllerRef.current = controller
    setDetailLoading(true)
    setDetailError(null)
    try {
      const response = await getDocument(targetDocumentId, controller.signal)
      if (!controller.signal.aborted) setDocument(response)
    } catch (error) {
      if ((error as { name?: string }).name !== 'AbortError') setDetailError(error instanceof Error ? error.message : '文档详情加载失败，请稍后重试')
    } finally {
      if (!controller.signal.aborted) setDetailLoading(false)
    }
  }, [])

  const loadChunks = useCallback(async (targetDocumentId: string | number, targetPage: number) => {
    chunksControllerRef.current?.abort()
    const controller = new AbortController()
    chunksControllerRef.current = controller
    setChunksLoading(true)
    setChunksError(null)
    try {
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
    void loadDetail(documentId)
    void loadProcessStatus(documentId)
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
      const response = action === 'process' ? await processDocument(documentId, controller.signal) : await retryDocument(documentId, controller.signal)
      setProcessStatus(response)
      setDocument((current) => current ? { ...current, status: response.status } : current)
    } catch (error) {
      setProcessError(error instanceof Error ? error.message : '提交处理任务失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  const handleChunkPageChange = (nextPage: number) => {
    setSelectedChunk(null)
    setChunkPage(nextPage)
  }

  const handleChunkSave = (chunk: DocumentChunk, text: string) => {
    const updatedChunk = { ...chunk, text }
    setChunks((current) => ({ ...current, records: current.records.map((item) => item.chunkId === chunk.chunkId ? updatedChunk : item) }))
    setSelectedChunk(updatedChunk)
  }

  if (documentId === null) return <InvalidDocumentAddress />

  const isProcessing = currentStatus !== null && isProcessingStatus(currentStatus)

  return (
    <section className="min-h-full min-w-0 overflow-y-auto bg-background px-4 py-5 sm:px-6 lg:px-10">
      {detailError && <LoadError message={detailError} onRetry={() => void loadDetail(documentId)} />}      
      {detailLoading && !document && (
        <div className="flex min-h-[400px] flex-col items-center justify-center gap-3 text-tertiary">
          <RefreshCw className="size-6 animate-spin text-primary" />
          <span className="text-sm font-medium">正在加载文档详情…</span>
        </div>
      )}

      {document && (
        <div className="mx-auto w-full max-w-[1280px] space-y-5">
          {/* 面包屑导航 */}
          <nav aria-label="Breadcrumb" className="flex items-center gap-2 text-xs text-tertiary">
            <Link to="/knowledge-base" className="transition-colors hover:text-primary">知识库</Link>
            <ChevronRight className="size-3.5 text-tertiary" />
            <Link to="/knowledge-base?view=documents" className="transition-colors hover:text-primary">默认知识库</Link>
            <ChevronRight className="size-3.5 text-tertiary" />
            <span className="font-medium text-secondary">全部文档</span>
          </nav>

          {/* 统一 Header 主 Banner 卡片 */}
          <header className="rounded-lg border border-border bg-card p-5 space-y-4">
            {/* 上层：标题 + 操作组 */}
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div className="flex min-w-0 items-center gap-4">
                <div className="rounded-md border border-border bg-muted p-1.5">
                  <FileTypeIcon fileName={document.originalFileName} fileType={document.fileType} size="lg" />
                </div>
                <div className="min-w-0 space-y-1">
                  <h1 className="truncate text-xl font-semibold text-foreground">
                    {document.title || document.originalFileName || '未命名文档'}
                  </h1>
                  <p className="flex items-center gap-1.5 text-xs text-secondary">
                    <span className="truncate">{document.originalFileName || '未提供原始文件名'}</span>
                  </p>
                </div>
              </div>

              {/* 动作区：返回 + 源文件预览下载 + 处理/重试按钮 */}
              <div className="flex flex-wrap items-center gap-2.5 shrink-0">
                {document.originalFileUrl && (
                  <a
                    href={document.originalFileUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex h-8 items-center gap-1.5 rounded-md border border-border bg-muted px-3 text-xs text-secondary transition-colors hover:bg-muted/70 hover:text-primary"
                  >
                    <Download className="size-3.5" />
                    下载源文件
                  </a>
                )}

                {document.parsedFileUrl && (
                  <a
                    href={document.parsedFileUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex h-8 items-center gap-1.5 rounded-md border border-border bg-muted px-3 text-xs text-secondary transition-colors hover:bg-muted/70 hover:text-primary"
                  >
                    <ExternalLink className="size-3.5" />
                    预览 Markdown
                  </a>
                )}

                <Link
                  to="/knowledge-base?view=documents"
                  className="inline-flex h-8 items-center gap-1.5 rounded-md border border-border bg-card px-3.5 text-xs text-secondary transition-colors hover:bg-muted hover:text-foreground"
                >
                  <ArrowLeft className="size-3.5" />
                  返回文档
                </Link>

                {currentStatus === 'UPLOADED' && (
                  <Button
                    onClick={() => void submitProcess('process')}
                    disabled={submitting}
                    className="h-8 rounded-md bg-primary px-3.5 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
                  >
                    {submitting ? '提交中…' : '开始处理'}
                  </Button>
                )}

                {currentStatus === 'FAILED' && (
                  <Button
                    onClick={() => void submitProcess('retry')}
                    disabled={submitting}
                    className="h-8 rounded-md bg-danger px-3.5 text-sm font-medium text-white hover:bg-danger/90 disabled:opacity-50"
                  >
                    {submitting ? '提交中…' : '重新处理'}
                  </Button>
                )}
              </div>
            </div>

            {/* 文档描述字段 (description) */}
            {document.description && (
              <div className="flex items-center gap-2 rounded-md bg-muted border border-border px-3.5 py-2 text-xs text-secondary">
                <Info className="size-3.5 shrink-0 text-tertiary" />
                <span className="font-medium text-tertiary mr-1">描述:</span>
                <span className="truncate text-secondary">{document.description}</span>
              </div>
            )}

            {/* 动态 Pipeline 真实进度条 (处理中状态时展现) */}
            {isProcessing && (
              <div className="rounded-md border border-primary/30 bg-primary-light p-3.5 space-y-2">
                <div className="flex items-center justify-between text-xs font-medium text-foreground">
                  <span className="flex items-center gap-1.5">
                    <RefreshCw className="size-3.5 animate-spin text-primary" />
                    文档处理流水线进行中…
                  </span>
                  {processStatus?.consumedTimes !== null && processStatus?.consumedTimes !== undefined && (
                    <span className="flex items-center gap-1 font-mono text-[11px] font-normal text-primary">
                      <Clock className="size-3" />
                      已耗时 {processStatus.consumedTimes} 秒
                    </span>
                  )}
                </div>
                <div className="grid grid-cols-3 gap-2 text-center text-[10px] font-medium">
                  <span className={`rounded py-1.5 transition-colors ${currentStatus === 'QUEUED' || currentStatus === 'PARSING' ? 'bg-primary text-white' : 'bg-success-light text-success'}`}>
                    1. 格式解析
                  </span>
                  <span className={`rounded py-1.5 transition-colors ${currentStatus === 'CHUNKING' ? 'bg-primary text-white' : currentStatus === 'INDEXING' ? 'bg-success-light text-success' : 'bg-muted text-tertiary'}`}>
                    2. 智能切分
                  </span>
                  <span className={`rounded py-1.5 transition-colors ${currentStatus === 'INDEXING' ? 'bg-primary text-white' : 'bg-muted text-tertiary'}`}>
                    3. 向量索引
                  </span>
                </div>
              </div>
            )}

            {/* 下层：4项基本指标横栏 */}
            <div className="grid grid-cols-2 gap-4 border-t border-border pt-4 sm:grid-cols-4">
              <div className="flex items-center gap-3">
                <div className="rounded-md bg-muted p-2.5 text-primary"><FileText className="size-4" /></div>
                <div>
                  <span className="block text-[10px] font-medium uppercase tracking-wider text-tertiary">文档类型</span>
                  <span className="text-xs font-semibold text-foreground">{document.fileType || '—'}</span>
                </div>
              </div>

              <div className="flex items-center gap-3">
                <div className="rounded-md bg-muted p-2.5 text-secondary"><HardDrive className="size-4" /></div>
                <div>
                  <span className="block text-[10px] font-medium uppercase tracking-wider text-tertiary">文件大小</span>
                  <span className="text-xs font-semibold text-foreground">{formatFileSize(document.fileSize)}</span>
                </div>
              </div>

              <div className="flex items-center gap-3">
                <div className="rounded-md bg-muted p-2.5 text-warning"><Database className="size-4" /></div>
                <div>
                  <span className="block text-[10px] font-medium uppercase tracking-wider text-tertiary">处理状态</span>
                  <div>{currentStatus ? <DocumentStatusBadge status={currentStatus} /> : <span className="text-tertiary text-xs">状态未知</span>}</div>
                </div>
              </div>

              <div className="flex items-center gap-3">
                <div className="rounded-md bg-muted p-2.5 text-success"><Layers className="size-4" /></div>
                <div>
                  <span className="block text-[10px] font-medium uppercase tracking-wider text-tertiary">文本分块</span>
                  <span className="text-xs font-semibold text-foreground">{chunks.total} <span className="font-normal text-tertiary">个</span></span>
                </div>
              </div>
            </div>
          </header>

          {/* Tab 导航 */}
          <nav aria-label="文档视图选项" className="flex items-center gap-5 border-b border-border px-1 pt-1">
            <span className="pb-2.5 text-sm text-tertiary">文档概览</span>
            <span className="relative pb-2.5 text-sm font-medium text-primary after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:rounded-full after:bg-primary">
              文本分块
            </span>
          </nav>

          {/* 异常诊断提示 */}
          {(processStatus?.messageStatus || currentStatus === 'FAILED' || processError) && (
            <section className="rounded-lg border border-border bg-card p-4">
              <div className="flex items-start gap-3">
                <div className="rounded-md bg-primary-light p-2 text-primary">
                  <Sparkles className="size-4" />
                </div>
                <div className="space-y-1.5 text-xs">
                  {processStatus?.messageStatus && (
                    <p className="font-medium text-secondary">任务信息：{processStatus.messageStatus}</p>
                  )}
                  {currentStatus === 'FAILED' && (
                    <p className="rounded bg-danger-light p-2.5 font-medium text-danger">
                      失败阶段：{processStatus?.failureStage || '未知'}；失败原因：{processStatus?.failureReason || '后端未返回具体原因'}
                    </p>
                  )}
                  {processError && <p className="font-medium text-danger">{processError}</p>}
                </div>
              </div>
            </section>
          )}

          {/* 分块内容工作区 或 未就绪空状态 */}
          {currentStatus !== 'INDEXED' ? (
            <section className="flex flex-col items-center justify-center rounded-lg border border-dashed border-border bg-card px-6 py-16 text-center">
              <div className="rounded-md bg-primary-light p-4 text-primary">
                <FileText className="size-8" />
              </div>
              <h2 className="mt-4 text-base font-semibold text-foreground">分块尚未就绪</h2>
              <p className="mt-1 max-w-sm text-xs text-secondary">文档解析与向量索引完成后，可在此查看并在线编辑调整文本分块。</p>
            </section>
          ) : (
            <DocumentChunkBrowser
              chunks={chunks.records}
              sourceFileName={document.originalFileName || document.title || '未命名文档'}
              fileDescription={document.description}
              loading={chunksLoading}
              error={chunksError}
              selectedChunk={selectedChunk}
              pagination={<ChunkPagination page={chunks} pageNum={chunkPage} onChange={handleChunkPageChange} />}
              onSelect={setSelectedChunk}
              onClose={() => setSelectedChunk(null)}
              onRetry={() => void loadChunks(documentId, chunkPage)}
              onSave={handleChunkSave}
            />
          )}
        </div>
      )}
    </section>
  )
}

/** 无效地址提示。 */
function InvalidDocumentAddress() {
  return (
    <section className="flex min-h-full min-w-0 flex-1 items-center justify-center bg-background p-6">
      <div className="max-w-md rounded-lg border border-border bg-card p-8 text-center">
        <h1 className="text-lg font-semibold text-foreground">文档地址无效</h1>
        <p className="mt-2 text-xs text-secondary">无法检索到对应的文档记录，请核对文档 URL 地址。</p>
        <Link
          to="/knowledge-base"
          className="mt-5 inline-flex items-center gap-1.5 rounded-md bg-primary px-4 py-2 text-xs font-medium text-white transition-colors hover:bg-primary/90"
        >
          返回文档列表
        </Link>
      </div>
    </section>
  )
}

/** 详情请求失败提示。 */
function LoadError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="mx-auto mb-6 flex w-full max-w-[1280px] flex-wrap items-center justify-between gap-3 rounded-md border border-danger-light bg-danger-light px-4 py-3 text-xs text-danger">
      <span className="font-medium">{message}</span>
      <button
        type="button"
        onClick={onRetry}
        className="inline-flex items-center gap-1.5 rounded border border-danger bg-card px-3 py-1.5 font-medium text-danger transition-colors hover:bg-danger-light"
      >
        <RefreshCw className="size-3.5" />
        重新加载
      </button>
    </div>
  )
}

/** 分块分页控件。 */
function ChunkPagination({ page, pageNum, onChange }: { page: PageVO<DocumentChunk>; pageNum: number; onChange: (pageNum: number) => void }) {
  const totalPages = Math.max(page.pages, 1)
  return (
    <div className="mt-6 flex items-center justify-between border-t border-border pt-4 text-xs text-secondary">
      <span className="text-tertiary">共 {page.total} 个文本分块</span>
      <div className="flex items-center gap-3">
        <button
          type="button"
          disabled={pageNum <= 1}
          onClick={() => onChange(pageNum - 1)}
          className="rounded border border-border bg-card px-3.5 py-1.5 font-medium transition-colors hover:bg-muted disabled:cursor-not-allowed disabled:opacity-40"
        >
          上一页
        </button>
        <span className="font-medium text-secondary">第 {pageNum} / {totalPages} 页</span>
        <button
          type="button"
          disabled={pageNum >= totalPages}
          onClick={() => onChange(pageNum + 1)}
          className="rounded border border-border bg-card px-3.5 py-1.5 font-medium transition-colors hover:bg-muted disabled:cursor-not-allowed disabled:opacity-40"
        >
          下一页
        </button>
      </div>
    </div>
  )
}

function parseDocumentId(value: string | undefined): string | null {
  const normalized = value?.trim()
  if (!normalized || !/^[1-9]\d*$/.test(normalized)) return null
  return normalized
}

function formatFileSize(value: number | null): string {
  if (value === null || value < 0) return '—'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}
