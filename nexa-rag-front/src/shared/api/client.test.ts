import { afterEach, describe, expect, it, vi } from 'vitest'
import { request } from './client'

describe('通用请求客户端', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('应移除 FormData 请求中显式设置的 Content-Type', async () => {
    const fetchMock = vi.fn().mockResolvedValue(success())
    vi.stubGlobal('fetch', fetchMock)
    const body = new FormData()
    body.append('file', new File(['文档内容'], '员工手册.pdf'))

    await request('/api/documents/upload', {
      method: 'POST',
      body,
      headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'request-1' },
    })

    const init = fetchMock.mock.calls[0][1] as RequestInit
    const headers = new Headers(init.headers)
    expect(headers.has('Content-Type')).toBe(false)
    expect(headers.get('X-Request-Id')).toBe('request-1')
  })

  it('应为非空字符串请求体补充默认 JSON 请求头', async () => {
    const fetchMock = vi.fn().mockResolvedValue(success())
    vi.stubGlobal('fetch', fetchMock)

    await request('/api/example', { method: 'POST', body: '' })

    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(new Headers(init.headers).get('Content-Type')).toBe('application/json')
  })
})

function success(): Response {
  return new Response(JSON.stringify({ code: '0', message: null, data: null, traceId: null }))
}
