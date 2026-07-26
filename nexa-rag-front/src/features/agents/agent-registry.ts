/** 当前前端已支持的 Agent 类型。 */
export type AgentType = 'rag'

/** 会话在前端保存的 Agent 元数据，当前不会传入后端。 */
export interface ConversationAgentMeta {
  agentType: AgentType
  agentId?: string
}

/** 供 Agent 自定义事件渲染逻辑使用的中间模型。 */
export interface AgentEventRenderModel {
  kind: 'message' | 'status'
  content: string
}

/** 前端 Agent 定义；renderEvent 为后续 Agent 的事件渲染扩展边界。 */
export interface AgentDefinition {
  id: string
  type: AgentType
  label: string
  renderEvent?: (event: { type: string; content?: string | null }) => AgentEventRenderModel | null
}

/** RAG 是当前唯一可用的默认 Agent。 */
export const DEFAULT_CONVERSATION_AGENT: ConversationAgentMeta = { agentType: 'rag' }

const AGENT_DEFINITIONS: readonly AgentDefinition[] = [
  { id: 'rag', type: 'rag', label: 'RAG', renderEvent: undefined },
]

/** 按 Agent 类型查询前端定义。 */
export function getAgentDefinition(type: AgentType): AgentDefinition | undefined {
  return AGENT_DEFINITIONS.find((definition) => definition.type === type)
}
