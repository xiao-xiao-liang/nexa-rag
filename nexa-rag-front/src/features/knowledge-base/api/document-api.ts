import { request } from '@/shared/api/client'
import type { PageVO } from '@/shared/api/types'
import type { DocumentStatus } from '../document-status'

/** 文档列表项。 */
export interface DocumentSummary {
  documentId: number | string
  title: string | null
  originalFileName: string | null
  fileType: string | null
  status: DocumentStatus
}

/** 文档详情。 */
export interface DocumentDetail extends DocumentSummary {
  description: string | null
  fileSize: number | null
  originalFileUrl: string | null
  parsedFileUrl: string | null
  processConfigJson: string | null
}

/** 文档处理状态。 */
export interface DocumentProcessStatus {
  documentId: number | string
  processId: string | null
  status: DocumentStatus
  messageStatus: string | null
  consumedTimes: number | null
  failureStage: string | null
  failureReason: string | null
}

/** 与文档服务切分策略枚举保持一致。 */
export type SplitStrategy = 'PARENT_MARKDOWN' | 'BROTHER_MARKDOWN' | 'REGEX_TEXT' | 'EXCEL'

/** 文本切分配置输入。 */
export interface SplitConfigInput {
  splitStrategy: SplitStrategy
  chunkSize?: number
  chunkOverlap?: number
}

/** 文本分块。 */
export interface DocumentChunk {
  chunkId: string
  documentId: number | string
  chunkOrder: number
  text: string
  status: string
}

/** 上传成功后的文档处理批次。 */
export interface UploadDocumentResponse {
  documentId: number | string
  processId: string | null
  status: DocumentStatus
}

/** 上传表单输入。 */
export interface UploadDocumentInput {
  file: File
  title: string
  description: string
  splitConfig?: SplitConfigInput | null
}

/** 查询文档服务端分页列表。 */
export function listDocuments(pageNum = 1, pageSize = 20, signal?: AbortSignal): Promise<PageVO<DocumentSummary>> {
  return request<PageVO<DocumentSummary>>(`/api/documents?pageNum=${pageNum}&pageSize=${pageSize}`, signal ? { signal } : undefined)
}

/** 上传文档并提交处理配置。 */
export function uploadDocument(input: UploadDocumentInput, signal?: AbortSignal): Promise<UploadDocumentResponse> {
  const body = new FormData()
  body.append('file', input.file)

  const requestPayload: Record<string, unknown> = {
    title: input.title || null,
    description: input.description || null,
  }

  if (input.splitConfig) {
    requestPayload.splitConfig = input.splitConfig
  }

  body.append('request', new Blob([JSON.stringify(requestPayload)], { type: 'application/json' }))
  return request<UploadDocumentResponse>('/api/documents/upload', { method: 'POST', body, signal })
}

/** 查询文档详情。 */
export function getDocument(documentId: number | string, signal?: AbortSignal): Promise<DocumentDetail> {
  return request<DocumentDetail>(`/api/documents/${encodeDocumentId(documentId)}`, signal ? { signal } : undefined)
}

/** 查询文档处理状态。 */
export function getDocumentProcessStatus(documentId: number | string, signal?: AbortSignal): Promise<DocumentProcessStatus> {
  return request<DocumentProcessStatus>(`/api/documents/${encodeDocumentId(documentId)}/process-status`, signal ? { signal } : undefined)
}

/** 查询文档文本分块。 */
export function getDocumentChunks(documentId: number | string, pageNum = 1, pageSize = 20, signal?: AbortSignal): Promise<PageVO<DocumentChunk>> {
  return request<PageVO<DocumentChunk>>(`/api/documents/${encodeDocumentId(documentId)}/chunks?pageNum=${pageNum}&pageSize=${pageSize}`, signal ? { signal } : undefined)
}

/** 为已上传文档提交处理任务。 */
export function processDocument(documentId: number | string, signal?: AbortSignal): Promise<DocumentProcessStatus> {
  return request<DocumentProcessStatus>(`/api/documents/${encodeDocumentId(documentId)}/process`, signal ? { method: 'POST', signal } : { method: 'POST' })
}

/** 重试失败文档的处理任务。 */
export function retryDocument(documentId: number | string, signal?: AbortSignal): Promise<DocumentProcessStatus> {
  return request<DocumentProcessStatus>(`/api/documents/${encodeDocumentId(documentId)}/retry`, signal ? { method: 'POST', signal } : { method: 'POST' })
}

/** 删除文档。 */
export function deleteDocument(documentId: number | string, signal?: AbortSignal): Promise<boolean> {
  return request<boolean>(`/api/documents/${encodeDocumentId(documentId)}`, signal ? { method: 'DELETE', signal } : { method: 'DELETE' })
}

function encodeDocumentId(documentId: number | string): string {
  return encodeURIComponent(String(documentId))
}
