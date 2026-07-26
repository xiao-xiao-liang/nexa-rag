import { useCallback, useEffect, useRef, useState } from 'react'
import { FileUp, RefreshCw } from 'lucide-react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import type { PageVO } from '@/shared/api/types'
import { DocumentListTable } from '../components/DocumentListTable'
import { UploadDocumentDialog } from '../components/UploadDocumentDialog'
import { deleteDocument, listDocuments, type DocumentSummary } from '../api/document-api'

const PAGE_SIZE = 20
const EMPTY_PAGE: PageVO<DocumentSummary> = { records: [], total: 0, current: 1, size: PAGE_SIZE, pages: 0 }

/** 知识库文档列表页面，提供服务端分页、上传和删除能力。 */
export function KnowledgeBaseListPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const pageNum = parsePageNum(searchParams.get('page'))
  const [page, setPage] = useState<PageVO<DocumentSummary>>(EMPTY_PAGE)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [uploadOpen, setUploadOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<DocumentSummary | null>(null)
  const [deleting, setDeleting] = useState(false)
  const controllerRef = useRef<AbortController | null>(null)
  const requestIdRef = useRef(0)

  const loadPage = useCallback(async (targetPage: number) => {
    // 1. 取消上一条未完成的列表请求，避免过期响应覆盖最新页数据。
    controllerRef.current?.abort()
    const controller = new AbortController()
    controllerRef.current = controller
    const requestId = ++requestIdRef.current
    setLoading(true)
    setError(null)
    try {
      // 2. 按服务端页码加载，前端不模拟本地分页或状态筛选。
      const response = await listDocuments(targetPage, PAGE_SIZE, controller.signal)
      if (requestId === requestIdRef.current) {
        setPage(response)
      }
    } catch (loadError) {
      if (requestId === requestIdRef.current && (loadError as { name?: string }).name !== 'AbortError') {
        setError(loadError instanceof Error ? loadError.message : '文档列表加载失败，请稍后重试')
      }
    } finally {
      if (requestId === requestIdRef.current) {
        setLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    void loadPage(pageNum)
    return () => controllerRef.current?.abort()
  }, [loadPage, pageNum])

  const changePage = (nextPage: number) => {
    setSearchParams(nextPage === 1 ? {} : { page: String(nextPage) })
  }

  const handleDelete = async (document: DocumentSummary) => {
    if (deleting) {
      return
    }
    setDeleting(true)
    setError(null)
    try {
      // 1. 调用删除接口后先关闭确认弹窗，避免用户重复触发删除。
      await deleteDocument(document.documentId)
      setDeleteTarget(null)
      // 2. 当前页最后一条被删除时回退一页，其余情况刷新当前页。
      const nextPage = page.records.length === 1 && pageNum > 1 ? pageNum - 1 : pageNum
      if (nextPage === pageNum) {
        await loadPage(nextPage)
      } else {
        changePage(nextPage)
      }
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : '删除文档失败，请稍后重试')
    } finally {
      setDeleting(false)
    }
  }

  return (
    <section className="min-w-0 flex-1 overflow-y-auto bg-slate-50 p-6 md:p-10">
      <div className="mx-auto flex w-full max-w-6xl flex-col gap-6">
        <header className="flex flex-wrap items-end justify-between gap-4"><div><p className="text-sm font-medium text-blue-600">知识库</p><h1 className="mt-1 text-2xl font-semibold tracking-tight">知识库文档</h1><p className="mt-2 text-sm text-muted-foreground">管理用于 RAG 问答的文档，上传后由后端完成处理。</p></div><Button onClick={() => setUploadOpen(true)}><FileUp className="size-4" />上传文档</Button></header>
        {error && <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"><span>{error}</span><Button variant="outline" size="sm" onClick={() => void loadPage(pageNum)}><RefreshCw className="size-3.5" />重新加载</Button></div>}
        {loading && page.records.length === 0 ? <div className="rounded-2xl border bg-card px-5 py-14 text-center text-sm text-muted-foreground">正在加载文档列表…</div> : <DocumentListTable page={page} pageNum={pageNum} loading={loading} deleting={deleting} onView={(documentId) => navigate(`/knowledge-base/${documentId}`)} onDelete={(document) => void handleDelete(document)} onPrevious={() => changePage(pageNum - 1)} onNext={() => changePage(pageNum + 1)} deleteTarget={deleteTarget} onDeleteTargetChange={setDeleteTarget} />}
      </div>
      <UploadDocumentDialog open={uploadOpen} onOpenChange={setUploadOpen} onUploaded={(documentId) => navigate(`/knowledge-base/${documentId}`)} />
    </section>
  )
}

function parsePageNum(value: string | null): number {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 1
}
