import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { router } from './app/router'

describe('RAG 对话工作台', () => {
  beforeEach(async () => {
    await router.navigate('/chat')
  })

  afterEach(() => {
    cleanup()
    localStorage.clear()
    vi.unstubAllGlobals()
    window.history.replaceState({}, '', '/chat')
  })

  it('空会话时应展示会话优先的欢迎页和默认输入框', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: '0', message: null, data: { records: [], total: 0, current: 1, size: 20, pages: 0 }, traceId: null,
    }))))

    render(<App />)

    expect(await screen.findByRole('heading', { name: '你好，今天想做什么？' })).toBeInTheDocument()
    expect(screen.getByText('通过模型与知识库，让复杂信息转化为清晰答案。')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '解读文档' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '检索知识库' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '创建提示词' })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: '消息内容' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '新建对话' })).toBeInTheDocument()
  })

  it('点击快捷建议应将内容带入输入框', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(success({ records: [], total: 0, current: 1, size: 20, pages: 0 })))
    const user = userEvent.setup()
    render(<App />)

    await user.click(await screen.findByRole('button', { name: '检索知识库' }))

    expect(screen.getByRole('textbox', { name: '消息内容' })).toHaveValue('请从知识库中检索与我的问题相关的内容，并给出依据。')
  })

  it('应可从全局导航进入知识库，且一期不展示未接入能力', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(success({ records: [], total: 0, current: 1, size: 20, pages: 0 }))
      .mockResolvedValueOnce(success({ records: [], total: 0, current: 1, size: 20, pages: 0 }))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    render(<App />)

    await user.click(await screen.findByRole('link', { name: '知识库' }))

    expect(await screen.findByRole('heading', { name: '知识库文档' })).toBeInTheDocument()
    expect(screen.queryByText('处理状态筛选')).not.toBeInTheDocument()
    expect(screen.queryByText('查看原文件')).not.toBeInTheDocument()
    expect(screen.queryByText('OCR 配置')).not.toBeInTheDocument()
  })

  it('按 Enter 应发送消息，Shift+Enter 保留换行', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        code: '0', message: null, data: { records: [], total: 0, current: 1, size: 20, pages: 0 }, traceId: null,
      })))
      .mockResolvedValueOnce(new Response(new ReadableStream({ start: (controller) => controller.close() }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    const input = await screen.findByRole('textbox', { name: '消息内容' })

    await user.type(input, '第一行{Shift>}{Enter}{/Shift}第二行')
    expect(input).toHaveValue('第一行\n第二行')
    await user.keyboard('{Enter}')

    expect(fetchMock).toHaveBeenCalledWith('/api/chat/stream', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ content: '第一行\n第二行' }),
    }))
  })

  it('流式错误后即使收到 COMPLETE 也应保留失败状态和已输出内容', async () => {
    const encoder = new TextEncoder()
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        code: '0', message: null, data: { records: [], total: 0, current: 1, size: 20, pages: 0 }, traceId: null,
      })))
      .mockResolvedValueOnce(new Response(new ReadableStream({
        start(controller) {
          controller.enqueue(encoder.encode('event: TOKEN\ndata: {"content":"已生成内容"}\n\n'))
          controller.enqueue(encoder.encode('event: ERROR\ndata: {"errorMessage":"模型不可用"}\n\n'))
          controller.enqueue(encoder.encode('event: COMPLETE\ndata: {}\n\n'))
          controller.close()
        },
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        code: '0', message: null, data: { records: [], total: 0, current: 1, size: 20, pages: 0 }, traceId: null,
      })))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    const input = await screen.findByRole('textbox', { name: '消息内容' })

    await userEvent.type(input, '测试错误')
    await userEvent.keyboard('{Enter}')

    expect(await screen.findByText('已生成内容')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('模型不可用')
    expect(screen.getByRole('button', { name: /重试/ })).toBeInTheDocument()
  })

  it('切换会话时应忽略上一个会话晚到的历史响应', async () => {
    const firstHistory = deferred<Response>()
    const secondHistory = deferred<Response>()
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(success({ records: [conversation('c-1', '第一个'), conversation('c-2', '第二个')], total: 2, current: 1, size: 20, pages: 1 }))
      .mockReturnValueOnce(firstHistory.promise)
      .mockReturnValueOnce(secondHistory.promise))
    render(<App />)

    await userEvent.click(await screen.findByRole('button', { name: /第一个/ }))
    await userEvent.click(screen.getByRole('button', { name: /第二个/ }))
    secondHistory.resolve(success(historyPage('第二个会话消息')))
    expect(await screen.findByText('第二个会话消息')).toBeInTheDocument()
    firstHistory.resolve(success(historyPage('第一个会话消息')))

    await waitFor(() => expect(screen.queryByText('第一个会话消息')).not.toBeInTheDocument())
  })

  it('历史加载失败时应保留独立重试入口', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(success({ records: [conversation('c-1', '会话')], total: 1, current: 1, size: 20, pages: 1 }))
      .mockRejectedValueOnce(new TypeError('网络异常'))
      .mockResolvedValueOnce(success(historyPage('恢复后的消息')))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    await userEvent.click(await screen.findByRole('button', { name: '打开会话 会话' }))
    const retry = await screen.findByRole('button', { name: '重试加载历史' })
    await userEvent.click(retry)

    expect(await screen.findByText('恢复后的消息')).toBeInTheDocument()
  })

  it('输入法组合期间按 Enter 不应发送消息', async () => {
    const fetchMock = vi.fn().mockResolvedValue(success({ records: [], total: 0, current: 1, size: 20, pages: 0 }))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    const input = await screen.findByRole('textbox', { name: '消息内容' })
    await userEvent.type(input, '正在输入')
    const event = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true })
    Object.defineProperty(event, 'isComposing', { value: true })

    fireEvent(input, event)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(input).toHaveValue('正在输入')
  })

  it('停止生成时应只停止当前生成任务并保留已输出内容', async () => {
    const encoder = new TextEncoder()
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(success({ records: [], total: 0, current: 1, size: 20, pages: 0 }))
      .mockResolvedValueOnce(new Response(new ReadableStream({
        start(controller) {
          controller.enqueue(encoder.encode('event: META\ndata: {"generationId":"g-current"}\n\n'))
          controller.enqueue(encoder.encode('event: TOKEN\ndata: {"content":"保留内容"}\n\n'))
        },
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    const input = await screen.findByRole('textbox', { name: '消息内容' })
    await userEvent.type(input, '停止测试')
    await userEvent.keyboard('{Enter}')

    await userEvent.click(await screen.findByRole('button', { name: '停止生成' }))

    expect(await screen.findByText('保留内容')).toBeInTheDocument()
    expect(screen.getByText('已停止生成')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/chat/generations/g-current', { method: 'DELETE' })
  })

  it('META 在停止后到达时仍应取消对应生成任务', async () => {
    const encoder = new TextEncoder()
    let streamController: ReadableStreamDefaultController<Uint8Array>
    let streamAborted = false
    const callOrder: string[] = []
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(success({ records: [], total: 0, current: 1, size: 20, pages: 0 }))
      .mockImplementationOnce((_url: string, init?: RequestInit) => Promise.resolve(new Response(new ReadableStream({
        start(controller) {
          streamController = controller
          init?.signal?.addEventListener('abort', () => {
            streamAborted = true
            callOrder.push('abort')
            controller.error(new DOMException('请求已取消', 'AbortError'))
          }, { once: true })
        },
      }), { status: 200 })))
      .mockImplementationOnce(() => {
        callOrder.push('delete')
        return Promise.resolve(new Response(null, { status: 204 }))
      })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    const input = await screen.findByRole('textbox', { name: '消息内容' })
    await userEvent.type(input, '先停止')
    await userEvent.keyboard('{Enter}')
    await userEvent.click(await screen.findByRole('button', { name: '停止生成' }))

    expect(streamAborted).toBe(false)
    streamController!.enqueue(encoder.encode('event: META\ndata: {"generationId":"g-late"}\n\n'))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/chat/generations/g-late', { method: 'DELETE' }))
    await waitFor(() => expect(callOrder).toEqual(['delete', 'abort']))
  })
})

function success(data: unknown) {
  return new Response(JSON.stringify({ code: '0', message: null, data, traceId: null }))
}

function conversation(conversationId: string, title: string) {
  return { conversationId, title, status: 'ACTIVE', lastMessageTime: null, createdTime: '2026-01-01T00:00:00', updatedTime: '2026-01-01T00:00:00' }
}

function historyPage(content: string) {
  return { records: [{ messageId: content, sequence: 1, role: 'ASSISTANT', status: 'COMPLETED', content, createdTime: '2026-01-01T00:00:00', updatedTime: '2026-01-01T00:00:00' }], hasMore: false, nextBeforeSequence: null }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((promiseResolve) => { resolve = promiseResolve })
  return { promise, resolve }
}
