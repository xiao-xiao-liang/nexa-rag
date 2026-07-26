import type { Result } from './types'

/**
 * 可直接呈现在界面上的接口异常。
 */
export class ApiError extends Error {
  readonly code: string
  readonly traceId: string | null

  constructor(code: string, message: string, traceId: string | null = null) {
    super(traceId ? `${message}（追踪 ID：${traceId}）` : message)
    this.name = 'ApiError'
    this.code = code
    this.traceId = traceId
  }
}

/**
 * 请求后端统一响应接口，并在业务失败时转换为可展示异常。
 *
 * @param path 接口相对路径
 * @param init Fetch 请求配置
 * @returns 成功响应中的 data 字段
 */
export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, init)
  } catch (error) {
    // 1. 取消请求属于调用方控制流，必须保留原始 AbortError。
    if (isAbortError(error)) {
      throw error
    }

    // 2. 将网络和代理层异常转换为可直接展示的稳定错误。
    throw new ApiError('NETWORK_ERROR', '网络请求失败，请稍后重试')
  }

  const result = await readResult<T>(response)

  // 1. 优先按后端业务编码判定成功，避免 HTTP 200 掩盖业务异常。
  if (result.code !== '0') {
    throw new ApiError(result.code, result.message || '请求失败', result.traceId)
  }

  // 2. 保留 HTTP 状态码兜底，处理网关或非标准响应。
  if (!response.ok) {
    throw new ApiError(String(response.status), result.message || '请求失败', result.traceId)
  }

  return result.data
}

async function readResult<T>(response: Response): Promise<Result<T>> {
  try {
    return await response.json() as Result<T>
  } catch (error) {
    // 1. JSON 读取过程的取消请求继续交由调用方处理。
    if (isAbortError(error)) {
      throw error
    }

    // 2. 其他解析失败统一转换为可展示的响应格式错误。
    throw new ApiError(String(response.status), '服务响应格式异常')
  }
}

function isAbortError(error: unknown): error is { name: 'AbortError' } {
  return typeof error === 'object' && error !== null && 'name' in error && error.name === 'AbortError'
}
