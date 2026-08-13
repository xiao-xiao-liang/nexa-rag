import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createModelRoute,
  deleteModelRoute,
  getModelRouteConfigs,
  getModelRoutes,
} from './model-api'

function successData(data: unknown): Response {
  return new Response(JSON.stringify({ code: '0', message: null, data, traceId: null }), { status: 200 })
}

describe('模型路由接口', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('查询路由候选配置应调用对应路径并返回 data', async () => {
    const fetchMock = vi.fn().mockResolvedValue(successData([
      { routeConfigId: 1, routeId: 2, configId: 3, modelName: 'qwen-plus' },
    ]))
    vi.stubGlobal('fetch', fetchMock)

    const list = await getModelRouteConfigs(2)

    expect(fetchMock).toHaveBeenCalledWith('/api/model/routes/2/configs', undefined)
    expect(list[0]?.modelName).toBe('qwen-plus')
  })

  it('创建路由应 POST 到 /api/model/routes', async () => {
    const fetchMock = vi.fn().mockResolvedValue(successData({ routeId: 9, routeKey: 'DEFAULT_LLM', modelType: 'CHAT' }))
    vi.stubGlobal('fetch', fetchMock)

    await createModelRoute({ routeKey: 'DEFAULT_LLM', modelType: 'CHAT' })

    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe('/api/model/routes')
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body))).toEqual({ routeKey: 'DEFAULT_LLM', modelType: 'CHAT' })
  })

  it('删除路由应调用 DELETE 接口', async () => {
    const fetchMock = vi.fn().mockResolvedValue(successData(null))
    vi.stubGlobal('fetch', fetchMock)

    await deleteModelRoute(5)

    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe('/api/model/routes/5')
    expect(init.method).toBe('DELETE')
  })

  it('查询路由列表应调用 GET 接口', async () => {
    const fetchMock = vi.fn().mockResolvedValue(successData([
      { routeId: 1, routeKey: 'DEFAULT_LLM', modelType: 'CHAT', strategy: 'FAILOVER', enabled: true },
    ]))
    vi.stubGlobal('fetch', fetchMock)

    const routes = await getModelRoutes()

    expect(fetchMock).toHaveBeenCalledWith('/api/model/routes', undefined)
    expect(routes[0]?.routeKey).toBe('DEFAULT_LLM')
  })
})
