import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import {
  createConversation as createApi,
  deleteConversation as deleteApi,
  getConversations,
  updateConversation as updateApi,
  type ConversationListItem,
} from './api/conversation-api'

const PINNED_CONVERSATIONS_KEY = 'nexa-rag.pinned-conversations'

interface ConversationNavigationValue {
  conversations: ConversationListItem[]
  pinnedIds: string[]
  loading: boolean
  error: string | null
  refresh: () => Promise<void>
  createConversation: (title?: string) => Promise<ConversationListItem>
  renameConversation: (conversationId: string, title: string) => Promise<void>
  deleteConversation: (conversationId: string) => Promise<void>
  togglePinConversation: (conversationId: string) => void
}

const ConversationNavigationContext = createContext<ConversationNavigationValue | null>(null)

/** 为页面外壳和对话工作区提供同一份会话列表数据与置顶状态。 */
export function ConversationNavigationProvider({ children }: { children: ReactNode }) {
  const [conversations, setConversations] = useState<ConversationListItem[]>([])
  const [pinnedIds, setPinnedIds] = useState<string[]>(() => {
    try {
      const stored = localStorage.getItem(PINNED_CONVERSATIONS_KEY)
      return stored ? (JSON.parse(stored) as string[]) : []
    } catch {
      return []
    }
  })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    try {
      localStorage.setItem(PINNED_CONVERSATIONS_KEY, JSON.stringify(pinnedIds))
    } catch {
      // 本地存储异常防护
    }
  }, [pinnedIds])

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const page = await getConversations()
      setConversations(page.records)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '会话加载失败，请稍后重试')
    } finally {
      setLoading(false)
    }
  }, [])

  const createConversation = useCallback(async (title?: string): Promise<ConversationListItem> => {
    const newItem = await createApi({ title })
    setConversations((prev) => [newItem, ...prev.filter((item) => item.conversationId !== newItem.conversationId)])
    return newItem
  }, [])

  const renameConversation = useCallback(async (conversationId: string, title: string): Promise<void> => {
    await updateApi(conversationId, { title })
    setConversations((prev) =>
      prev.map((item) => (item.conversationId === conversationId ? { ...item, title } : item)),
    )
  }, [])

  const deleteConversation = useCallback(async (conversationId: string): Promise<void> => {
    await deleteApi(conversationId)
    setConversations((prev) => prev.filter((item) => item.conversationId !== conversationId))
    setPinnedIds((prev) => prev.filter((id) => id !== conversationId))
  }, [])

  const togglePinConversation = useCallback((conversationId: string) => {
    setPinnedIds((prev) =>
      prev.includes(conversationId) ? prev.filter((id) => id !== conversationId) : [...prev, conversationId],
    )
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const value = useMemo(
    () => ({
      conversations,
      pinnedIds,
      loading,
      error,
      refresh,
      createConversation,
      renameConversation,
      deleteConversation,
      togglePinConversation,
    }),
    [conversations, pinnedIds, error, loading, refresh, createConversation, renameConversation, deleteConversation, togglePinConversation],
  )

  return <ConversationNavigationContext.Provider value={value}>{children}</ConversationNavigationContext.Provider>
}

/** 读取全局会话导航数据与置顶控制。 */
export function useConversationNavigation() {
  const context = useContext(ConversationNavigationContext)
  if (!context) {
    throw new Error('会话导航上下文未初始化')
  }
  return context
}
