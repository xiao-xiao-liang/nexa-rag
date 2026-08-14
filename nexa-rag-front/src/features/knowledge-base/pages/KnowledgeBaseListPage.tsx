import { useCallback, useEffect, useRef, useState } from 'react'
import { ArrowRight, BookOpen, CircleAlert, FileUp, RefreshCw, Search, Sparkles, X } from 'lucide-react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/ui/empty-state'
import { Tabs } from '@/components/ui/tabs'
import type { PageVO } from '@/shared/api/types'
import { isProcessingStatus } from '../document-status'
import { DocumentListTable } from '../components/DocumentListTable'
import { DocumentStatusBadge } from '../components/DocumentStatusBadge'
import { FileTypeIcon } from '../components/FileTypeIcon'
import { UploadDocumentDialog } from '../components/UploadDocumentDialog'
import { deleteDocument, listDocuments, type DocumentSummary } from '../api/document-api'

const PAGE_SIZE = 20
const EMPTY_PAGE: PageVO<DocumentSummary> = { records: [], total: 0, current: 1, size: PAGE_SIZE, pages: 0 }

export type StatusFilterType = 'ALL' | 'INDEXED' | 'PROCESSING' | 'FAILED'

/** 知识库主页面，承载概览和完整文档列表两个原型视图。 */
export function KnowledgeBaseListPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const pageNum = parsePageNum(searchParams.get('page'))
  const view = searchParams.get('view') === 'overview' ? 'overview' : 'documents'
  const [page, setPage] = useState<PageVO<DocumentSummary>>(EMPTY_PAGE)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [uploadOpen, setUploadOpen] = useState(false)
  const [aiPrompt, setAiPrompt] = useState('')
  const [deleteTarget, setDeleteTarget] = useState<DocumentSummary | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [query, setQuery] = useState('')
  const statusParam = searchParams.get('status')
  const [statusFilter, setStatusFilter] = useState<StatusFilterType>(
    statusParam === 'PROCESSING' || statusParam === 'FAILED' ? statusParam : 'ALL',
  )
  const controllerRef = useRef<AbortController | null>(null)
  const requestIdRef = useRef(0)

  const loadPage = useCallback(async (targetPage: number) => {
    controllerRef.current?.abort()
    const controller = new AbortController()
    controllerRef.current = controller
    const requestId = ++requestIdRef.current
    setLoading(true)
    setError(null)
    try {
      const response = await listDocuments(targetPage, PAGE_SIZE, controller.signal)
      if (requestId === requestIdRef.current) setPage(response)
    } catch (loadError) {
      if (requestId === requestIdRef.current && (loadError as { name?: string }).name !== 'AbortError') {
        setError(loadError instanceof Error ? loadError.message : '文档列表加载失败，请稍后重试')
      }
    } finally {
      if (requestId === requestIdRef.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadPage(pageNum)
    return () => controllerRef.current?.abort()
  }, [loadPage, pageNum])

  useEffect(() => {
    if (searchParams.get('upload') === '1') {
      setUploadOpen(true)
    }
  }, [searchParams])

  const updateLocation = (nextView: 'overview' | 'documents', nextPage = 1) => {
    const nextParams = new URLSearchParams()
    nextParams.set('view', nextView)
    if (nextPage > 1) nextParams.set('page', String(nextPage))
    setSearchParams(nextParams)
  }

  const handleDelete = async (document: DocumentSummary) => {
    if (deleting) return
    setDeleting(true)
    setError(null)
    try {
      await deleteDocument(document.documentId)
      setDeleteTarget(null)
      const nextPage = page.records.length === 1 && pageNum > 1 ? pageNum - 1 : pageNum
      if (nextPage === pageNum) await loadPage(nextPage)
      else updateLocation('documents', nextPage)
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : '删除文档失败，请稍后重试')
    } finally {
      setDeleting(false)
    }
  }

  const handleBatchDelete = async (documents: DocumentSummary[]) => {
    if (deleting || documents.length === 0) return
    setDeleting(true)
    setError(null)
    try {
      await Promise.all(documents.map((document) => deleteDocument(document.documentId)))
      const nextPage = page.records.length === documents.length && pageNum > 1 ? pageNum - 1 : pageNum
      if (nextPage === pageNum) await loadPage(nextPage)
      else updateLocation('documents', nextPage)
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : '批量删除文档失败，请稍后重试')
    } finally {
      setDeleting(false)
    }
  }

  const processingDocuments = page.records.filter((document) => isProcessingStatus(document.status))
  const indexedCount = page.records.filter((document) => document.status === 'INDEXED').length
  const failedCount = page.records.filter((document) => document.status === 'FAILED').length
  const openDocument = (documentId: number | string) => navigate(`/knowledge-base/${documentId}`)

  return (
    <section className="min-h-full min-w-0 overflow-y-auto bg-background px-4 py-5 sm:px-6 lg:px-8">
      <div className="w-full">
        {view === 'overview' ? (
          <KnowledgeOverview
            total={page.total}
            indexedCount={indexedCount}
            processingDocuments={processingDocuments}
            failedCount={failedCount}
            documents={page.records.slice(0, 3)}
            loading={loading}
            error={error}
            onRetry={() => void loadPage(pageNum)}
            onUpload={() => setUploadOpen(true)}
            onShowAll={() => updateLocation('documents')}
            onView={openDocument}
          />
        ) : (
          <DocumentLibrary
            page={page}
            pageNum={pageNum}
            loading={loading}
            error={error}
            deleting={deleting}
            query={query}
            onQueryChange={setQuery}
            statusFilter={statusFilter}
            onStatusFilterChange={setStatusFilter}
            onRetry={() => void loadPage(pageNum)}
            onUpload={() => setUploadOpen(true)}
            onBack={() => updateLocation('overview')}
            onView={openDocument}
            onDelete={(document) => void handleDelete(document)}
            onBatchDelete={(documents) => void handleBatchDelete(documents)}
            onPrevious={() => updateLocation('documents', pageNum - 1)}
            onNext={() => updateLocation('documents', pageNum + 1)}
            deleteTarget={deleteTarget}
            onDeleteTargetChange={setDeleteTarget}
            aiPrompt={aiPrompt}
            onAiPromptChange={setAiPrompt}
            onAiSubmit={() => setUploadOpen(true)}
          />
        )}
      </div>
      <UploadDocumentDialog
        open={uploadOpen}
        onOpenChange={(open) => {
          setUploadOpen(open)
          if (!open) setAiPrompt('')
          if (!open && searchParams.get('upload') === '1') {
            const nextParams = new URLSearchParams(searchParams)
            nextParams.delete('upload')
            setSearchParams(nextParams, { replace: true })
          }
        }}
        initialDescription={aiPrompt}
        onUploaded={openDocument}
      />
    </section>
  )
}

/** 知识库概览页，按原型展示总览、最近文档和处理提醒。 */
function KnowledgeOverview({
  total,
  indexedCount,
  processingDocuments,
  failedCount,
  documents,
  loading,
  error,
  onRetry,
  onUpload,
  onShowAll,
  onView,
}: {
  total: number
  indexedCount: number
  processingDocuments: DocumentSummary[]
  failedCount: number
  documents: DocumentSummary[]
  loading: boolean
  error: string | null
  onRetry: () => void
  onUpload: () => void
  onShowAll: () => void
  onView: (documentId: number | string) => void
}) {
  return (
    <div className="space-y-6">
      {/* 头部 Header */}
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-foreground">知识库</h1>
          <p className="mt-1 text-xs text-tertiary">管理用于 RAG 智能问答与检索增强的知识文档及向量化索引状态</p>
        </div>
        <div className="flex items-center gap-3">
          <Button
            onClick={onUpload}
            className="h-8 rounded-md bg-primary px-3.5 text-sm font-medium text-primary-foreground hover:bg-primary/90"
          >
            <FileUp className="size-4" />
            上传文档
          </Button>
          <span className="h-5 w-px bg-border" aria-hidden="true" />
          <span className="flex size-6 items-center justify-center rounded-full bg-tertiary/30 text-[10px] font-semibold text-secondary" aria-label="当前用户">
            N
          </span>
        </div>
      </header>

      {error && <LoadError message={error} onRetry={onRetry} />}

      {/* 默认知识库 Hero Banner */}
      <section className="flex flex-col justify-between gap-6 rounded-lg border border-border bg-card p-5 lg:flex-row lg:items-center">
        <div className="flex items-start gap-4">
          <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-primary text-white">
            <BookOpen className="size-6" />
          </span>
          <div>
            <div className="flex items-center gap-3">
              <h2 className="text-base font-semibold text-foreground">默认知识库</h2>
              <span className="inline-flex items-center gap-1 rounded-full bg-success-light px-2.5 py-0.5 text-[10px] font-medium text-success">
                <span className="size-1.5 rounded-full bg-success" />
                运行正常
              </span>
            </div>
            <p className="mt-1.5 max-w-lg text-xs leading-relaxed text-secondary">
              上传的文档会在自动解析、智能切分和向量索引完成后，服务于 AI 检索增强问答。
            </p>
          </div>
        </div>

        {/* 4 项核心 Metric 统计 */}
        <div className="grid grid-cols-2 gap-x-8 gap-y-4 rounded-md border border-border bg-card p-4 sm:grid-cols-4">
          {[
            { label: '全部文档', value: total, color: 'text-foreground' },
            { label: '已索引', value: indexedCount, color: 'text-success' },
            { label: '处理中', value: processingDocuments.length, color: 'text-warning' },
            { label: '处理失败', value: failedCount, color: 'text-danger' },
          ].map((metric) => (
            <div key={metric.label} className="min-w-[70px]">
              <b className={`block text-xl font-bold tracking-tight ${metric.color}`}>
                {loading ? '—' : metric.value}
              </b>
              <span className="mt-1 block whitespace-nowrap text-[10px] font-medium text-tertiary">{metric.label}</span>
            </div>
          ))}
        </div>
      </section>

      {/* 最近文档板块 */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-foreground">最近文档</h2>
          <button
            type="button"
            className="flex items-center gap-1.5 text-xs font-medium text-primary transition-colors hover:text-primary/80"
            onClick={onShowAll}
          >
            查看全部文档
            <ArrowRight className="size-3.5" />
          </button>
        </div>

        <section className="overflow-hidden rounded-lg border border-border bg-card">
          <div className="grid grid-cols-[minmax(190px,2.2fr)_0.8fr_0.75fr] gap-3 bg-muted px-5 py-2.5 text-xs font-medium text-tertiary sm:grid-cols-[minmax(260px,2.2fr)_0.8fr_0.75fr]">
            <span>文档</span>
            <span>类型</span>
            <span>状态</span>
          </div>

          {loading && (
            <p className="px-5 py-12 text-center text-xs font-medium text-tertiary">正在加载知识库概览…</p>
          )}

          {!loading && documents.length === 0 && (
            <EmptyState title="暂无文档" description="上传文件后即可开始构建知识库。" className="py-10" />
          )}

          {!loading &&
            documents.map((document) => (
              <button
                key={document.documentId}
                type="button"
                onClick={() => onView(document.documentId)}
                className="grid w-full grid-cols-[minmax(190px,2.2fr)_0.8fr_0.75fr] items-center gap-3 border-t border-border px-5 py-3.5 text-left text-xs text-secondary transition-colors hover:bg-muted/60 sm:grid-cols-[minmax(260px,2.2fr)_0.8fr_0.75fr]"
              >
                <DocumentCell document={document} />
                <span className="font-medium text-secondary">{document.fileType || '—'}</span>
                <DocumentStatusBadge status={document.status} />
              </button>
            ))}
        </section>
      </div>

      {/* 底部提醒与流水线 */}
      <section className="grid gap-4 lg:grid-cols-2">
        <article className="rounded-lg border border-border bg-card p-4 space-y-3">
          <h3 className="text-xs font-semibold text-foreground">正在处理</h3>
          {processingDocuments.length > 0 ? (
            <div>
              <p className="text-xs text-secondary">{documentName(processingDocuments[0])} · 文档处理流水线</p>
              <div className="mt-3 flex gap-2">
                <span className="flex-1 rounded bg-success-light py-2 text-center text-[10px] font-medium text-success">
                  解析
                </span>
                <span className="flex-1 rounded bg-success-light py-2 text-center text-[10px] font-medium text-success">
                  切分
                </span>
                <span className="flex-1 rounded bg-muted py-2 text-center text-[10px] font-medium text-tertiary">
                  索引
                </span>
              </div>
            </div>
          ) : (
            <p className="rounded bg-muted p-3 text-xs text-tertiary">当前没有处理中的文档</p>
          )}
        </article>

        <article className="rounded-lg border border-border bg-card p-4 space-y-3">
          <h3 className="text-xs font-semibold text-foreground">需要关注</h3>
          <div className="flex items-center gap-2.5 rounded bg-muted p-3 text-xs text-secondary">
            <CircleAlert className="size-4 text-warning shrink-0" />
            <span>{failedCount > 0 ? `${failedCount} 个文档处理失败，查看失败原因` : '当前没有处理失败的文档'}</span>
          </div>
        </article>
      </section>
    </div>
  )
}

/** 全部文档页，按原型提供搜索、状态 Tab 筛选、刷新、分页和失败提醒。 */
function DocumentLibrary({
  page,
  pageNum,
  loading,
  error,
  deleting,
  query,
  onQueryChange,
  statusFilter,
  onStatusFilterChange,
  onRetry,
  onUpload,
  onBack,
  onView,
  onDelete,
  onBatchDelete,
  onPrevious,
  onNext,
  deleteTarget,
  onDeleteTargetChange,
  aiPrompt,
  onAiPromptChange,
  onAiSubmit,
}: {
  page: PageVO<DocumentSummary>
  pageNum: number
  loading: boolean
  error: string | null
  deleting: boolean
  query: string
  onQueryChange: (value: string) => void
  statusFilter: StatusFilterType
  onStatusFilterChange: (filter: StatusFilterType) => void
  onRetry: () => void
  onUpload: () => void
  onBack: () => void
  onView: (documentId: number | string) => void
  onDelete: (document: DocumentSummary) => void
  onBatchDelete: (documents: DocumentSummary[]) => void
  onPrevious: () => void
  onNext: () => void
  deleteTarget: DocumentSummary | null
  onDeleteTargetChange: (document: DocumentSummary | null) => void
  aiPrompt: string
  onAiPromptChange: (value: string) => void
  onAiSubmit: () => void
}) {
  return (
    <div className="space-y-5">
      {/* 面包屑导航 */}
      <div className="flex items-center gap-2 text-xs text-tertiary">
        <button type="button" onClick={onBack} className="transition-colors hover:text-primary">
          知识库
        </button>
        <span className="text-tertiary">/</span>
        <span className="font-medium text-secondary">默认知识库</span>
      </div>

      {/* Header */}
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-foreground">全部文档</h1>
          <p className="mt-1 text-xs text-tertiary">共 {page.total} 个文档记录</p>
        </div>
        <div className="flex items-center gap-3">
          <Button
            onClick={onUpload}
            className="h-8 rounded-md bg-primary px-3.5 text-sm font-medium text-primary-foreground hover:bg-primary/90"
          >
            <FileUp className="size-4" />
            上传文档
          </Button>
          <span className="h-5 w-px bg-border" aria-hidden="true" />
          <span className="flex size-6 items-center justify-center rounded-full bg-tertiary/30 text-[10px] font-semibold text-secondary" aria-label="当前用户">
            N
          </span>
        </div>
      </header>

      {error && <LoadError message={error} onRetry={onRetry} />}

      {/* AI 创建入口 */}
      <div className="flex items-center gap-2.5 rounded-xl border border-border bg-card px-4 py-2.5 transition-all focus-within:border-primary focus-within:shadow-[0_0_0_3px_rgba(51,112,255,0.12)]">
        <Sparkles className="size-4 shrink-0 text-primary" />
        <input
          aria-label="AI 创建知识库"
          value={aiPrompt}
          onChange={(event) => onAiPromptChange(event.target.value)}
          onKeyDown={(event) => { if (event.key === 'Enter') onAiSubmit() }}
          placeholder="描述需求，AI 帮你整理知识库…"
          className="min-w-0 flex-1 bg-transparent text-sm text-foreground outline-none placeholder:text-tertiary"
        />
        <button
          type="button"
          aria-label="创建"
          disabled={!aiPrompt.trim()}
          onClick={onAiSubmit}
          className="flex size-7 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground transition-colors hover:bg-primary/90 disabled:bg-muted disabled:text-tertiary"
        >
          <ArrowRight className="size-3.5" />
        </button>
      </div>

      {/* 状态分类 Quick Filter Tabs */}
      <Tabs
        items={[
          { value: 'ALL', label: '全部状态' },
          { value: 'INDEXED', label: '已索引' },
          { value: 'PROCESSING', label: '处理中' },
          { value: 'FAILED', label: '处理失败' },
        ]}
        value={statusFilter}
        onChange={(value) => onStatusFilterChange(value as StatusFilterType)}
      />

      {/* 工具栏搜索与过滤 */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="relative w-full max-w-sm">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-3.5 -translate-y-1/2 text-tertiary" />
          <input
            value={query}
            onChange={(event) => onQueryChange(event.target.value)}
            placeholder="搜索当前页文档"
            className="h-8 w-full rounded-md border border-border bg-card pl-9 pr-8 text-xs text-foreground placeholder:text-tertiary outline-none transition-all focus:border-primary focus:ring-2 focus:ring-primary/30"
          />
          {query && (
            <button
              type="button"
              onClick={() => onQueryChange('')}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-tertiary hover:text-secondary"
            >
              <X className="size-3.5" />
            </button>
          )}
        </div>

        <div className="flex items-center gap-2">
          <span className="h-7 rounded border border-border bg-card px-3 text-xs leading-7 text-secondary">
            20 条 / 页
          </span>
          <button
            type="button"
            aria-label="刷新文档列表"
            onClick={onRetry}
            className="inline-flex h-7 items-center gap-1.5 rounded border border-border bg-card px-3 text-xs text-secondary transition-colors hover:bg-muted"
          >
            <RefreshCw className="size-3.5" />
            刷新
          </button>
        </div>
      </div>

      {/* 文档列表表格 */}
      <DocumentListTable
        page={page}
        pageNum={pageNum}
        loading={loading}
        deleting={deleting}
        query={query}
        statusFilter={statusFilter}
        onView={onView}
        onDelete={onDelete}
        onBatchDelete={onBatchDelete}
        onRetryItem={onRetry}
        onPrevious={onPrevious}
        onNext={onNext}
        deleteTarget={deleteTarget}
        onDeleteTargetChange={onDeleteTargetChange}
      />

      {/* 提示 Alert Banner */}
      <div className="flex items-center gap-2.5 rounded-md border border-warning-light bg-warning-light p-3 text-xs text-warning">
        <Sparkles className="size-4 shrink-0" />
        <span>处理失败的文档可在表格中直接重试，或点击进入详情页查看失败阶段与具体原因。</span>
      </div>
    </div>
  )
}

/** 文档名与文件类型的小型展示单元。 */
function DocumentCell({ document }: { document: DocumentSummary }) {
  return (
    <span className="flex min-w-0 items-center gap-3">
      <FileTypeIcon fileName={document.originalFileName} fileType={document.fileType} />
      <span className="min-w-0 truncate text-foreground">{documentName(document)}</span>
    </span>
  )
}

/** 数据请求失败提示。 */
function LoadError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-danger-light bg-danger-light px-4 py-3 text-xs text-danger">
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

function documentName(document: DocumentSummary): string {
  return document.title || document.originalFileName || '未命名文档'
}
function parsePageNum(value: string | null): number {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 1
}
