/** 流式对话请求。 */
export interface ChatStreamRequest {
  conversationId?: string
  content: string
}

/** 后端流式事件类型。 */
export type ChatStreamEventType = 'META' | 'TOKEN' | 'COMPLETE' | 'ERROR' | 'CANCELLED'

/** 后端流式事件载荷。 */
export interface ChatStreamEvent {
  type: ChatStreamEventType
  content?: string | null
  conversationId?: string | null
  traceId?: string | null
  generationId?: string | null
  messageId?: string | null
  errorCode?: string | null
  errorMessage?: string | null
}

/**
 * 发起 RAG 流式对话并逐条读取 SSE 事件。
 */
export async function streamChat(
  request: ChatStreamRequest,
  onEvent: (event: ChatStreamEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify(request),
    signal,
  })
  if (!response.ok || !response.body) {
    throw new Error(`流式对话请求失败（${response.status}）`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const blocks = buffer.split(/\r?\n\r?\n/)
      buffer = blocks.pop() ?? ''
      blocks.forEach((block) => parseSseBlock(block, onEvent))
    }
    buffer += decoder.decode()
    if (buffer.trim()) {
      parseSseBlock(buffer, onEvent)
    }
  } finally {
    reader.releaseLock()
  }
}

/** 主动停止指定生成任务。 */
export async function cancelGeneration(generationId: string): Promise<void> {
  const response = await fetch(`/api/chat/generations/${encodeURIComponent(generationId)}`, {
    method: 'DELETE',
  })
  if (!response.ok) {
    throw new Error('停止生成失败，请稍后重试')
  }
}

function parseSseBlock(block: string, onEvent: (event: ChatStreamEvent) => void): void {
  let eventType: string | undefined
  const dataLines: string[] = []
  block.split(/\r?\n/).forEach((line) => {
    if (line.startsWith('event:')) {
      eventType = line.slice(6).trim()
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  })
  if (!eventType || dataLines.length === 0 || !isChatStreamEventType(eventType)) {
    return
  }

  try {
    const payload = JSON.parse(dataLines.join('\n')) as Omit<ChatStreamEvent, 'type'>
    onEvent({ type: eventType, ...payload })
  } catch {
    // 忽略无法解析的单条 SSE 数据，保持已收到的有效内容。
  }
}

function isChatStreamEventType(value: string): value is ChatStreamEventType {
  return ['META', 'TOKEN', 'COMPLETE', 'ERROR', 'CANCELLED'].includes(value)
}
