import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { getConversations, type ConversationListItem } from './api/conversation-api'

interface ConversationNavigationValue {
  conversations: ConversationListItem[]
  loading: boolean
  error: string | null
  refresh: () => Promise<void>
}

const ConversationNavigationContext = createContext<ConversationNavigationValue | null>(null)

/** 为页面外壳和对话工作区提供同一份会话列表数据。 */
export function ConversationNavigationProvider({ children }: { children: ReactNode }) {
  const [conversations, setConversations] = useState<ConversationListItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

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

  useEffect(() => {
    void refresh()
  }, [refresh])

  const value = useMemo(() => ({ conversations, loading, error, refresh }), [conversations, error, loading, refresh])

  return <ConversationNavigationContext.Provider value={value}>{children}</ConversationNavigationContext.Provider>
}

/** 读取全局会话导航数据。 */
export function useConversationNavigation() {
  const context = useContext(ConversationNavigationContext)
  if (!context) {
    throw new Error('会话导航上下文未初始化')
  }
  return context
}
