import { afterEach, describe, expect, it, vi } from 'vitest'
import { getConversationMessages, getConversations } from './conversation-api'

describe('会话查询接口', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('查询会话列表时应调用页码分页接口并返回 data', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({
        code: '0',
        message: null,
        data: {
          records: [{ conversationId: 'c-1', title: '测试会话' }],
          total: 1,
          current: 1,
          size: 20,
          pages: 1,
        },
        traceId: 'trace-1',
      }), { status: 200 }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const page = await getConversations()

    expect(fetchMock).toHaveBeenCalledWith('/api/conversations?current=1&size=20', undefined)
    expect(page.records[0]?.conversationId).toBe('c-1')
  })

  it('查询历史消息时应携带游标并返回 data', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({
        code: '0',
        message: null,
        data: {
          records: [{ messageId: 'm-1', sequence: 8, role: 'ASSISTANT', status: 'COMPLETED', content: '你好' }],
          hasMore: true,
          nextBeforeSequence: 8,
        },
        traceId: 'trace-2',
      }), { status: 200 }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const page = await getConversationMessages('c/1', { beforeSequence: 9, size: 30 })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/conversations/c%2F1/messages?beforeSequence=9&size=30',
      undefined,
    )
    expect(page.nextBeforeSequence).toBe(8)
  })

  it('响应业务失败码时应抛出可展示的接口错误', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({
        code: 'A0404',
        message: '会话不存在',
        data: null,
        traceId: 'trace-404',
      }), { status: 200 }),
    ))

    await expect(getConversations()).rejects.toEqual(
      expect.objectContaining({
        code: 'A0404',
        message: '会话不存在（追踪 ID：trace-404）',
        traceId: 'trace-404',
      }),
    )
  })

  it('网络请求被拒绝时应抛出稳定的可展示错误', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    await expect(getConversations()).rejects.toEqual(
      expect.objectContaining({
        code: 'NETWORK_ERROR',
        message: '网络请求失败，请稍后重试',
        traceId: null,
      }),
    )
  })

  it('HTTP 非成功状态且业务码成功时应按 HTTP 状态抛出错误', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({
        code: '0',
        message: '网关服务不可用',
        data: null,
        traceId: 'trace-502',
      }), { status: 502 }),
    ))

    await expect(getConversations()).rejects.toEqual(
      expect.objectContaining({
        code: '502',
        message: '网关服务不可用（追踪 ID：trace-502）',
        traceId: 'trace-502',
      }),
    )
  })

  it('请求取消时应保留原始 AbortError', async () => {
    const abortError = new DOMException('请求已取消', 'AbortError')
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(abortError))

    await expect(getConversations()).rejects.toBe(abortError)
  })

  it('响应 JSON 解析取消时应保留原始 AbortError', async () => {
    const abortError = new DOMException('请求已取消', 'AbortError')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: vi.fn().mockRejectedValue(abortError),
    } as unknown as Response))

    await expect(getConversations()).rejects.toBe(abortError)
  })
})
