import { request } from '@/shared/api/client'

export interface ModelProviderCatalogItem {
  provider: string
  displayName: string
  defaultBaseUrl: string
  apiKeyRequired: boolean
  openAiCompatible?: boolean
  defaultEndpointPath?: string
  defaultGovernanceDescription?: string
  supportedTypes?: string[]
  recommendedModels?: Record<string, string[]>
}

export interface ModelConfigItem {
  configId: number
  configKey?: string
  modelName: string
  provider: string
  modelType: string
  baseUrl: string
  apiKeyMask?: string
  apiKeyMasked?: string
  enabled?: boolean
  timeoutMs?: number
  createTime?: string
}

export interface CreateModelConfigRequest {
  modelName: string
  provider: string
  modelType: string
  baseUrl: string
  apiKey: string
}

export interface ModelRouteItem {
  routeId: number
  routeKey: string
  modelType: string
  strategy: string
  enabled: boolean
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface ModelRouteUpdateRequest {
  routeKey?: string
  strategy?: string
  enabled?: boolean
  remark?: string
}

export interface ModelGovernanceConfigDTO {
  governanceId?: number
  configId?: number
  bindingMode?: 'CONFIG' | 'ROUTE'
  routeKey?: string
  enabled?: boolean
  strategyMode?: 'FAILOVER' | 'WEIGHTED' | 'PROTECTION'
  retryEnabled?: boolean
  maxAttempts?: number
  retryWaitMs?: number
  circuitEnabled?: boolean
  failureRateThreshold?: number
  slowCallRateThreshold?: number
  slowCallDurationMs?: number
  rateLimitEnabled?: boolean
  limitForPeriod?: number
  limitRefreshPeriodMs?: number
  timeLimiterTimeoutMs?: number
  maxConcurrentCalls?: number
  fallbackModel?: string
  primaryWeight?: number
  fallbackWeight?: number
  circuitBreakerEnabled?: boolean
  streamFirstChunkTimeoutMs?: number
  streamMaxDurationMs?: number
}

/** 查询内置模型厂商目录 */
export function getModelProviderCatalog(): Promise<ModelProviderCatalogItem[]> {
  return request<ModelProviderCatalogItem[]>('/api/model/providers/catalog')
}

/** 查询已配置的模型列表 */
export function getModelConfigs(): Promise<ModelConfigItem[]> {
  return request<ModelConfigItem[]>('/api/model/configs')
}

/** 创建新的模型配置 */
export function createModelConfig(data: CreateModelConfigRequest): Promise<ModelConfigItem> {
  return request<ModelConfigItem>('/api/model/configs', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

/** 删除指定模型配置 */
export function deleteModelConfig(configId: number): Promise<void> {
  return request<void>(`/api/model/configs/${configId}`, {
    method: 'DELETE',
  })
}

/** 测试模型配置连接 */
export function testModelConfig(configId: number): Promise<{ success: boolean; message: string }> {
  return request<{ success: boolean; message: string }>(`/api/model/configs/${configId}/test`, {
    method: 'POST',
  })
}

/** 查询模型路由列表 */
export function getModelRoutes(): Promise<ModelRouteItem[]> {
  return request<ModelRouteItem[]>('/api/model/routes')
}

/** 更新指定模型路由 */
export function updateModelRoute(routeId: number, data: ModelRouteUpdateRequest): Promise<ModelRouteItem> {
  return request<ModelRouteItem>(`/api/model/routes/${routeId}`, {
    method: 'PATCH',
    body: JSON.stringify(data),
  })
}

/** 查询指定模型的治理配置 */
export function getModelGovernanceConfig(configId: number): Promise<ModelGovernanceConfigDTO> {
  return request<ModelGovernanceConfigDTO>(`/api/model/configs/${configId}/governance`)
}

/** 更新指定模型的治理配置 */
export function updateModelGovernanceConfig(configId: number, data: ModelGovernanceConfigDTO): Promise<ModelGovernanceConfigDTO> {
  return request<ModelGovernanceConfigDTO>(`/api/model/configs/${configId}/governance`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

/** 查询系统全量模型治理配置列表 */
export function listModelGovernanceConfigs(): Promise<ModelGovernanceConfigDTO[]> {
  return request<ModelGovernanceConfigDTO[]>('/api/model/governance-configs')
}

/** 重置指定模型治理配置为系统默认值 */
export function resetModelGovernanceDefault(governanceId: number): Promise<void> {
  return request<void>(`/api/model/governance-configs/${governanceId}/reset-default`, {
    method: 'POST',
  })
}

/** 手动向 JVM 广播刷新模型注册表快照 */
export function refreshModelRegistry(): Promise<{ success: boolean; message: string }> {
  return request<{ success: boolean; message: string }>('/api/model/registry/refresh', {
    method: 'POST',
  })
}

/** 查询当前 JVM 的模型注册表快照概要 */
export function getModelRegistrySnapshot(): Promise<any> {
  return request<any>('/api/model/registry/snapshot')
}


