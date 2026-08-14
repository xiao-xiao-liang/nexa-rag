import { useCallback, useEffect, useRef, useState } from 'react'
import { ArrowLeft, ChevronRight, Clock, Download, ExternalLink, FileText, Info, RefreshCw, Sparkles } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Tabs } from '@/components/ui/tabs'
import { cn } from '@/lib/utils'
import type { PageVO } from '@/shared/api/types'
import { DocumentStatusBadge } from '../components/DocumentStatusBadge'
import { FileTypeIcon } from '../components/FileTypeIcon'
import { getDocument, getDocumentChunks, getDocumentProcessStatus, processDocument, retryDocument, type DocumentChunk, type DocumentDetail, type DocumentProcessStatus } from '../api/document-api'
import { isProcessingStatus, type DocumentStatus } from '../document-status'
import { useDocumentStatusPolling } from '../hooks/useDocumentStatusPolling'
import { DocumentChunkBrowser } from '../components/DocumentChunkBrowser'

const CHUNK_PAGE_SIZE = 20
const SCROLL_LOAD_THRESHOLD = 120
const EMPTY_CHUNKS: PageVO<DocumentChunk> = { records: [], total: 0, current: 1, size: CHUNK_PAGE_SIZE, pages: 0 }

type DocumentView = 'overview' | 'chunks'

/** 文档详情页：面包屑 + 标题行（含信息条）+ Tab + 知识块卡片网格，分块随滚动加载。 */
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
  const [activeView, setActiveView] = useState<DocumentView>('chunks')
  const detailControllerRef = useRef<AbortController | null>(null)
  const processControllerRef = useRef<AbortController | null>(null)
  const chunksControllerRef = useRef<AbortController | null>(null)
  const scrollRef = useRef<HTMLElement | null>(null)
  const scrollStateRef = useRef({ hasMore: false, loading: false })
  const currentStatus: DocumentStatus | null = processStatus?.status ?? document?.status ?? null
  const hasMore = chunkPage < Math.max(chunks.pages, 1) && chunks.total > 0
  scrollStateRef.current = { hasMore, loading: chunksLoading }

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

  const loadChunks = useCallback(async (targetDocumentId: string | number, targetPage: number, append: boolean) => {
    chunksControllerRef.current?.abort()
    const controller = new AbortController()
    chunksControllerRef.current = controller
    setChunksLoading(true)
    setChunksError(null)
    try {
      const response = await getDocumentChunks(targetDocumentId, targetPage, CHUNK_PAGE_SIZE, controller.signal)
      if (!controller.signal.aborted) {
        setChunks((current) => {
          if (!append) return response
          const seen = new Set(current.records.map((item) => item.chunkId))
          return { ...response, records: [...current.records, ...response.records.filter((item) => !seen.has(item.chunkId))] }
        })
      }
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

  // 切换文档时重置分块列表与选中态
  useEffect(() => {
    setChunks(EMPTY_CHUNKS)
    setChunkPage(1)
    setSelectedChunk(null)
  }, [documentId])

  // 已索引后加载分块；页码递增时追加下一页
  useEffect(() => {
    if (documentId === null || currentStatus !== 'INDEXED') return
    void loadChunks(documentId, chunkPage, chunkPage > 1)
  }, [chunkPage, currentStatus, documentId, loadChunks])

  // 滚动接近底部时触发下一页加载
  useEffect(() => {
    const container = scrollRef.current
    if (!container) return
    const handleScroll = () => {
      const state = scrollStateRef.current
      if (state.loading || !state.hasMore) return
      if (container.scrollTop + container.clientHeight >= container.scrollHeight - SCROLL_LOAD_THRESHOLD) {
        setChunkPage((page) => page + 1)
      }
    }
    container.addEventListener('scroll', handleScroll)
    return () => container.removeEventListener('scroll', handleScroll)
  }, [])

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

  const handleRefresh = () => {
    if (documentId === null || currentStatus !== 'INDEXED') return
    setSelectedChunk(null)
    setChunkPage(1)
    void loadChunks(documentId, 1, false)
  }

  const handleChunkSave = (chunk: DocumentChunk, text: string) => {
    const updatedChunk = { ...chunk, text }
    setChunks((current) => ({ ...current, records: current.records.map((item) => item.chunkId === chunk.chunkId ? updatedChunk : item) }))
    setSelectedChunk(updatedChunk)
  }

  const handleChunkDelete = (chunk: DocumentChunk) => {
    setChunks((current) => ({
      ...current,
      total: Math.max(0, current.total - 1),
      records: current.records.filter((item) => item.chunkId !== chunk.chunkId),
    }))
    setSelectedChunk(null)
  }

  const splitStrategyLabel = parseSplitStrategyLabel(document?.processConfigJson ?? null)

  if (documentId === null) return <InvalidDocumentAddress />

  const isProcessing = currentStatus !== null && isProcessingStatus(currentStatus)

  return (
    <section
      ref={scrollRef}
      data-testid="document-detail-scroll"
      className="h-full min-w-0 overflow-y-auto bg-background px-4 py-5 sm:px-6 lg:px-10"
    >
      {detailError && <LoadError message={detailError} onRetry={() => void loadDetail(documentId)} />}
      {detailLoading && !document && (
        <div className="flex min-h-[400px] flex-col items-center justify-center gap-3 text-tertiary">
          <RefreshCw className="size-6 animate-spin text-primary" />
          <span className="text-sm font-medium">正在加载文档详情…</span>
        </div>
      )}

      {document && (
        <div className="w-full space-y-4">
          {/* 面包屑导航 */}
          <nav aria-label="Breadcrumb" className="flex items-center gap-2 text-xs text-tertiary">
            <Link to="/knowledge-base" className="transition-colors hover:text-primary">知识库</Link>
            <ChevronRight className="size-3.5 text-tertiary" />
            <Link to="/knowledge-base?view=documents" className="transition-colors hover:text-primary">默认知识库</Link>
            <ChevronRight className="size-3.5 text-tertiary" />
            <span className="font-medium text-secondary">全部文档</span>
          </nav>

          {/* 标题行（含信息条） */}
          <header className="rounded-md border border-border bg-card p-4">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div className="flex min-w-0 items-center gap-3">
                <span className="rounded-md border border-border bg-muted p-2">
                  <FileTypeIcon fileName={document.originalFileName} fileType={document.fileType} size="lg" />
                </span>
                <div className="min-w-0">
                  <h1 className="truncate text-lg font-semibold text-foreground">
                    {document.title || document.originalFileName || '未命名文档'}
                  </h1>
                  <p className="mt-0.5 truncate text-xs text-secondary">
                    <span className="font-medium text-tertiary">原始文件名：</span>
                    <span>{document.originalFileName || '未提供原始文件名'}</span>
                    {currentStatus && (
                      <>
                        <span className="mx-1 text-tertiary">·</span>
                        <DocumentStatusBadge status={currentStatus} />
                      </>
                    )}
                  </p>
                </div>
              </div>

              {/* 动作区 */}
              <div className="flex shrink-0 flex-wrap items-center gap-2">
                {document.originalFileUrl && (
                  <a
                    href={document.originalFileUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex h-8 items-center gap-1.5 rounded-md border border-border bg-card px-3 text-xs text-secondary transition-colors hover:bg-muted hover:text-foreground"
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
                    className="inline-flex h-8 items-center gap-1.5 rounded-md border border-border bg-card px-3 text-xs text-secondary transition-colors hover:bg-muted hover:text-foreground"
                  >
                    <ExternalLink className="size-3.5" />
                    预览
                  </a>
                )}

                <Link
                  to="/knowledge-base?view=documents"
                  className="inline-flex h-8 items-center gap-1.5 rounded-md border border-border bg-card px-3 text-xs text-secondary transition-colors hover:bg-muted hover:text-foreground"
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
                    variant="danger"
                    className="h-8 rounded-md px-3.5 text-sm font-medium disabled:opacity-50"
                  >
                    {submitting ? '提交中…' : '重新处理'}
                  </Button>
                )}
              </div>
            </div>

            {/* 信息条 */}
            <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-border pt-3 text-xs text-secondary">
              <span>
                <b className="font-medium text-tertiary">文档类型</b> {document.fileType || '—'}
              </span>
              <span className="h-3 w-px bg-border" />
              <span>
                <b className="font-medium text-tertiary">文件大小</b> {formatFileSize(document.fileSize)}
              </span>
              <span className="h-3 w-px bg-border" />
              <span>
                <b className="font-medium text-tertiary">处理状态</b>{' '}
                {currentStatus ? <DocumentStatusBadge status={currentStatus} /> : <span className="text-tertiary">状态未知</span>}
              </span>
              <span className="h-3 w-px bg-border" />
              <span>
                <b className="font-medium text-tertiary">文本分块</b> {chunks.total} 个
              </span>
            </div>

            {/* 处理中：头部卡片内细进度条，完成后消失 */}
            {isProcessing && (
              <div className="mt-3">
                <div className="flex items-center justify-between text-[11px] text-secondary">
                  <span className="flex items-center gap-1.5">
                    <RefreshCw className="size-3 animate-spin text-primary" />
                    {pipelineStageLabel(currentStatus)}
                  </span>
                  {processStatus?.consumedTimes !== null && processStatus?.consumedTimes !== undefined && (
                    <span className="flex items-center gap-1 font-mono text-tertiary">
                      <Clock className="size-3" />
                      已耗时 {processStatus.consumedTimes} 秒
                    </span>
                  )}
                </div>
                <div className="mt-1.5 flex gap-1">
                  {[0, 1, 2].map((index) => (
                    <span
                      key={index}
                      className={cn(
                        'h-1 flex-1 rounded-full transition-colors',
                        pipelineStageIndex(currentStatus) > index
                          ? 'bg-success'
                          : pipelineStageIndex(currentStatus) === index
                            ? 'bg-primary'
                            : 'bg-muted'
                      )}
                    />
                  ))}
                </div>
              </div>
            )}
          </header>

          {/* Tab 导航 */}
          <Tabs
            items={[
              { value: 'overview', label: '文档概览' },
              { value: 'chunks', label: '文本分块' },
            ]}
            value={activeView}
            onChange={(value) => setActiveView(value as DocumentView)}
          />

          {/* 异常诊断提示：仅失败或请求异常时展示 */}
          {(currentStatus === 'FAILED' || processError) && (
            <section className="rounded-md border border-border bg-card p-4">
              <div className="flex items-start gap-3">
                <div className="rounded-md bg-primary-light p-2 text-primary">
                  <Sparkles className="size-4" />
                </div>
                <div className="space-y-1.5 text-xs">
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

          {/* 文档概览 */}
          {activeView === 'overview' && (
            <section className="rounded-md border border-border bg-card p-5">
              {document.description ? (
                <div className="flex items-start gap-2 text-xs text-secondary">
                  <Info className="mt-0.5 size-3.5 shrink-0 text-tertiary" />
                  <div className="min-w-0">
                    <span className="mr-2 font-medium text-tertiary">描述</span>
                    <span className="whitespace-pre-wrap text-secondary">{document.description}</span>
                  </div>
                </div>
              ) : (
                <p className="text-xs text-tertiary">暂无概览信息，可在文档处理完成后查看文本分块。</p>
              )}
            </section>
          )}

          {/* 文本分块工作区 或 未就绪空状态 */}
          {activeView === 'chunks' && currentStatus !== 'INDEXED' && (
            <section className="flex flex-col items-center justify-center rounded-md border border-dashed border-border bg-card px-6 py-16 text-center">
              <div className="rounded-md bg-primary-light p-4 text-primary">
                <FileText className="size-8" />
              </div>
              <h2 className="mt-4 text-base font-semibold text-foreground">分块尚未就绪</h2>
              <p className="mt-1 max-w-sm text-xs text-secondary">文档解析与向量索引完成后，可在此查看并在线编辑调整文本分块。</p>
            </section>
          )}

          {activeView === 'chunks' && currentStatus === 'INDEXED' && (
            <DocumentChunkBrowser
              chunks={chunks.records}
              total={chunks.total}
              sourceFileName={document.originalFileName || document.title || '未命名文档'}
              fileDescription={document.description}
              splitStrategyLabel={splitStrategyLabel}
              loading={chunksLoading && chunks.records.length === 0}
              loadingMore={chunksLoading && chunks.records.length > 0 && chunkPage > 1}
              hasMore={hasMore}
              error={chunksError}
              selectedChunk={selectedChunk}
              onSelect={setSelectedChunk}
              onClose={() => setSelectedChunk(null)}
              onRetry={() => { if (documentId !== null) void loadChunks(documentId, chunkPage, chunkPage > 1) }}
              onRefresh={handleRefresh}
              onSave={handleChunkSave}
              onDelete={handleChunkDelete}
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
      <div className="max-w-md rounded-md border border-border bg-card p-8 text-center">
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
    <div className="mb-4 flex w-full flex-wrap items-center justify-between gap-3 rounded-md border border-danger-light bg-danger-light px-4 py-3 text-xs text-danger">
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

/** 处理流水线阶段序号：解析 0 / 切分 1 / 索引 2。 */
function pipelineStageIndex(status: DocumentStatus): number {
  if (status === 'CHUNKING' || status === 'CHUNKED') return 1
  if (status === 'INDEXING') return 2
  return 0
}

/** 处理流水线当前阶段文案。 */
function pipelineStageLabel(status: DocumentStatus): string {
  if (status === 'QUEUED') return '排队中，等待进入处理流水线…'
  if (status === 'PARSING' || status === 'PARSED') return '正在解析文档…'
  if (status === 'CHUNKING' || status === 'CHUNKED') return '正在切分文本…'
  return '正在写入向量索引…'
}

/** 从文档处理配置快照中解析切分策略展示名。 */
function parseSplitStrategyLabel(processConfigJson: string | null): string | null {
  if (!processConfigJson) return null
  try {
    const config = JSON.parse(processConfigJson) as { splitConfig?: { splitStrategy?: string } }
    const strategy = config?.splitConfig?.splitStrategy
    if (!strategy) return null
    return {
      PARENT_MARKDOWN: '父子 Markdown',
      BROTHER_MARKDOWN: '同级 Markdown',
      REGEX_TEXT: '正则文本',
      EXCEL: '表格',
    }[strategy] ?? null
  } catch {
    return null
  }
}
