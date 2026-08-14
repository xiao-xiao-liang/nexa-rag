import { useCallback, useEffect, useRef, useState, type KeyboardEvent } from 'react'
import {
  BookOpen, Check, ChevronDown, ChevronUp, CircleStop, Compass, Copy, FileText, HelpCircle, Paperclip, RefreshCw, Send, Sparkles,
} from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { cancelGeneration, streamChat, type ChatStreamEvent } from '@/features/chat/api/chat-api'
import { AssistantMarkdown } from '@/features/chat/components/AssistantMarkdown'
import { DEFAULT_CONVERSATION_AGENT, type ConversationAgentMeta } from '@/features/agents/agent-registry'
import {
  getConversationMessages, type ConversationMessage,
} from '@/features/conversations/api/conversation-api'
import { useConversationNavigation } from '@/features/conversations/ConversationNavigationContext'

interface TimelineMessage extends ConversationMessage {
  local?: boolean
  errorMessage?: string
}

const CONVERSATION_AGENT_STORAGE_KEY = 'nexa-rag.conversation-agents'

const initialAssistant = (): TimelineMessage => ({
  messageId: `local-${crypto.randomUUID()}`,
  sequence: Number.MAX_SAFE_INTEGER,
  role: 'ASSISTANT',
  status: 'GENERATING',
  content: '',
  createdTime: new Date().toISOString(),
  updatedTime: new Date().toISOString(),
  local: true,
})

/** RAG 对话工作台，承载会话列表、历史消息与流式回复。 */
export default function ChatWorkspace() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { conversations, refresh: refreshConversations } = useConversationNavigation()
  const selectedId = searchParams.get('conversation')
  const [messages, setMessages] = useState<TimelineMessage[]>([])
  const [nextBeforeSequence, setNextBeforeSequence] = useState<number | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [draft, setDraft] = useState('')
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyError, setHistoryError] = useState<string | null>(null)
  const [olderHistoryError, setOlderHistoryError] = useState<string | null>(null)
  const [streaming, setStreaming] = useState(false)
  const [activeAssistantId, setActiveAssistantId] = useState<string | null>(null)
  const [conversationAgents, setConversationAgents] = useState<Record<string, ConversationAgentMeta>>(readConversationAgents)
  const [streamError, setStreamError] = useState<string | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const stopRequestedRef = useRef(false)
  const generationIdRef = useRef<string | null>(null)
  const streamActiveRef = useRef(false)
  const activeStreamConversationIdRef = useRef<string | null>(null)
  const cancelledGenerationIdsRef = useRef(new Set<string>())
  const historyAbortRef = useRef<AbortController | null>(null)
  const historyRequestRef = useRef(0)
  const activeAgent = selectedId ? conversationAgents[selectedId] ?? DEFAULT_CONVERSATION_AGENT : DEFAULT_CONVERSATION_AGENT

  useEffect(() => {
    localStorage.setItem(CONVERSATION_AGENT_STORAGE_KEY, JSON.stringify(conversationAgents))
  }, [conversationAgents])

  useEffect(() => {
    return () => {
      abortRef.current?.abort()
      historyAbortRef.current?.abort()
    }
  }, [])

  const loadConversation = useCallback(async (conversationId: string) => {
    abortRef.current?.abort()
    historyAbortRef.current?.abort()
    const requestId = ++historyRequestRef.current
    const controller = new AbortController()
    historyAbortRef.current = controller
    setConversationAgents((current) => ({ ...current, [conversationId]: current[conversationId] ?? DEFAULT_CONVERSATION_AGENT }))
    setMessages([])
    setNextBeforeSequence(null)
    setHasMore(false)
    setHistoryLoading(true)
    setHistoryError(null)
    setOlderHistoryError(null)
    setStreamError(null)
    try {
      const page = await getConversationMessages(conversationId, { signal: controller.signal })
      if (historyRequestRef.current !== requestId) {
        return
      }
      setMessages(page.records)
      setHasMore(page.hasMore)
      setNextBeforeSequence(page.nextBeforeSequence)
    } catch (error) {
      if (historyRequestRef.current !== requestId || (error as { name?: string }).name === 'AbortError') {
        return
      }
      setHistoryError(error instanceof Error ? error.message : '历史消息加载失败，请稍后重试')
    } finally {
      if (historyRequestRef.current === requestId) {
        setHistoryLoading(false)
      }
    }
  }, [])

  const loadOlderMessages = async () => {
    if (!selectedId || !hasMore || nextBeforeSequence === null || historyLoading) {
      return
    }
    const requestId = ++historyRequestRef.current
    const controller = new AbortController()
    historyAbortRef.current?.abort()
    historyAbortRef.current = controller
    setHistoryLoading(true)
    setOlderHistoryError(null)
    try {
      const page = await getConversationMessages(selectedId, { beforeSequence: nextBeforeSequence, signal: controller.signal })
      if (historyRequestRef.current !== requestId) {
        return
      }
      setMessages((current) => [...page.records, ...current])
      setHasMore(page.hasMore)
      setNextBeforeSequence(page.nextBeforeSequence)
    } catch (error) {
      if (historyRequestRef.current !== requestId || (error as { name?: string }).name === 'AbortError') {
        return
      }
      setOlderHistoryError(error instanceof Error ? error.message : '历史消息加载失败，请稍后重试')
    } finally {
      if (historyRequestRef.current === requestId) {
        setHistoryLoading(false)
      }
    }
  }

  useEffect(() => {
    if (selectedId) {
      // 1. 新建会话的 META 仅补充地址中的会话标识，不能把当前 SSE 误判为切换会话。
      if (streamActiveRef.current && activeStreamConversationIdRef.current === selectedId) {
        return
      }
      // 2. 用户切换到其他会话时，继续加载历史消息并中断旧会话的流。
      void loadConversation(selectedId)
      return
    }
    // 1. 中断已有请求，避免返回到欢迎页后被旧会话内容覆盖。
    abortRef.current?.abort()
    historyAbortRef.current?.abort()
    historyRequestRef.current += 1
    // 2. 重置消息相关状态，恢复新建对话的初始界面。
    setMessages([])
    setNextBeforeSequence(null)
    setHasMore(false)
    setHistoryLoading(false)
    setStreamError(null)
    setHistoryError(null)
    setOlderHistoryError(null)
  }, [loadConversation, selectedId])

  const cancelGenerationOnce = async (currentGenerationId: string) => {
    if (cancelledGenerationIdsRef.current.has(currentGenerationId)) {
      return
    }
    cancelledGenerationIdsRef.current.add(currentGenerationId)
    try {
      await cancelGeneration(currentGenerationId)
    } catch (error) {
      setStreamError(error instanceof Error ? error.message : '停止生成失败，请稍后重试')
    }
  }

  const cancelGenerationAndAbort = async (currentGenerationId: string) => {
    // 1. 先向后端发送取消请求，确保已创建的生成任务得到释放。
    await cancelGenerationOnce(currentGenerationId)
    // 2. 再中断仍在读取的 SSE，避免 META 前停止导致永远无法获知任务标识。
    abortRef.current?.abort()
  }

  const handleStreamEvent = (assistantId: string, event: ChatStreamEvent) => {
    if (event.type === 'META') {
      if (event.conversationId) {
        // 1. 新会话在收到 META 后才取得会话标识，供地址更新时识别当前流的归属。
        activeStreamConversationIdRef.current = event.conversationId
        setSearchParams({ conversation: event.conversationId }, { replace: true })
        setConversationAgents((current) => ({ ...current, [event.conversationId!]: current[event.conversationId!] ?? DEFAULT_CONVERSATION_AGENT }))
      }
      if (event.generationId) {
        generationIdRef.current = event.generationId
        if (stopRequestedRef.current) {
          void cancelGenerationAndAbort(event.generationId)
        }
      }
      return
    }
    if (event.type === 'TOKEN') {
      if (stopRequestedRef.current) {
        return
      }
      setMessages((current) => current.map((message) => message.messageId === assistantId
        ? { ...message, content: `${message.content}${event.content ?? ''}` } : message))
      return
    }
    if (event.type === 'ERROR') {
      if (stopRequestedRef.current) {
        return
      }
      setMessages((current) => current.map((message) => message.messageId === assistantId
        ? { ...message, status: 'FAILED', errorMessage: event.errorMessage ?? '生成失败，请重试' } : message))
      setStreamError(event.errorMessage ?? '生成失败，请重试')
      return
    }
    if (event.type === 'CANCELLED') {
      setMessages((current) => current.map((message) => message.messageId === assistantId
        ? { ...message, status: 'CANCELLED' } : message))
      return
    }
    if (event.type === 'COMPLETE') {
      setMessages((current) => current.map((message) => message.messageId === assistantId
        && message.status === 'GENERATING' ? { ...message, status: 'COMPLETED' } : message))
    }
  }

  const sendMessage = async (content = draft) => {
    const normalizedContent = content.trim()
    if (!normalizedContent || streaming) {
      return
    }
    const userMessage: TimelineMessage = {
      messageId: `local-${crypto.randomUUID()}`,
      sequence: Number.MAX_SAFE_INTEGER - 1,
      role: 'USER', status: 'COMPLETED', content: normalizedContent,
      createdTime: new Date().toISOString(), updatedTime: new Date().toISOString(), local: true,
    }
    const assistant = initialAssistant()
    const controller = new AbortController()
    abortRef.current = controller
    stopRequestedRef.current = false
    generationIdRef.current = null
    streamActiveRef.current = true
    activeStreamConversationIdRef.current = selectedId
    setDraft('')
    setMessages((current) => [...current, userMessage, assistant])
    setStreaming(true)
    setActiveAssistantId(assistant.messageId)
    setStreamError(null)
    try {
      await streamChat({ conversationId: selectedId ?? undefined, content: normalizedContent },
        (event) => handleStreamEvent(assistant.messageId, event), controller.signal)
      await refreshConversations()
    } catch (error) {
      if ((error as { name?: string }).name !== 'AbortError') {
        const errorMessage = error instanceof Error ? error.message : '生成失败，请重试'
        setMessages((current) => current.map((message) => message.messageId === assistant.messageId
          ? { ...message, status: 'FAILED', errorMessage } : message))
        setStreamError(errorMessage)
      }
    } finally {
      setStreaming(false)
      generationIdRef.current = null
      streamActiveRef.current = false
      activeStreamConversationIdRef.current = null
      setActiveAssistantId((current) => current === assistant.messageId ? null : current)
      abortRef.current = null
    }
  }

  const stopStreaming = async () => {
    // 1. 先在本地结束展示状态，避免网络取消过程阻塞界面反馈。
    if (activeAssistantId) {
      setMessages((current) => current.map((message) => message.messageId === activeAssistantId
        ? { ...message, status: 'CANCELLED' } : message))
    }
    stopRequestedRef.current = true
    // 2. 尚未收到 META 时继续读取流，待取得任务标识后再取消，避免遗漏后端生成任务。
    if (generationIdRef.current) {
      await cancelGenerationAndAbort(generationIdRef.current)
    }
  }

  const onComposerKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault()
      void sendMessage()
    }
  }

  return (
    <section className="flex h-full min-h-[560px] min-w-0 flex-col bg-background">
      <header className="flex h-11 shrink-0 items-center justify-between border-b border-border bg-card px-5">
        <div><p className="text-sm font-semibold text-foreground">{selectedId ? conversations.find((conversation) => conversation.conversationId === selectedId)?.title || '新对话' : '新对话'}</p><p className="mt-0.5 text-xs text-tertiary">智能问答工作台</p></div>
        <div className="flex items-center gap-4 text-xs text-secondary"><button type="button" className="flex items-center gap-1.5 hover:text-primary"><HelpCircle className="size-3.5" />帮助</button><button type="button" className="flex items-center gap-1.5 hover:text-primary"><Compass className="size-3.5" />我的工作区</button><span className="h-4 w-px bg-border" aria-hidden="true" /><span className="flex size-6 items-center justify-center rounded-full bg-tertiary/30 text-[10px] font-semibold text-secondary" aria-label="当前用户">N</span></div>
      </header>
      <div className="min-h-0 flex-1 overflow-y-auto bg-muted">
        <div className="mx-auto flex min-h-full w-full max-w-[880px] flex-col px-5 py-6 sm:px-10">
          {messages.length === 0 && !historyLoading && !historyError && <Welcome onSuggestion={setDraft} />}
          {historyLoading && messages.length === 0 && <TimelineSkeleton />}
          {historyError && <HistoryError message={historyError} onRetry={() => selectedId && void loadConversation(selectedId)} />}
          {hasMore && <div className="mb-5 text-center"><Button variant="ghost" size="sm" onClick={() => void loadOlderMessages()}><ChevronUp className="size-4" />加载更早消息</Button></div>}
          {olderHistoryError && <HistoryError message={olderHistoryError} onRetry={() => void loadOlderMessages()} />}
          {messages.map((message, index) => <MessageBubble key={message.messageId} message={message} onRetry={() => void sendMessage(findQuestionForRetry(messages, index))} />)}
          {streamError && <div role="status" className="mt-4 rounded-md border border-danger-light bg-danger-light px-4 py-3 text-sm text-danger">{streamError}</div>}
        </div>
      </div>
      <Composer draft={draft} streaming={streaming} onDraftChange={setDraft} onKeyDown={onComposerKeyDown} agent={activeAgent} onSend={() => void sendMessage()} onStop={() => void stopStreaming()} />
    </section>
  )
}

function Welcome({ onSuggestion }: { onSuggestion: (value: string) => void }) {
  const suggestions = [
    { title: '解读文档', description: '总结一份材料的要点', prompt: '请帮我解读这份文档，并总结其中的关键要点。', icon: FileText },
    { title: '检索知识库', description: '从已有资料中查找依据', prompt: '请从知识库中检索与我的问题相关的内容，并给出依据。', icon: BookOpen },
    { title: '创建提示词', description: '沉淀可复用的工作模板', prompt: '请帮我创建一份可复用的提示词模板。', icon: Sparkles },
  ]
  return <div className="m-auto w-full max-w-[760px] text-center"><div className="mx-auto mb-5 flex size-10 items-center justify-center rounded-lg bg-primary-light text-primary"><Sparkles className="size-5" /></div><h1 className="text-2xl font-semibold tracking-[-0.03em] text-foreground">你好，今天想做什么？</h1><p className="mt-3 text-sm text-secondary">通过模型与知识库，让复杂信息转化为清晰答案。</p><div className="mt-8 grid gap-3 text-left sm:grid-cols-3">{suggestions.map(({ description, icon: Icon, prompt, title }) => <button key={title} type="button" aria-label={title} onClick={() => onSuggestion(prompt)} className="group rounded-lg border border-border bg-card p-4 transition-colors hover:border-primary/60 hover:bg-muted"><span className="mb-4 flex size-8 items-center justify-center rounded-md bg-primary-light text-primary"><Icon className="size-4" /></span><p className="text-sm font-medium text-foreground">{title}</p><p className="mt-1 text-xs text-tertiary">{description}</p></button>)}</div></div>
}

function Composer({ draft, streaming, agent: _agent, onDraftChange, onKeyDown, onSend, onStop }: {
  draft: string; streaming: boolean; agent: ConversationAgentMeta; onDraftChange: (value: string) => void; onKeyDown: (event: KeyboardEvent<HTMLTextAreaElement>) => void; onSend: () => void; onStop: () => void
}) {
  return (
    <div className="shrink-0 bg-muted px-5 pb-4 pt-2 sm:px-10">
      <div className="mx-auto flex min-h-[122px] max-h-[300px] w-full max-w-[880px] flex-col overflow-hidden rounded-[24px] border border-border bg-card shadow-xs transition-all focus-within:border-primary focus-within:shadow-[0_0_0_3px_rgba(51,112,255,0.12)]">
        <div className="min-h-0 flex-1 overflow-hidden px-4 pt-4">
          <Textarea
            aria-label="消息内容"
            value={draft}
            onChange={(event) => onDraftChange(event.target.value)}
            onKeyDown={onKeyDown}
            disabled={streaming}
            placeholder="输入你的问题，AI 将结合知识库回答…"
            className="min-h-[24px] w-full resize-none border-0 bg-transparent p-0 text-sm leading-6 shadow-none placeholder:text-tertiary focus-visible:ring-0 [field-sizing:content] max-h-[200px] overflow-y-auto [scrollbar-width:thin] [&::-webkit-scrollbar]:w-1.5 [&::-webkit-scrollbar-thumb]:rounded-full [&::-webkit-scrollbar-thumb]:bg-border"
          />
        </div>
        <div className="flex shrink-0 items-center justify-between gap-2 px-3 pb-3 pt-2">
          <div className="flex items-center gap-1">
            <button type="button" className="flex h-8 items-center gap-1.5 rounded-full px-3 text-xs text-secondary transition-colors hover:bg-muted">
              <Sparkles className="size-3.5 text-primary" />
              Qwen 3
              <ChevronDown className="size-3 text-tertiary" />
            </button>
            <button type="button" aria-label="添加附件" className="flex size-8 items-center justify-center rounded-full text-tertiary transition-colors hover:bg-muted hover:text-secondary">
              <Paperclip className="size-4" />
            </button>
          </div>
          {streaming ? (
            <button
              type="button"
              aria-label="停止生成"
              onClick={onStop}
              className="flex size-9 items-center justify-center rounded-full border border-border bg-card text-danger transition-colors hover:bg-danger-light"
            >
              <CircleStop className="size-4" />
            </button>
          ) : (
            <button
              type="button"
              aria-label="发送消息"
              disabled={!draft.trim()}
              onClick={onSend}
              className="flex size-9 items-center justify-center rounded-full bg-primary pl-0.5 text-primary-foreground transition-colors hover:bg-primary/90 disabled:bg-muted disabled:text-tertiary"
            >
              <Send className="size-4" />
            </button>
          )}
        </div>
      </div>
      <p className="mx-auto mt-2 w-full max-w-[880px] px-2 text-center text-[11px] text-tertiary">Enter 发送 · Shift + Enter 换行</p>
    </div>
  )
}

function MessageBubble({ message, onRetry }: { message: TimelineMessage; onRetry: () => void }) {
  const isUser = message.role === 'USER'
  if (isUser) {
    return (
      <article className="group mb-6 flex justify-end">
        <div className="max-w-[80%]">
          <div className="whitespace-pre-wrap break-words rounded-xl rounded-br-sm bg-primary px-3.5 py-2 text-left text-sm leading-6 text-primary-foreground">
            {message.content}
          </div>
          <div className="mt-1 flex items-center justify-end gap-1.5 opacity-0 transition-opacity group-hover:opacity-100">
            <span className="text-[10px] text-tertiary">{formatMessageTime(message.createdTime)}</span>
            <CopyButton text={message.content} />
          </div>
          {message.status === 'FAILED' && (
            <p className="mt-1.5 pr-1 text-xs text-danger">{message.errorMessage || '生成失败'}</p>
          )}
        </div>
      </article>
    )
  }

  return (
    <article className="group mb-6 flex gap-3">
      <span className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-md bg-primary text-xs font-bold text-primary-foreground" aria-hidden="true">
        N
      </span>
      <div className="min-w-0 max-w-[86%]">
        <div className="mb-1 flex items-center gap-2">
          <span className="text-xs font-medium text-secondary">NexaRAG</span>
          {message.status === 'FAILED' && (
            <button
              type="button"
              onClick={onRetry}
              className="flex items-center gap-1 rounded px-1.5 py-0.5 text-[10px] text-primary transition-colors hover:bg-primary-light"
            >
              <RefreshCw className="size-3" />
              重试
            </button>
          )}
        </div>
        <div className="text-sm leading-6">
          <AssistantMarkdown content={message.content || (message.status === 'GENERATING' ? '正在生成…' : '')} status={message.status} />
        </div>
        <div className="mt-1 flex items-center gap-1.5 opacity-0 transition-opacity group-hover:opacity-100">
          <span className="text-[10px] text-tertiary">{formatMessageTime(message.createdTime)}</span>
          {message.status === 'COMPLETED' && message.content && (
            <CopyButton text={message.content} />
          )}
        </div>
        {message.status === 'FAILED' && (
          <p className="mt-1.5 text-xs text-danger">{message.errorMessage || '生成失败'}</p>
        )}
        {message.status === 'CANCELLED' && (
          <p className="mt-1.5 text-xs text-tertiary">已停止生成</p>
        )}
      </div>
    </article>
  )
}

/** 悬停展示的复制图标按钮，点击后短暂切换为对勾。 */
function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false)
  const handleCopy = () => {
    void navigator.clipboard?.writeText(text)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }
  return (
    <button
      type="button"
      aria-label={copied ? '已复制' : '复制'}
      onClick={handleCopy}
      className="rounded p-1 text-tertiary transition-colors hover:text-primary"
    >
      {copied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />}
    </button>
  )
}

function formatMessageTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function TimelineSkeleton() { return <div className="space-y-5"><Skeleton className="h-20 w-3/4" /><Skeleton className="ml-auto h-16 w-2/3" /><Skeleton className="h-28 w-4/5" /></div> }
function HistoryError({ message, onRetry }: { message: string; onRetry: () => void }) { return <div role="status" className="mb-4 rounded-md border border-danger-light bg-danger-light px-4 py-3 text-sm text-danger"><p>{message}</p><Button variant="ghost" size="sm" className="mt-2" onClick={onRetry}>重试加载历史</Button></div> }
function findQuestionForRetry(messages: TimelineMessage[], failedMessageIndex: number) { return messages.slice(0, failedMessageIndex).reverse().find((message) => message.role === 'USER')?.content ?? '' }

function readConversationAgents(): Record<string, ConversationAgentMeta> {
  try {
    const value = localStorage.getItem(CONVERSATION_AGENT_STORAGE_KEY)
    if (!value) {
      return {}
    }
    const parsed = JSON.parse(value) as Record<string, ConversationAgentMeta>
    return Object.fromEntries(Object.entries(parsed).filter(([, metadata]) => metadata?.agentType === 'rag'))
  } catch {
    return {}
  }
}
