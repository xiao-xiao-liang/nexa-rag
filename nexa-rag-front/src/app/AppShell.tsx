import { Outlet } from 'react-router-dom'
import { IconRail } from '@/components/layout/IconRail'
import { TopBar } from '@/components/layout/TopBar'
import { TooltipProvider } from '@/components/ui/tooltip'
import { ConversationPanel } from '@/features/conversations/ConversationPanel'
import { ConversationNavigationProvider } from '@/features/conversations/ConversationNavigationContext'

/** AI 中台页面外壳：顶栏 + 图标栏 + 会话面板 + 内容区（Task 14 起按路由分发模块面板）。 */
export function AppShell() {
  return (
    <TooltipProvider delayDuration={250}>
      <ConversationNavigationProvider>
        <div className="flex h-dvh min-h-[560px] flex-col overflow-hidden bg-background text-foreground">
          <TopBar />
          <div className="flex min-h-0 flex-1">
            <IconRail activeKey="chat" />
            <ConversationPanel />
            <main className="relative min-w-0 flex-1">
              <Outlet />
            </main>
          </div>
        </div>
      </ConversationNavigationProvider>
    </TooltipProvider>
  )
}
