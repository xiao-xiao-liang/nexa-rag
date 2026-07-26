import { describe, expect, it, vi } from 'vitest'
import { cancelGeneration, streamChat } from './chat-api'

describe('流式对话接口', () => {
  it('应解析 META、TOKEN 和 COMPLETE 事件', async () => {
    const encoder = new TextEncoder()
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode('event: META\ndata: {"conversationId":"c-1","generationId":"g-1"}\n\n'))
        controller.enqueue(encoder.encode('event: TOKEN\ndata: {"content":"你好"}\n\n'))
        controller.enqueue(encoder.encode('event: COMPLETE\ndata: {}\n\n'))
        controller.close()
      },
    })
    const fetchMock = vi.fn().mockResolvedValue(new Response(body, {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' },
    }))
    vi.stubGlobal('fetch', fetchMock)
    const events: string[] = []

    await streamChat({ content: '你好' }, (event) => events.push(event.type))

    expect(fetchMock).toHaveBeenCalledWith('/api/chat/stream', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ content: '你好' }),
    }))
    expect(events).toEqual(['META', 'TOKEN', 'COMPLETE'])
  })

  it('应通过删除接口取消生成任务', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await cancelGeneration('g/1')

    expect(fetchMock).toHaveBeenCalledWith('/api/chat/generations/g%2F1', { method: 'DELETE' })
  })
})
