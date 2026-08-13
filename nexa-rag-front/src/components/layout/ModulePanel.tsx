import { useLocation } from 'react-router-dom'
import { ConversationPanel } from '@/features/conversations/ConversationPanel'
import { KnowledgePanel } from '@/features/knowledge-base/KnowledgePanel'
import { ModelPanel } from '@/features/models/ModelPanel'

/** 模块面板分发：按当前路由渲染对应面板。 */
export function ModulePanel() {
  const { pathname } = useLocation()
  if (pathname.startsWith('/knowledge-base')) return <KnowledgePanel />
  if (pathname.startsWith('/models') || pathname.startsWith('/prompts')) return <ModelPanel />
  if (pathname.startsWith('/settings')) return null
  return <ConversationPanel />
}
