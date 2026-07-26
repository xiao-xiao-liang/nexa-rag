import { request } from '@/shared/api/client'
import type { CursorPageVO, PageVO } from '@/shared/api/types'

/** 会话状态。 */
export type ConversationStatus = 'ACTIVE' | 'ARCHIVED' | 'DELETED'

/** 消息角色。 */
export type MessageRole = 'USER' | 'ASSISTANT'

/** 消息处理状态。 */
export type MessageStatus = 'COMPLETED' | 'GENERATING' | 'FAILED' | 'CANCELLED'

/** 会话列表项。 */
export interface ConversationListItem {
  conversationId: string
  title: string | null
  status: ConversationStatus
  lastMessageTime: string | null
  createdTime: string
  updatedTime: string
}

/** 历史消息项。 */
export interface ConversationMessage {
  messageId: string
  sequence: number
  role: MessageRole
  status: MessageStatus
  content: string
  createdTime: string
  updatedTime: string
}

/** 会话列表查询参数。 */
export interface GetConversationsParams {
  current?: number
  size?: number
}

/** 历史消息查询参数。 */
export interface GetConversationMessagesParams {
  beforeSequence?: number
  size?: number
  signal?: AbortSignal
}

/** 查询当前用户的会话列表。 */
export function getConversations(
  { current = 1, size = 20 }: GetConversationsParams = {},
): Promise<PageVO<ConversationListItem>> {
  const params = new URLSearchParams({ current: String(current), size: String(size) })
  return request<PageVO<ConversationListItem>>(`/api/conversations?${params.toString()}`)
}

/** 查询指定会话的历史消息。 */
export function getConversationMessages(
  conversationId: string,
  { beforeSequence, size = 50, signal }: GetConversationMessagesParams = {},
): Promise<CursorPageVO<ConversationMessage>> {
  const params = new URLSearchParams()
  if (beforeSequence !== undefined) {
    params.set('beforeSequence', String(beforeSequence))
  }
  params.set('size', String(size))
  return request<CursorPageVO<ConversationMessage>>(
    `/api/conversations/${encodeURIComponent(conversationId)}/messages?${params.toString()}`,
    signal ? { signal } : undefined,
  )
}
