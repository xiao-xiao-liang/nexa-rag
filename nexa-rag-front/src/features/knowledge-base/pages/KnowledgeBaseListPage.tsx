import { useCallback, useEffect, useRef, useState } from 'react'
import { ArrowRight, BookOpen, CheckCircle2, CircleAlert, FileUp, RefreshCw, Search, Sparkles, X } from 'lucide-react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import type { PageVO } from '@/shared/api/types'
import { isProcessingStatus, type DocumentStatus } from '../document-status'
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
  const view = searchParams.get('view') === 'documents' ? 'documents' : 'overview'
  const [page, setPage] = useState<PageVO<DocumentSummary>>(EMPTY_PAGE)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [uploadOpen, setUploadOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<DocumentSummary | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [query, setQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState<StatusFilterType>('ALL')
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

  const updateLocation = (nextView: 'overview' | 'documents', nextPage = 1) => {
    const nextParams = new URLSearchParams()
    if (nextView === 'documents') nextParams.set('view', 'documents')
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

  const processingDocuments = page.records.filter((document) => isProcessingStatus(document.status))
  const indexedCount = page.records.filter((document) => document.status === 'INDEXED').length
  const failedCount = page.records.filter((document) => document.status === 'FAILED').length
  const openDocument = (documentId: number) => navigate(`/knowledge-base/${documentId}`)

  return (
    <section className="min-h-full min-w-0 overflow-y-auto bg-gradient-to-b from-slate-50 via-slate-50/60 to-slate-100/80 px-4 py-6 sm:px-6 lg:px-10">
      <div className="mx-auto w-full max-w-[1280px]">
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
            onPrevious={() => updateLocation('documents', pageNum - 1)}
            onNext={() => updateLocation('documents', pageNum + 1)}
            deleteTarget={deleteTarget}
            onDeleteTargetChange={setDeleteTarget}
          />
        )}
      </div>
      <UploadDocumentDialog open={uploadOpen} onOpenChange={setUploadOpen} onUploaded={openDocument} />
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
  onView: (documentId: number) => void
}) {
  return (
    <div className="space-y-6">
      {/* 头部 Header */}
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">知识库</h1>
          <p className="mt-1 text-xs text-slate-500">管理用于 RAG 智能问答与检索增强的知识文档及向量化索引状态</p>
        </div>
        <Button
          onClick={onUpload}
          className="h-10 rounded-xl bg-gradient-to-r from-indigo-600 to-violet-600 px-4 text-xs font-semibold text-white shadow-md shadow-indigo-200 transition-all duration-200 hover:from-indigo-500 hover:to-violet-500 hover:shadow-indigo-300"
        >
          <FileUp className="size-4" />
          上传文档
        </Button>
      </header>

      {error && <LoadError message={error} onRetry={onRetry} />}

      {/* 默认知识库 Hero Banner */}
      <section className="flex flex-col justify-between gap-6 rounded-2xl border border-indigo-100/80 bg-gradient-to-br from-indigo-50/70 via-white to-slate-50 p-6 shadow-sm backdrop-blur-sm lg:flex-row lg:items-center">
        <div className="flex items-start gap-4">
          <span className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-indigo-600 to-violet-600 text-white shadow-md shadow-indigo-200">
            <BookOpen className="size-6" />
          </span>
          <div>
            <div className="flex items-center gap-3">
              <h2 className="text-lg font-bold text-slate-900">默认知识库</h2>
              <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-0.5 text-[10px] font-semibold text-emerald-600 border border-emerald-200/60">
                <span className="size-1.5 rounded-full bg-emerald-500" />
                运行正常
              </span>
            </div>
            <p className="mt-1.5 max-w-lg text-xs leading-relaxed text-slate-500">
              上传的文档会在自动解析、智能切分和向量索引完成后，服务于 AI 检索增强问答。
            </p>
          </div>
        </div>

        {/* 4 项核心 Metric 统计 */}
        <div className="grid grid-cols-2 gap-x-8 gap-y-4 rounded-xl border border-slate-100 bg-white/80 p-4 shadow-inner sm:grid-cols-4">
          {[
            { label: '全部文档', value: total, color: 'text-slate-800' },
            { label: '已索引', value: indexedCount, color: 'text-emerald-600' },
            { label: '处理中', value: processingDocuments.length, color: 'text-amber-600' },
            { label: '处理失败', value: failedCount, color: 'text-rose-600' },
          ].map((metric) => (
            <div key={metric.label} className="min-w-[70px]">
              <b className={`block text-xl font-bold tracking-tight ${metric.color}`}>
                {loading ? '—' : metric.value}
              </b>
              <span className="mt-1 block whitespace-nowrap text-[10px] font-medium text-slate-400">{metric.label}</span>
            </div>
          ))}
        </div>
      </section>

      {/* 最近文档板块 */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-bold text-slate-800">最近文档</h2>
          <button
            type="button"
            className="flex items-center gap-1.5 text-xs font-semibold text-indigo-600 transition-colors hover:text-indigo-500"
            onClick={onShowAll}
          >
            查看全部文档
            <ArrowRight className="size-3.5" />
          </button>
        </div>

        <section className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-sm">
          <div className="grid grid-cols-[minmax(190px,2.2fr)_0.8fr_0.75fr] gap-3 bg-slate-50/80 px-5 py-3 text-[10px] font-semibold text-slate-400 uppercase tracking-wider sm:grid-cols-[minmax(260px,2.2fr)_0.8fr_0.75fr_0.7fr]">
            <span>文档</span>
            <span>类型</span>
            <span>状态</span>
            <span className="hidden sm:block">更新时间</span>
          </div>

          {loading && (
            <p className="px-5 py-12 text-center text-xs font-medium text-slate-400">正在加载知识库概览…</p>
          )}

          {!loading && documents.length === 0 && (
            <p className="px-5 py-12 text-center text-xs font-medium text-slate-400">暂时没有文档，上传文件后即可开始构建知识库。</p>
          )}

          {!loading &&
            documents.map((document) => (
              <button
                key={document.documentId}
                type="button"
                onClick={() => onView(document.documentId)}
                className="grid w-full grid-cols-[minmax(190px,2.2fr)_0.8fr_0.75fr] items-center gap-3 border-t border-slate-100 px-5 py-3.5 text-left text-xs text-slate-600 transition-colors hover:bg-slate-50/80 sm:grid-cols-[minmax(260px,2.2fr)_0.8fr_0.75fr_0.7fr]"
              >
                <DocumentCell document={document} />
                <span className="font-semibold text-slate-700">{document.fileType || '—'}</span>
                <DocumentStatusBadge status={document.status} />
                <span className="hidden font-mono text-[11px] text-slate-400 sm:block">#{document.documentId}</span>
              </button>
            ))}
        </section>
      </div>

      {/* 底部提醒与流水线 */}
      <section className="grid gap-4 lg:grid-cols-2">
        <article className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm space-y-3">
          <h3 className="text-xs font-bold text-slate-800">正在处理</h3>
          {processingDocuments.length > 0 ? (
            <div>
              <p className="text-xs text-slate-500">{documentName(processingDocuments[0])} · 文档处理流水线</p>
              <div className="mt-3 flex gap-2">
                <span className="flex-1 rounded-xl bg-emerald-50 py-2 text-center text-[10px] font-semibold text-emerald-600 border border-emerald-100">
                  解析
                </span>
                <span className="flex-1 rounded-xl bg-emerald-50 py-2 text-center text-[10px] font-semibold text-emerald-600 border border-emerald-100">
                  切分
                </span>
                <span className="flex-1 rounded-xl bg-slate-100 py-2 text-center text-[10px] font-medium text-slate-400">
                  索引
                </span>
              </div>
            </div>
          ) : (
            <p className="rounded-xl bg-slate-50 p-3 text-xs text-slate-400">当前没有处理中的文档</p>
          )}
        </article>

        <article className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm space-y-3">
          <h3 className="text-xs font-bold text-slate-800">需要关注</h3>
          <div className="flex items-center gap-2.5 rounded-xl bg-slate-50 p-3 text-xs text-slate-600">
            <CircleAlert className="size-4 text-amber-500 shrink-0" />
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
  onPrevious,
  onNext,
  deleteTarget,
  onDeleteTargetChange,
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
  onView: (documentId: number) => void
  onDelete: (document: DocumentSummary) => void
  onPrevious: () => void
  onNext: () => void
  deleteTarget: DocumentSummary | null
  onDeleteTargetChange: (document: DocumentSummary | null) => void
}) {
  return (
    <div className="space-y-5">
      {/* 面包屑导航 */}
      <div className="flex items-center gap-2 text-xs text-slate-500">
        <button type="button" onClick={onBack} className="transition-colors hover:text-indigo-600">
          知识库
        </button>
        <span className="text-slate-400">/</span>
        <span className="font-medium text-slate-700">默认知识库</span>
      </div>

      {/* Header */}
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">全部文档</h1>
          <p className="mt-1 text-xs text-slate-500">共 {page.total} 个文档记录</p>
        </div>
        <Button
          onClick={onUpload}
          className="h-10 rounded-xl bg-gradient-to-r from-indigo-600 to-violet-600 px-4 text-xs font-semibold text-white shadow-md shadow-indigo-200 transition-all duration-200 hover:from-indigo-500 hover:to-violet-500 hover:shadow-indigo-300"
        >
          <FileUp className="size-4" />
          上传文档
        </Button>
      </header>

      {error && <LoadError message={error} onRetry={onRetry} />}

      {/* 状态分类 Quick Filter Tabs */}
      <div className="flex items-center gap-2 border-b border-slate-200/80 pb-2">
        {[
          { key: 'ALL', label: '全部状态' },
          { key: 'INDEXED', label: '已索引' },
          { key: 'PROCESSING', label: '处理中' },
          { key: 'FAILED', label: '处理失败' },
        ].map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => onStatusFilterChange(tab.key as StatusFilterType)}
            className={`rounded-xl px-3 py-1.5 text-xs font-semibold transition-all ${
              statusFilter === tab.key
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200/80'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* 工具栏搜索与过滤 */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="relative w-full max-w-sm">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <input
            value={query}
            onChange={(event) => onQueryChange(event.target.value)}
            placeholder="搜索当前页文档"
            className="h-10 w-full rounded-xl border border-slate-200/90 bg-white pl-10 pr-9 text-xs text-slate-700 placeholder-slate-400 shadow-sm outline-none transition-all duration-200 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
          />
          {query && (
            <button
              type="button"
              onClick={() => onQueryChange('')}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
            >
              <X className="size-3.5" />
            </button>
          )}
        </div>

        <div className="flex items-center gap-2">
          <span className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-medium text-slate-600 shadow-sm">
            20 条 / 页
          </span>
          <button
            type="button"
            aria-label="刷新文档列表"
            onClick={onRetry}
            className="inline-flex h-9 items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3.5 text-xs font-semibold text-slate-600 shadow-sm transition-all hover:bg-slate-50 hover:text-slate-900"
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
        onRetryItem={onRetry}
        onPrevious={onPrevious}
        onNext={onNext}
        deleteTarget={deleteTarget}
        onDeleteTargetChange={onDeleteTargetChange}
      />

      {/* 提示 Alert Banner */}
      <div className="flex items-center gap-2.5 rounded-2xl border border-amber-200/80 bg-amber-50/70 p-4 text-xs text-amber-800 shadow-sm">
        <Sparkles className="size-4 shrink-0 text-amber-500" />
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
      <span className="min-w-0 space-y-0.5">
        <b className="block truncate text-xs font-semibold text-slate-800">{documentName(document)}</b>
        <small className="block truncate text-[10px] text-slate-400">{document.originalFileName || '未提供原始文件名'}</small>
      </span>
    </span>
  )
}

/** 数据请求失败提示。 */
function LoadError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-rose-200 bg-rose-50/80 px-4 py-3 text-xs text-rose-700 shadow-sm">
      <span className="font-medium">{message}</span>
      <button
        type="button"
        onClick={onRetry}
        className="inline-flex items-center gap-1.5 rounded-xl border border-rose-200 bg-white px-3 py-1.5 font-semibold text-rose-600 shadow-sm hover:bg-rose-50 transition-colors"
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
