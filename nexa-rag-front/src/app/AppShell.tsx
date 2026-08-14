import { Outlet, useLocation } from 'react-router-dom'
import { IconRail } from '@/components/layout/IconRail'
import { ModulePanel } from '@/components/layout/ModulePanel'
import { ResizablePanel } from '@/components/layout/ResizablePanel'
import { TooltipProvider } from '@/components/ui/tooltip'
import { ConversationNavigationProvider } from '@/features/conversations/ConversationNavigationContext'

function resolveActiveKey(pathname: string): string {
  if (pathname.startsWith('/knowledge-base')) return 'knowledge'
  if (pathname.startsWith('/models') || pathname.startsWith('/prompts')) return 'models'
  if (pathname.startsWith('/settings')) return 'settings'
  return 'chat'
}

/** AI 中台页面外壳：顶栏 + 图标栏 + 模块面板 + 内容区。 */
export function AppShell() {
  const { pathname } = useLocation()
  return (
    <TooltipProvider delayDuration={250}>
      <ConversationNavigationProvider>
        <div className="flex h-dvh min-h-[560px] flex-col overflow-hidden bg-background text-foreground">
          <div className="flex min-h-0 flex-1">
            <IconRail activeKey={resolveActiveKey(pathname)} />
            {resolveActiveKey(pathname) !== 'settings' && (
              <ResizablePanel>
                <ModulePanel />
              </ResizablePanel>
            )}
            <main className="relative min-w-0 flex-1">
              <Outlet />
            </main>
          </div>
        </div>
      </ConversationNavigationProvider>
    </TooltipProvider>
  )
}
