import { describe, expect, it } from 'vitest'
import { DEFAULT_CONVERSATION_AGENT, getAgentDefinition } from './agent-registry'

describe('前端 Agent 注册表', () => {
  it('默认会话应使用 RAG，且定义预留事件渲染扩展边界', () => {
    const definition = getAgentDefinition(DEFAULT_CONVERSATION_AGENT.agentType)

    expect(DEFAULT_CONVERSATION_AGENT).toEqual({ agentType: 'rag' })
    expect(definition?.id).toBe('rag')
    expect('renderEvent' in (definition ?? {})).toBe(true)
  })
})
