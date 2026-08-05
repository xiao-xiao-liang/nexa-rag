import { request } from '@/shared/api/client'

export interface PromptVersionItem {
  versionId: number | string
  versionNo: number
  content: string
  createdBy: string
  createdAt: string
  remark?: string | null
}

export interface PromptReleaseItem {
  releaseId: number | string
  stableVersionId: number | string
  canaryVersionId?: number | string | null
  canaryRule?: string | null
  releaseRevision: number
  releasedBy: string
  releasedAt: string
  rollbackFromReleaseId?: number | string | null
  remark?: string | null
}

export interface PromptItem {
  promptCode: string
  name: string
  variableSchema: string // JSON string e.g. '{"required":["question"]}'
  enabled: boolean
  currentReleaseId?: number | string | null
  currentReleaseRevision?: number | null
  versions: PromptVersionItem[]
  releases: PromptReleaseItem[]
}

export interface PromptPreviewRequest {
  content: string
}

export interface PromptPreviewResponse {
  content: string
}

export interface PromptSubmitRequest {
  content: string
}

export interface PromptReleaseRequest {
  stableVersionId: number | string
  canaryVersionId?: number | string | null
  canaryPercentage?: number | null
}

export interface PromptRollbackRequest {
  targetVersionId: number | string
}

export interface PromptUpdateRequest {
  name?: string
  variableSchema?: string
  enabled?: boolean
}

export interface PromptReleaseResponse {
  versionId: number | string
  releaseId: number | string
  releaseRevision: number
}

/**
 * 查询 Prompt 定义与摘要列表
 */
export function getPrompts(): Promise<PromptItem[]> {
  return request<PromptItem[]>('/api/model/prompts')
}

/**
 * 查询指定 Prompt 的详细信息（含完整版本与发布历史）
 */
export function getPrompt(promptCode: string): Promise<PromptItem> {
  return request<PromptItem>(`/api/model/prompts/${encodeURIComponent(promptCode)}`)
}

/**
 * 预览 Prompt 脱敏渲染正文
 */
export function previewPrompt(promptCode: string, content: string): Promise<PromptPreviewResponse> {
  return request<PromptPreviewResponse>(`/api/model/prompts/${encodeURIComponent(promptCode)}/preview`, {
    method: 'POST',
    body: JSON.stringify({ content }),
  })
}

/**
 * 提交 Prompt 新正文并立即发布为正式版本
 */
export function submitPrompt(promptCode: string, content: string): Promise<PromptReleaseResponse> {
  return request<PromptReleaseResponse>(`/api/model/prompts/${encodeURIComponent(promptCode)}/submit`, {
    method: 'POST',
    body: JSON.stringify({ content }),
  })
}

/**
 * 发布 Prompt 已有正式版本和可选灰度版本
 */
export function releasePrompt(promptCode: string, payload: PromptReleaseRequest): Promise<PromptReleaseResponse> {
  return request<PromptReleaseResponse>(`/api/model/prompts/${encodeURIComponent(promptCode)}/release`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

/**
 * 回滚到 Prompt 历史版本
 */
export function rollbackPrompt(promptCode: string, targetVersionId: number | string): Promise<PromptReleaseResponse> {
  return request<PromptReleaseResponse>(`/api/model/prompts/${encodeURIComponent(promptCode)}/rollback`, {
    method: 'POST',
    body: JSON.stringify({ targetVersionId }),
  })
}

/**
 * 更新 Prompt 基础定义（名称、变量契约 JSON 与启用/禁用状态）
 */
export function updatePrompt(promptCode: string, payload: PromptUpdateRequest): Promise<PromptItem> {
  return request<PromptItem>(`/api/model/prompts/${encodeURIComponent(promptCode)}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}
