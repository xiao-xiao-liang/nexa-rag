import { useCallback, useEffect, useRef, useState, type KeyboardEvent } from 'react'
import {
  Bot, ChevronUp, CircleStop, Database, Menu, Plane, Plus, RefreshCw, Send, Sparkles,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { cancelGeneration, streamChat, type ChatStreamEvent } from '@/features/chat/api/chat-api'
import { DEFAULT_CONVERSATION_AGENT, type ConversationAgentMeta } from '@/features/agents/agent-registry'
import {
  getConversationMessages, getConversations, type ConversationListItem, type ConversationMessage,
} from '@/features/conversations/api/conversation-api'
import { cn } from '@/lib/utils'

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
  const [conversations, setConversations] = useState<ConversationListItem[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [messages, setMessages] = useState<TimelineMessage[]>([])
  const [nextBeforeSequence, setNextBeforeSequence] = useState<number | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [draft, setDraft] = useState('')
  const [conversationLoading, setConversationLoading] = useState(true)
  const [conversationError, setConversationError] = useState<string | null>(null)
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
  const cancelledGenerationIdsRef = useRef(new Set<string>())
  const historyAbortRef = useRef<AbortController | null>(null)
  const historyRequestRef = useRef(0)
  const activeAgent = selectedId ? conversationAgents[selectedId] ?? DEFAULT_CONVERSATION_AGENT : DEFAULT_CONVERSATION_AGENT

  useEffect(() => {
    localStorage.setItem(CONVERSATION_AGENT_STORAGE_KEY, JSON.stringify(conversationAgents))
  }, [conversationAgents])

  const loadConversations = useCallback(async () => {
    setConversationLoading(true)
    setConversationError(null)
    try {
      const page = await getConversations()
      setConversations(page.records)
    } catch (error) {
      setConversationError(error instanceof Error ? error.message : '会话加载失败，请稍后重试')
    } finally {
      setConversationLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadConversations()
    return () => {
      abortRef.current?.abort()
      historyAbortRef.current?.abort()
    }
  }, [loadConversations])

  const selectConversation = useCallback(async (conversationId: string) => {
    abortRef.current?.abort()
    historyAbortRef.current?.abort()
    const requestId = ++historyRequestRef.current
    const controller = new AbortController()
    historyAbortRef.current = controller
    setSelectedId(conversationId)
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

  const createConversation = () => {
    abortRef.current?.abort()
    historyAbortRef.current?.abort()
    historyRequestRef.current += 1
    setSelectedId(null)
    setMessages([])
    setNextBeforeSequence(null)
    setHasMore(false)
    setStreamError(null)
    setHistoryError(null)
    setOlderHistoryError(null)
  }

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
        setSelectedId(event.conversationId)
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
    setDraft('')
    setMessages((current) => [...current, userMessage, assistant])
    setStreaming(true)
    setActiveAssistantId(assistant.messageId)
    setStreamError(null)
    try {
      await streamChat({ conversationId: selectedId ?? undefined, content: normalizedContent },
        (event) => handleStreamEvent(assistant.messageId, event), controller.signal)
      await loadConversations()
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

  const sidebar = (
    <aside className="flex h-full w-full flex-col bg-card">
      <div className="flex items-center justify-between border-b px-4 py-4">
        <div className="flex items-center gap-2 font-semibold"><Sparkles className="size-5 text-primary" aria-hidden="true" />Nexa AI</div>
        <Button variant="outline" size="icon" aria-label="新建会话" onClick={createConversation}><Plus className="size-4" /></Button>
      </div>
      <div className="px-3 pb-2 pt-4 text-xs font-medium text-muted-foreground">会话</div>
      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-4">
        {conversationLoading && <ConversationSkeleton />}
        {conversationError && <SidebarError message={conversationError} onRetry={() => void loadConversations()} />}
        {!conversationLoading && !conversationError && conversations.length === 0 && (
          <p className="px-3 py-8 text-center text-sm text-muted-foreground">暂时没有会话</p>
        )}
        {conversations.map((conversation) => (
          <button key={conversation.conversationId} type="button" aria-label={`打开会话 ${conversation.title || '未命名会话'}`} onClick={() => void selectConversation(conversation.conversationId)}
            className={cn('mb-1 w-full rounded-xl px-3 py-2.5 text-left transition-colors hover:bg-muted', selectedId === conversation.conversationId && 'bg-blue-50 text-primary')}>
            <span className="block truncate text-sm font-medium">{conversation.title || '未命名会话'}</span>
            <span className="mt-1 block text-xs text-muted-foreground">{formatTime(conversation.updatedTime)}</span>
          </button>
        ))}
      </div>
    </aside>
  )

  return (
    <TooltipProvider delayDuration={300}>
      <section className="flex h-dvh min-h-[560px] min-w-0 flex-1 bg-background text-foreground">
        <div className="hidden w-72 shrink-0 border-r md:block">{sidebar}</div>
        <section className="flex min-w-0 flex-1 flex-col">
          <header className="flex h-16 items-center gap-3 border-b bg-card px-4 md:px-8">
            <Sheet><SheetTrigger asChild><Button variant="ghost" size="icon" className="md:hidden" aria-label="打开会话列表"><Menu className="size-5" /></Button></SheetTrigger>
              <SheetContent side="left" className="p-0"><SheetHeader className="sr-only"><SheetTitle>会话列表</SheetTitle><SheetDescription>选择或创建会话</SheetDescription></SheetHeader>{sidebar}</SheetContent>
            </Sheet>
            <div><p className="text-sm font-semibold">{selectedId ? 'RAG 对话' : '新对话'}</p><p className="text-xs text-muted-foreground">知识库问答</p></div>
          </header>
          <div className="min-h-0 flex-1 overflow-y-auto">
            <div className="mx-auto flex min-h-full w-full max-w-4xl flex-col px-4 py-8 sm:px-8">
              {messages.length === 0 && !historyLoading && !historyError && <Welcome />}
              {historyLoading && messages.length === 0 && <TimelineSkeleton />}
              {historyError && <HistoryError message={historyError} onRetry={() => selectedId && void selectConversation(selectedId)} />}
              {hasMore && <div className="mb-5 text-center"><Button variant="ghost" size="sm" onClick={() => void loadOlderMessages()}><ChevronUp className="size-4" />加载更早消息</Button></div>}
              {olderHistoryError && <HistoryError message={olderHistoryError} onRetry={() => void loadOlderMessages()} />}
              {messages.map((message, index) => <MessageBubble key={message.messageId} message={message}
                onRetry={() => void sendMessage(findQuestionForRetry(messages, index))} />)}
              {streamError && <div role="status" className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{streamError}</div>}
            </div>
          </div>
          <Composer draft={draft} streaming={streaming} onDraftChange={setDraft} onKeyDown={onComposerKeyDown}
            agent={activeAgent} onSend={() => void sendMessage()} onStop={() => void stopStreaming()} />
        </section>
      </section>
    </TooltipProvider>
  )
}

function Welcome() {
  return <div className="m-auto max-w-xl text-center"><div className="mx-auto mb-5 flex size-12 items-center justify-center rounded-2xl bg-blue-50 text-primary"><Bot className="size-6" /></div><h1 className="text-2xl font-semibold tracking-tight">开始一段 RAG 对话</h1><p className="mt-3 text-sm leading-6 text-muted-foreground">基于已接入的知识库，为你查找、归纳并回答问题。</p></div>
}

function Composer({ draft, streaming, agent: _agent, onDraftChange, onKeyDown, onSend, onStop }: {
  draft: string; streaming: boolean; agent: ConversationAgentMeta; onDraftChange: (value: string) => void; onKeyDown: (event: KeyboardEvent<HTMLTextAreaElement>) => void; onSend: () => void; onStop: () => void
}) {
  return <div className="border-t bg-background px-4 pb-5 pt-3 sm:px-8"><div className="mx-auto max-w-4xl rounded-[28px] border border-blue-200 bg-card p-2 shadow-sm transition-shadow focus-within:border-blue-400 focus-within:ring-4 focus-within:ring-blue-100">
    <Textarea aria-label="消息内容" value={draft} onChange={(event) => onDraftChange(event.target.value)} onKeyDown={onKeyDown} disabled={streaming} placeholder="发送消息，向知识库提问…" className="min-h-24 resize-none border-0 px-3 py-2 shadow-none focus-visible:ring-0" />
    <div className="flex items-center justify-between gap-2 px-1 pb-1"><div className="flex min-w-0 items-center gap-1.5"><FutureAgent icon={<Database className="size-4" />} label="数据分析" /><FutureAgent icon={<Plane className="size-4" />} label="智能差旅" /></div>
      {streaming ? <Button type="button" variant="outline" size="icon" aria-label="停止生成" onClick={onStop}><CircleStop className="size-4" /></Button> : <Button type="button" size="icon" aria-label="发送消息" disabled={!draft.trim()} onClick={onSend}><Send className="size-4" /></Button>}
    </div>
  </div><p className="mx-auto mt-2 max-w-4xl px-2 text-xs text-muted-foreground">Enter 发送，Shift + Enter 换行</p></div>
}

function FutureAgent({ icon, label }: { icon: React.ReactNode; label: string }) {
  return <Tooltip><TooltipTrigger asChild><button type="button" disabled className="flex items-center gap-1 rounded-lg px-2 py-1 text-xs text-muted-foreground disabled:cursor-not-allowed">{icon}{label}</button></TooltipTrigger><TooltipContent>即将推出</TooltipContent></Tooltip>
}

function MessageBubble({ message, onRetry }: { message: TimelineMessage; onRetry: () => void }) {
  const isUser = message.role === 'USER'
  return <article className={cn('mb-6 flex gap-3', isUser && 'justify-end')}><div className={cn('max-w-[86%] rounded-2xl px-4 py-3 text-sm leading-7', isUser ? 'bg-primary text-primary-foreground' : 'border bg-card')}>
    {!isUser && <div className="mb-1 flex items-center gap-1.5 text-xs font-medium text-primary"><Sparkles className="size-3.5" />RAG</div>}
    <p className="whitespace-pre-wrap break-words">{message.content || (message.status === 'GENERATING' ? '正在生成…' : '')}</p>
    {message.status === 'FAILED' && <div className="mt-2 flex items-center gap-2 text-xs text-red-600"><span>{message.errorMessage || '生成失败'}</span><Button variant="ghost" size="sm" onClick={onRetry}><RefreshCw className="size-3.5" />重试</Button></div>}
    {message.status === 'CANCELLED' && <p className="mt-2 text-xs text-muted-foreground">已停止生成</p>}
  </div></article>
}

function ConversationSkeleton() { return <div className="space-y-2 px-2">{Array.from({ length: 5 }, (_, index) => <Skeleton key={index} className="h-14 w-full" />)}</div> }
function TimelineSkeleton() { return <div className="space-y-5"><Skeleton className="h-20 w-3/4" /><Skeleton className="ml-auto h-16 w-2/3" /><Skeleton className="h-28 w-4/5" /></div> }
function SidebarError({ message, onRetry }: { message: string; onRetry: () => void }) { return <div className="mx-2 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-700"><p>{message}</p><Button variant="ghost" size="sm" className="mt-2" onClick={onRetry}><RefreshCw className="size-3.5" />重试</Button></div> }
function HistoryError({ message, onRetry }: { message: string; onRetry: () => void }) { return <div role="status" className="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"><p>{message}</p><Button variant="ghost" size="sm" className="mt-2" onClick={onRetry}>重试加载历史</Button></div> }
function formatTime(time: string | null) { if (!time) return '暂无消息'; const date = new Date(time); return Number.isNaN(date.getTime()) ? '刚刚' : date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' }) }
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
