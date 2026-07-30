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

export interface ModelGovernanceConfigDTO {
  configId: number
  strategyMode?: 'FAILOVER' | 'WEIGHTED' | 'PROTECTION' // 治理模式: 主备降级 | 加权负载 | 严格限流保护
  timeoutMs?: number
  maxRetries?: number
  maxConcurrency?: number
  rateLimitRpm?: number
  rateLimitTpm?: number
  fallbackModel?: string
  primaryWeight?: number // 主节点权重 (%)
  fallbackWeight?: number // 备用节点权重 (%)
  circuitBreakerEnabled?: boolean
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
