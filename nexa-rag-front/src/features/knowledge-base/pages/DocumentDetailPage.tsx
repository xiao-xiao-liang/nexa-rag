import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { ArrowLeft, Check, ChevronRight, Clock, Download, ExternalLink, FileText, Pencil, RefreshCw, Sparkles, Trash2, X } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Tabs } from '@/components/ui/tabs'
import { cn } from '@/lib/utils'
import type { PageVO } from '@/shared/api/types'
import { DocumentStatusBadge } from '../components/DocumentStatusBadge'
import { FileTypeIcon } from '../components/FileTypeIcon'
import { getDocument, getDocumentChunks, getDocumentOverview, getDocumentProcessStatus, processDocument, retryDocument, type DocumentChunk, type DocumentDetail, type DocumentOverview, type DocumentProcessStatus } from '../api/document-api'
import { isProcessingStatus, type DocumentStatus } from '../document-status'
import { useDocumentStatusPolling } from '../hooks/useDocumentStatusPolling'
import { ChunkStatusText, ChunkText, DocumentChunkBrowser, type ChunkViewMode } from '../components/DocumentChunkBrowser'

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
  const [overview, setOverview] = useState<DocumentOverview | null>(null)
  const [overviewLoading, setOverviewLoading] = useState(true)
  const [overviewError, setOverviewError] = useState<string | null>(null)
  const [chunks, setChunks] = useState<PageVO<DocumentChunk>>(EMPTY_CHUNKS)
  const [chunkPage, setChunkPage] = useState(1)
  const [chunksLoading, setChunksLoading] = useState(false)
  const [chunksError, setChunksError] = useState<string | null>(null)
  const [selectedChunk, setSelectedChunk] = useState<DocumentChunk | null>(null)
  const [drawerChunk, setDrawerChunk] = useState<DocumentChunk | null>(null)
  const [viewMode, setViewMode] = useState<ChunkViewMode>('preview')
  const [deleteTarget, setDeleteTarget] = useState<DocumentChunk | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [activeView, setActiveView] = useState<DocumentView>('chunks')
  const detailControllerRef = useRef<AbortController | null>(null)
  const processControllerRef = useRef<AbortController | null>(null)
  const overviewControllerRef = useRef<AbortController | null>(null)
  const chunksControllerRef = useRef<AbortController | null>(null)
  const scrollRef = useRef<HTMLDivElement | null>(null)
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

  const loadOverview = useCallback(async (targetDocumentId: string | number) => {
    overviewControllerRef.current?.abort()
    const controller = new AbortController()
    overviewControllerRef.current = controller
    setOverviewLoading(true)
    setOverviewError(null)
    try {
      const response = await getDocumentOverview(targetDocumentId, controller.signal)
      if (!controller.signal.aborted) setOverview(response)
    } catch (error) {
      if ((error as { name?: string }).name !== 'AbortError') setOverviewError(error instanceof Error ? error.message : '文档概览加载失败，请稍后重试')
    } finally {
      if (!controller.signal.aborted) setOverviewLoading(false)
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
    void loadOverview(documentId)
    return () => { detailControllerRef.current?.abort(); processControllerRef.current?.abort(); overviewControllerRef.current?.abort(); chunksControllerRef.current?.abort() }
  }, [documentId, loadDetail, loadOverview, loadProcessStatus])

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
    setDrawerChunk(updatedChunk)
  }

  const handleChunkDelete = (chunk: DocumentChunk) => {
    setChunks((current) => ({
      ...current,
      total: Math.max(0, current.total - 1),
      records: current.records.filter((item) => item.chunkId !== chunk.chunkId),
    }))
    setSelectedChunk(null)
  }

  const handleSelectChunk = (chunk: DocumentChunk) => {
    setDrawerChunk(chunk)
    setSelectedChunk(chunk)
  }

  const handleDeleteConfirm = () => {
    if (!deleteTarget) return
    handleChunkDelete(deleteTarget)
    setDeleteTarget(null)
  }

  if (documentId === null) return <InvalidDocumentAddress />

  const isProcessing = currentStatus !== null && isProcessingStatus(currentStatus)

  return (
    <div className="flex h-full min-w-0 bg-background">
      {/* 左侧内容区（独立滚动） */}
      <div ref={scrollRef} data-testid="document-detail-scroll" className="no-scrollbar min-w-0 flex-1 overflow-y-auto">
        <div className="px-4 py-5 sm:px-6 lg:px-10">
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
                      消息消费 {processStatus.consumedTimes} 次
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

          {/* 文档概览（诊断视图） */}
          {activeView === 'overview' && (
            <DocumentOverviewView
              document={document}
              overview={overview}
              loading={overviewLoading}
              error={overviewError}
              processStatus={processStatus}
              onRetry={() => { if (documentId !== null) void loadOverview(documentId) }}
            />
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
              loading={chunksLoading && chunks.records.length === 0}
              loadingMore={chunksLoading && chunks.records.length > 0 && chunkPage > 1}
              hasMore={hasMore}
              error={chunksError}
              selectedChunk={selectedChunk}
              viewMode={viewMode}
              onViewModeChange={setViewMode}
              onSelect={handleSelectChunk}
              onRetry={() => { if (documentId !== null) void loadChunks(documentId, chunkPage, chunkPage > 1) }}
              onRefresh={handleRefresh}
            />
          )}
        </div>
      )}
      </div>
      </div>

      {/* 右侧整页详情抽屉（打开时滑入，关闭时收起） */}
      <aside
        aria-label="知识块详情抽屉"
        aria-hidden={selectedChunk === null}
        className={cn(
          'h-full shrink-0 overflow-hidden border-l border-border bg-card transition-[width] duration-300 ease-out',
          selectedChunk !== null ? 'w-[400px]' : 'w-0 invisible'
        )}
      >
        <div className="h-full w-[400px]">
          {drawerChunk && (
            <ChunkDrawer
              chunk={drawerChunk}
              sourceFileName={document?.originalFileName || document?.title || '未命名文档'}
              viewMode={viewMode}
              onClose={() => setSelectedChunk(null)}
              onSave={handleChunkSave}
              onDelete={() => setDeleteTarget(drawerChunk)}
            />
          )}
        </div>
      </aside>

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
    </div>
  )
}

/** 文档诊断概览视图：处理结果 KPI + 处理配置分组 + 文档信息。 */
function DocumentOverviewView({
  document,
  overview,
  loading,
  error,
  processStatus,
  onRetry,
}: {
  document: DocumentDetail
  overview: DocumentOverview | null
  loading: boolean
  error: string | null
  processStatus: DocumentProcessStatus | null
  onRetry: () => void
}) {
  const [configOpen, setConfigOpen] = useState(false)

  if (loading && !overview) {
    return (
      <div className="flex flex-col items-center justify-center rounded-md border border-dashed border-border bg-card px-6 py-16 text-center text-tertiary">
        <RefreshCw className="mb-2 size-5 animate-spin" />
        <span className="text-xs font-medium">正在加载文档概览…</span>
      </div>
    )
  }

  if (error && !overview) {
    return (
      <section className="rounded-md border border-danger-light bg-danger-light p-4 text-center text-xs text-danger">
        <p>{error}</p>
        <Button className="mt-3" type="button" variant="outline" size="sm" onClick={onRetry}>
          重新加载
        </Button>
      </section>
    )
  }

  const config = parseProcessConfig(overview?.processConfigJson ?? document.processConfigJson)
  const stats = overview?.chunkStatistics
  const configJson = overview?.processConfigJson ?? document.processConfigJson
  const consumedTimes = processStatus?.consumedTimes ?? 0

  return (
    <div className="space-y-3">
      {/* 处理结果 KPI（全宽） */}
      <section className="rounded-md border border-border bg-card p-4">
        <h3 className="mb-3 text-xs font-semibold text-secondary">处理结果</h3>
        <div className="grid grid-cols-3 gap-y-5 sm:grid-cols-6">
          <KpiItem label="总分块" value={stats?.total ?? 0} valueClassName="text-foreground" />
          <KpiItem label="已索引" value={stats?.indexed ?? 0} valueClassName="text-success" />
          <KpiItem label="待索引" value={stats?.pending ?? 0} valueClassName="text-primary/70" />
          <KpiItem label="跳过索引" value={stats?.skipped ?? 0} valueClassName="text-tertiary" />
          <KpiItem
            label="索引失败"
            value={stats?.failed ?? 0}
            valueClassName={(stats?.failed ?? 0) > 0 ? 'text-danger' : 'text-tertiary'}
          />
          <KpiItem label="消费次数" value={consumedTimes} valueClassName="text-foreground" />
        </div>
        {stats && stats.total > 0 && (
          <div className="mt-4 flex h-1.5 w-full overflow-hidden rounded-full bg-muted">
            <span className="bg-success" style={{ width: `${(stats.indexed / stats.total) * 100}%` }} />
            <span className="bg-border" style={{ width: `${(stats.skipped / stats.total) * 100}%` }} />
            {stats.failed > 0 && <span className="bg-danger" style={{ width: `${(stats.failed / stats.total) * 100}%` }} />}
          </div>
        )}
      </section>

      {/* 处理配置（2/3） + 文档信息（1/3） */}
      <div className="grid grid-cols-1 items-start gap-3 xl:grid-cols-3">
        {/* 处理配置 */}
        <section className="rounded-md border border-border bg-card p-4 xl:col-span-2">
          <h3 className="mb-3 text-xs font-semibold text-secondary">处理配置</h3>
          {config ? (
            <div className="divide-y divide-border/60">
              {/* 切分 */}
              <div className="py-3 first:pt-0 last:pb-0">
                <div className="mb-1.5 text-[11px] text-tertiary">切分</div>
                <div className="text-sm font-semibold text-foreground">{config.splitStrategy ?? '—'}</div>
                <div className="mt-1 text-xs text-secondary">{splitSummary(config)}</div>
              </div>
              {/* 内容处理 */}
              <div className="py-3 first:pt-0 last:pb-0">
                <div className="mb-2 text-[11px] text-tertiary">内容处理</div>
                <div className="flex flex-wrap gap-x-5 gap-y-2">
                  <FlagItem label="保护代码块" on={config.preserveCodeBlock} />
                  <FlagItem label="移除标题行" on={config.stripHeaders} />
                  <FlagItem label="超长创建父片段" on={config.createParentForOversized} />
                  <FlagItem label="OCR" on={config.enableOcr} />
                  <FlagItem label="图片描述" on={config.enableImageDescription} />
                </div>
              </div>
              {/* 索引 */}
              <div className="py-3 first:pt-0 last:pb-0">
                <div className="mb-2 text-[11px] text-tertiary">索引</div>
                <div className="flex flex-wrap gap-x-5 gap-y-2">
                  <FlagItem label="向量索引" on={config.vectorEnabled} />
                  <FlagItem label="关键词索引" on={config.keywordEnabled} />
                </div>
              </div>
            </div>
          ) : (
            <p className="text-xs text-tertiary">暂无处理配置快照。</p>
          )}
          <div className="mt-3 flex justify-end">
            <button
              type="button"
              disabled={!configJson}
              onClick={() => setConfigOpen(true)}
              className="text-xs text-primary/80 transition-colors hover:text-primary disabled:cursor-not-allowed disabled:opacity-50"
            >
              查看完整配置
            </button>
          </div>
        </section>

        {/* 文档信息 */}
        <section className="rounded-md border border-border bg-card p-4">
          <h3 className="mb-3 text-xs font-semibold text-secondary">文档信息</h3>
          <div className="space-y-2.5 text-xs">
            <InfoLine label="来源" value={sourceTypeLabel(overview?.sourceType)} />
            <InfoLine label="创建" value={formatDateTime(overview?.createTime)} />
            <InfoLine label="更新" value={formatDateTime(overview?.updateTime)} />
            {document.description && <InfoLine label="描述" value={document.description} />}
            {overview?.sourceUrl && <InfoLine label="链接" value={overview.sourceUrl} link />}
          </div>
        </section>
      </div>

      {/* 完整处理配置弹窗 */}
      <ConfigDialog open={configOpen} onOpenChange={setConfigOpen} json={configJson} />
    </div>
  )
}

/** 处理结果 KPI 单项：数字大号、标签小灰字。 */
function KpiItem({ label, value, valueClassName }: { label: string; value: number; valueClassName?: string }) {
  return (
    <div className="min-w-0">
      <div className={cn('text-2xl font-semibold leading-none', valueClassName)}>{value}</div>
      <div className="mt-1.5 text-[11px] text-tertiary">{label}</div>
    </div>
  )
}

/** 布尔配置状态项：开启为绿色 ✓，关闭为弱灰 —。 */
function FlagItem({ label, on }: { label: string; on: boolean | null }) {
  const enabled = on === true
  return (
    <span className={cn('inline-flex items-center gap-1 text-xs', enabled ? 'text-secondary' : 'text-tertiary')}>
      <span className={cn('font-semibold', enabled ? 'text-success' : 'text-tertiary')}>{enabled ? '✓' : '—'}</span>
      {label}
    </span>
  )
}

/** 切分配置一行摘要。 */
function splitSummary(config: ParsedProcessConfig): string {
  const parts: string[] = []
  if (config.chunkSize !== null) parts.push(`${config.chunkSize} 字符`)
  if (config.chunkOverlap !== null) parts.push(`重叠 ${config.chunkOverlap}`)
  if (config.titleLevel !== null) parts.push(`H${config.titleLevel}`)
  return parts.length > 0 ? parts.join(' · ') : '—'
}

/** 文档信息行。 */
function InfoLine({ label, value, link }: { label: string; value: string; link?: boolean }) {
  return (
    <div className="flex items-baseline gap-3">
      <span className="w-9 shrink-0 text-tertiary">{label}</span>
      {link ? (
        <a href={value} target="_blank" rel="noreferrer" className="min-w-0 break-all text-primary hover:underline">
          {value}
        </a>
      ) : (
        <span className="min-w-0 break-all text-foreground">{value}</span>
      )}
    </div>
  )
}

/** 完整处理配置查看弹窗。 */
function ConfigDialog({ open, onOpenChange, json }: { open: boolean; onOpenChange: (open: boolean) => void; json: string | null }) {
  const [copied, setCopied] = useState(false)
  const pretty = useMemo(() => {
    if (!json) return ''
    try {
      return JSON.stringify(JSON.parse(json), null, 2)
    } catch {
      return json
    }
  }, [json])

  const handleCopy = () => {
    if (!pretty) return
    void navigator.clipboard?.writeText(pretty)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex max-h-[calc(100dvh-4rem)] max-w-2xl flex-col overflow-hidden rounded-md bg-card p-0">
        <DialogHeader className="border-b border-border px-5 py-3.5">
          <DialogTitle className="text-sm font-semibold text-foreground">完整处理配置</DialogTitle>
          <DialogDescription className="text-xs text-secondary">处理配置快照原始 JSON，用于排查切分与索引参数。</DialogDescription>
        </DialogHeader>
        <div className="min-h-0 flex-1 overflow-hidden p-4">
          <ScrollArea className="h-[360px] rounded-md border border-border bg-muted/40 p-3" hideScrollbar>
            <pre className="whitespace-pre-wrap break-all font-mono text-xs leading-relaxed text-secondary">{pretty}</pre>
          </ScrollArea>
        </div>
        <div className="flex items-center justify-between border-t border-border px-5 py-3">
          {copied ? <span className="text-xs font-medium text-success">已复制</span> : <span />}
          <div className="flex items-center gap-2">
            <Button type="button" variant="outline" size="sm" onClick={handleCopy} disabled={!pretty}>
              复制
            </Button>
            <Button type="button" size="sm" onClick={() => onOpenChange(false)}>
              关闭
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}

interface ParsedProcessConfig {
  splitStrategy: string | null
  chunkSize: number | null
  chunkOverlap: number | null
  titleLevel: number | null
  stripHeaders: boolean | null
  preserveCodeBlock: boolean | null
  createParentForOversized: boolean | null
  enableOcr: boolean | null
  enableImageDescription: boolean | null
  vectorEnabled: boolean | null
  keywordEnabled: boolean | null
}

/** 解析文档处理配置快照为概览展示结构，解析失败返回 null。 */
function parseProcessConfig(json: string | null): ParsedProcessConfig | null {
  if (!json) return null
  try {
    const config = JSON.parse(json) as {
      splitConfig?: { splitStrategy?: string; chunkSize?: number; chunkOverlap?: number; markdown?: Record<string, unknown> }
      parseConfig?: Record<string, unknown>
      indexConfig?: Record<string, unknown>
    }
    const split = config?.splitConfig ?? {}
    const markdown = split.markdown ?? {}
    const parse = config?.parseConfig ?? {}
    const index = config?.indexConfig ?? {}
    return {
      splitStrategy: splitStrategyLabel(split.splitStrategy),
      chunkSize: split.chunkSize ?? null,
      chunkOverlap: split.chunkOverlap ?? null,
      titleLevel: typeof markdown.titleLevel === 'number' ? markdown.titleLevel : null,
      stripHeaders: typeof markdown.stripHeaders === 'boolean' ? markdown.stripHeaders : null,
      preserveCodeBlock: typeof markdown.preserveCodeBlock === 'boolean' ? markdown.preserveCodeBlock : null,
      createParentForOversized: typeof markdown.createParentForOversized === 'boolean' ? markdown.createParentForOversized : null,
      enableOcr: typeof parse.enableOcr === 'boolean' ? parse.enableOcr : null,
      enableImageDescription: typeof parse.enableImageDescription === 'boolean' ? parse.enableImageDescription : null,
      vectorEnabled: typeof index.vectorEnabled === 'boolean' ? index.vectorEnabled : null,
      keywordEnabled: typeof index.keywordEnabled === 'boolean' ? index.keywordEnabled : null,
    }
  } catch {
    return null
  }
}

/** 切分策略枚举转展示名。 */
function splitStrategyLabel(strategy: string | undefined): string | null {
  if (!strategy) return null
  return {
    PARENT_MARKDOWN: '父子 Markdown',
    BROTHER_MARKDOWN: '同级 Markdown',
    REGEX_TEXT: '正则文本',
    EXCEL: '表格',
  }[strategy] ?? strategy
}

/** 文档来源类型转展示名。 */
function sourceTypeLabel(value: string | null | undefined): string {
  if (value === 'LOCAL') return '本地文件'
  if (value === 'FEISHU') return '飞书文档'
  if (value === 'YUQUE') return '语雀文档'
  return value || '—'
}

/** 时间字符串转「YYYY-MM-DD HH:mm」展示。 */
function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 右侧整页知识块详情抽屉：内容预览即编辑区，底部统一操作栏。 */
function ChunkDrawer({
  chunk,
  sourceFileName,
  viewMode,
  onClose,
  onSave,
  onDelete,
}: {
  chunk: DocumentChunk
  sourceFileName: string
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
  }, [chunk.chunkId])

  const handleSaveClick = () => {
    onSave(chunk, draft)
    setEditing(false)
    setSavedSuccess(true)
    setTimeout(() => setSavedSuccess(false), 2000)
  }

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden px-4">
      {/* 抽屉头部：分块序号 + 状态 + 来源弱信息 */}
      <header className="flex shrink-0 items-start justify-between gap-3 border-b border-border py-3.5">
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

      {/* 中间正文区域：标题固定，内容框占满剩余高度，仅框内正文可滚动 */}
      <section aria-label="分块完整内容" className="flex min-h-0 flex-1 flex-col overflow-hidden py-3.5">
        <div className="mb-1.5 shrink-0 text-xs font-medium text-secondary">内容</div>
        {/* 内容框：预览态与编辑态复用同一位置与尺寸，滚动只发生在框内 */}
        <div className={cn(
          'flex min-h-0 flex-1 flex-col overflow-hidden rounded-md border border-border bg-muted/60 p-3'
        )}>
          {editing ? (
            <textarea
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              aria-label="编辑分块内容"
              className="h-full w-full resize-none overflow-y-auto rounded-md border-none bg-transparent p-0 text-xs leading-relaxed text-foreground outline-none"
            />
          ) : (
            <ScrollArea className="h-full min-h-0" hideScrollbar>
              <ChunkText text={chunk.text} viewMode={viewMode} />
            </ScrollArea>
          )}
        </div>
      </section>

      {/* 抽屉底部操作 */}
      <footer className="flex shrink-0 items-center justify-between border-t border-border py-3">
        {savedSuccess ? (
          <span className="inline-flex items-center gap-1 text-xs font-medium text-success">
            <Check className="size-3.5" />
            已保存
          </span>
        ) : (
          <span />
        )}
        <div className="flex items-center gap-2">
          {editing ? (
            <>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => {
                  setDraft(chunk.text)
                  setEditing(false)
                }}
              >
                取消
              </Button>
              <Button type="button" size="sm" onClick={handleSaveClick}>
                保存修改
              </Button>
            </>
          ) : (
            <>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => {
                  setDraft(chunk.text)
                  setEditing(true)
                }}
              >
                <Pencil className="size-3.5" />
                编辑
              </Button>
              <Button type="button" variant="outline" size="sm" onClick={onDelete} className="border-danger-light text-danger hover:bg-danger-light hover:text-danger">
                <Trash2 className="size-3.5" />
                删除
              </Button>
            </>
          )}
        </div>
      </footer>
    </div>
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
