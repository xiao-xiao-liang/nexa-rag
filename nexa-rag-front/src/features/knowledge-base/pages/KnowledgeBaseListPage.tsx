import { useCallback, useEffect, useRef, useState } from 'react'
import { ArrowLeft, ArrowRight, BookOpen, CheckCircle2, CircleAlert, FileUp, RefreshCw, Search } from 'lucide-react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import type { PageVO } from '@/shared/api/types'
import { isProcessingStatus } from '../document-status'
import { DocumentListTable } from '../components/DocumentListTable'
import { DocumentStatusBadge } from '../components/DocumentStatusBadge'
import { UploadDocumentDialog } from '../components/UploadDocumentDialog'
import { deleteDocument, listDocuments, type DocumentSummary } from '../api/document-api'

const PAGE_SIZE = 20
const EMPTY_PAGE: PageVO<DocumentSummary> = { records: [], total: 0, current: 1, size: PAGE_SIZE, pages: 0 }

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
  const controllerRef = useRef<AbortController | null>(null)
  const requestIdRef = useRef(0)

  const loadPage = useCallback(async (targetPage: number) => {
    // 1. 取消上一次请求，避免旧响应覆盖当前视图的数据。
    controllerRef.current?.abort()
    const controller = new AbortController()
    controllerRef.current = controller
    const requestId = ++requestIdRef.current
    setLoading(true)
    setError(null)
    try {
      // 2. 概览和完整文档列表共用服务端分页数据。
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
      // 1. 完成删除后关闭确认弹窗，阻止重复提交。
      await deleteDocument(document.documentId)
      setDeleteTarget(null)
      // 2. 删除当前页最后一个项目时，回退到前一页。
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

  return <section className="min-h-full min-w-0 overflow-y-auto bg-[#f7f8fb] px-5 py-8 sm:px-8 lg:px-10">
    <div className="mx-auto w-full max-w-[1180px]">
      {view === 'overview' ? <KnowledgeOverview total={page.total} indexedCount={indexedCount} processingDocuments={processingDocuments} failedCount={failedCount} documents={page.records.slice(0, 3)} loading={loading} error={error} onRetry={() => void loadPage(pageNum)} onUpload={() => setUploadOpen(true)} onShowAll={() => updateLocation('documents')} onView={openDocument} /> : <DocumentLibrary page={page} pageNum={pageNum} loading={loading} error={error} deleting={deleting} query={query} onQueryChange={setQuery} onRetry={() => void loadPage(pageNum)} onUpload={() => setUploadOpen(true)} onBack={() => updateLocation('overview')} onView={openDocument} onDelete={(document) => void handleDelete(document)} onPrevious={() => updateLocation('documents', pageNum - 1)} onNext={() => updateLocation('documents', pageNum + 1)} deleteTarget={deleteTarget} onDeleteTargetChange={setDeleteTarget} />}
    </div>
    <UploadDocumentDialog open={uploadOpen} onOpenChange={setUploadOpen} onUploaded={openDocument} />
  </section>
}

/** 知识库概览页，按原型展示总览、最近文档和处理提醒。 */
function KnowledgeOverview({ total, indexedCount, processingDocuments, failedCount, documents, loading, error, onRetry, onUpload, onShowAll, onView }: {
  total: number; indexedCount: number; processingDocuments: DocumentSummary[]; failedCount: number; documents: DocumentSummary[]; loading: boolean; error: string | null; onRetry: () => void; onUpload: () => void; onShowAll: () => void; onView: (documentId: number) => void
}) {
  return <>
    <header className="flex flex-wrap items-end justify-between gap-4"><div><h1 className="text-[26px] font-semibold tracking-[-0.04em] text-[#2f2d38]">知识库</h1><p className="mt-2 text-[13px] text-[#7d899b]">管理用于 RAG 问答的文档与处理状态</p></div><Button onClick={onUpload} className="h-10 rounded-lg bg-[#5b5ed2] px-4 text-xs font-semibold hover:bg-[#4f52c5]"><FileUp className="size-4" />上传文档</Button></header>
    {error && <LoadError message={error} onRetry={onRetry} />}
    <section className="mt-7 flex flex-col justify-between gap-6 rounded-[14px] border border-[#e1e6ef] bg-gradient-to-br from-[#f0f1ff] via-white to-white p-6 lg:flex-row lg:items-center"><div className="flex items-start gap-4"><span className="flex size-[46px] shrink-0 items-center justify-center rounded-[14px] bg-[#5b5ed2] text-white shadow-[0_10px_19px_rgba(91,94,210,0.22)]"><BookOpen className="size-5" /></span><div><h2 className="text-[17px] font-semibold text-[#363442]">默认知识库</h2><p className="mt-1.5 max-w-[450px] text-xs leading-5 text-[#748096]">文档会在解析、切分和索引完成后用于对话检索</p><span className="mt-2 inline-flex items-center rounded-md bg-[#e6f8ee] px-2 py-1 text-[10px] text-[#258459]"><CheckCircle2 className="mr-1 size-3" />运行正常</span></div></div><div className="grid grid-cols-2 gap-x-7 gap-y-4 sm:grid-cols-4">{[{ label: '全部文档', value: total }, { label: '已索引', value: indexedCount }, { label: '处理中', value: processingDocuments.length }, { label: '处理失败', value: failedCount }].map((metric) => <div key={metric.label} className="min-w-[58px]"><b className="block text-xl font-semibold tracking-tight text-[#353340]">{loading ? '—' : metric.value}</b><span className="mt-1 block whitespace-nowrap text-[10px] text-[#8892a1]">{metric.label}</span></div>)}</div></section>
    <div className="mt-8 flex items-center justify-between"><h2 className="text-base font-semibold text-[#3b3945]">最近文档</h2><button type="button" className="flex items-center gap-1 text-xs font-medium text-[#5d60cc] hover:text-[#4e51ba]" onClick={onShowAll}>查看全部文档<ArrowRight className="size-3.5" /></button></div>
    <section className="mt-3 overflow-hidden rounded-xl border border-[#e1e6ee] bg-white"><div className="grid grid-cols-[minmax(190px,2.2fr)_0.8fr_0.75fr] gap-3 bg-[#fafbfc] px-4 py-3 text-[10px] font-semibold text-[#9ba4b1] sm:grid-cols-[minmax(260px,2.2fr)_0.8fr_0.75fr_0.7fr]"><span>文档</span><span>类型</span><span>状态</span><span className="hidden sm:block">更新时间</span></div>{loading && <p className="px-4 py-10 text-center text-sm text-[#8e98a7]">正在加载知识库概览…</p>}{!loading && documents.length === 0 && <p className="px-4 py-10 text-center text-sm text-[#8e98a7]">暂时没有文档，上传文件后即可开始构建知识库。</p>}{!loading && documents.map((document) => <button key={document.documentId} type="button" onClick={() => onView(document.documentId)} className="grid w-full grid-cols-[minmax(190px,2.2fr)_0.8fr_0.75fr] items-center gap-3 border-t border-[#edf0f4] px-4 py-3 text-left text-xs text-[#66738a] transition-colors hover:bg-[#fafaff] sm:grid-cols-[minmax(260px,2.2fr)_0.8fr_0.75fr_0.7fr]"><DocumentCell document={document} /><span>{document.fileType || '—'}</span><DocumentStatusBadge status={document.status} /><span className="hidden text-[#8d97a5] sm:block">#{document.documentId}</span></button>)}</section>
    <section className="mt-6 grid gap-4 lg:grid-cols-[1.1fr_0.9fr]"><article className="rounded-xl border border-[#e1e6ee] bg-white p-5"><h3 className="text-sm font-semibold text-[#3d3b47]">正在处理</h3>{processingDocuments.length > 0 ? <><p className="mt-1.5 text-xs text-[#8290a2]">{documentName(processingDocuments[0])} · 文档处理流水线</p><div className="mt-4 flex gap-2"><span className="flex-1 rounded-lg bg-[#edf8f1] px-2 py-2 text-center text-[10px] text-[#28835e]">解析</span><span className="flex-1 rounded-lg bg-[#edf8f1] px-2 py-2 text-center text-[10px] text-[#28835e]">切分</span><span className="flex-1 rounded-lg bg-[#f5f6fa] px-2 py-2 text-center text-[10px] text-[#95a0af]">索引</span></div></> : <p className="mt-4 rounded-lg bg-[#f8f9fc] px-3 py-3 text-xs text-[#8792a2]">当前没有处理中的文档</p>}</article><article className="rounded-xl border border-[#e1e6ee] bg-white p-5"><h3 className="text-sm font-semibold text-[#3d3b47]">需要关注</h3><div className="mt-4 flex items-center gap-2 rounded-lg bg-[#f8f9fc] px-3 py-3 text-xs text-[#728097]"><CircleAlert className="size-4 text-[#a8a4bd]" />{failedCount > 0 ? `${failedCount} 个文档处理失败，查看失败原因` : '当前没有处理失败的文档'}</div></article></section>
  </>
}

/** 全部文档页，按原型提供搜索、刷新、分页和失败提醒。 */
function DocumentLibrary({ page, pageNum, loading, error, deleting, query, onQueryChange, onRetry, onUpload, onBack, onView, onDelete, onPrevious, onNext, deleteTarget, onDeleteTargetChange }: {
  page: PageVO<DocumentSummary>; pageNum: number; loading: boolean; error: string | null; deleting: boolean; query: string; onQueryChange: (value: string) => void; onRetry: () => void; onUpload: () => void; onBack: () => void; onView: (documentId: number) => void; onDelete: (document: DocumentSummary) => void; onPrevious: () => void; onNext: () => void; deleteTarget: DocumentSummary | null; onDeleteTargetChange: (document: DocumentSummary | null) => void
}) {
  return <>
    <div className="flex items-center gap-1.5 text-[11px] text-[#8b96a7]"><button type="button" onClick={onBack} className="hover:text-[#5b5ed2]">知识库</button><span>/</span><span>默认知识库</span></div>
    <header className="mt-2 flex flex-wrap items-start justify-between gap-4"><div><h1 className="text-[26px] font-semibold tracking-[-0.04em] text-[#2f2d38]">全部文档</h1><p className="mt-2 text-[13px] text-[#7d899b]">共 {page.total} 个文档</p></div><Button onClick={onUpload} className="h-10 rounded-lg bg-[#5b5ed2] px-4 text-xs font-semibold hover:bg-[#4f52c5]"><FileUp className="size-4" />上传文档</Button></header>
    {error && <LoadError message={error} onRetry={onRetry} />}
    <div className="mt-7 flex flex-wrap gap-2"><label className="relative min-w-[220px] flex-1"><Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-[#a1aab7]" /><input value={query} onChange={(event) => onQueryChange(event.target.value)} placeholder="搜索当前页文档" className="h-10 w-full rounded-lg border border-[#dfe5ef] bg-white pl-9 pr-3 text-xs text-[#536075] outline-none transition focus:border-[#7475df] focus:ring-2 focus:ring-[#e8e9ff]" /></label><button type="button" className="h-10 rounded-lg border border-[#dfe5ef] bg-white px-3 text-[11px] text-[#65738a]">20 条 / 页</button><button type="button" aria-label="刷新文档列表" onClick={onRetry} className="inline-flex h-10 items-center gap-2 rounded-lg border border-[#dfe5ef] bg-white px-3 text-[11px] text-[#65738a] hover:bg-[#fafbff]"><RefreshCw className="size-3.5" />刷新</button></div>
    <DocumentListTable page={page} pageNum={pageNum} loading={loading} deleting={deleting} query={query} onView={onView} onDelete={onDelete} onPrevious={onPrevious} onNext={onNext} deleteTarget={deleteTarget} onDeleteTargetChange={onDeleteTargetChange} />
    <div className="mt-[18px] flex items-center gap-2 rounded-lg bg-[#fff7e7] px-3.5 py-3 text-[11px] text-[#926b2a]"><CircleAlert className="size-4" /><span>处理失败的文档可在详情页查看失败阶段与原因，并重新提交处理。</span></div>
  </>
}

/** 文档名与文件类型的小型展示单元。 */
function DocumentCell({ document }: { document: DocumentSummary }) {
  return <span className="flex min-w-0 items-center gap-3"><span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-[#eef0ff] text-[9px] font-bold text-[#5d60cc]">{document.fileType || '文件'}</span><span className="min-w-0"><b className="block truncate text-xs font-medium text-[#3d4a60]">{documentName(document)}</b><small className="mt-1 block truncate text-[10px] text-[#9aa3b1]">{document.originalFileName || '未提供原始文件名'}</small></span></span>
}

/** 数据请求失败提示。 */
function LoadError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return <div className="mt-6 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[#f3cfca] bg-[#fff4f2] px-4 py-3 text-sm text-[#b6574d]"><span>{message}</span><button type="button" onClick={onRetry} className="inline-flex items-center gap-1.5 rounded-lg bg-white px-3 py-2 text-xs text-[#a85049] shadow-sm"><RefreshCw className="size-3.5" />重新加载</button></div>
}

function documentName(document: DocumentSummary): string { return document.title || document.originalFileName || '未命名文档' }
function parsePageNum(value: string | null): number { const parsed = Number(value); return Number.isInteger(parsed) && parsed > 0 ? parsed : 1 }
