import { LibraryBig, MessageSquare, Sparkles } from 'lucide-react'
import { NavLink, Outlet } from 'react-router-dom'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'

const navigation = [
  { to: '/chat', label: '对话', icon: MessageSquare },
  { to: '/knowledge-base', label: '知识库', icon: LibraryBig },
]

/** AI 中台页面外壳，提供全局模块导航与内容出口。 */
export function AppShell() {
  return (
    <TooltipProvider delayDuration={300}>
      <div className="flex h-dvh min-h-[560px] bg-background">
        <nav aria-label="全局导航" className="flex w-14 shrink-0 flex-col items-center border-r border-slate-800 bg-slate-950 py-3 text-slate-400">
          <div className="mb-6 flex size-8 items-center justify-center rounded-xl bg-blue-500 text-white" aria-label="Nexa AI">
            <Sparkles className="size-4" aria-hidden="true" />
          </div>
          <div className="flex flex-col gap-3">
            {navigation.map(({ to, label, icon: Icon }) => (
              <Tooltip key={to}>
                <TooltipTrigger asChild>
                  <NavLink to={to} aria-label={label} className={({ isActive }) => cn(
                    'flex size-9 items-center justify-center rounded-xl transition-colors hover:bg-slate-800 hover:text-white',
                    isActive && 'bg-blue-500 text-white',
                  )}>
                    <Icon className="size-4.5" aria-hidden="true" />
                  </NavLink>
                </TooltipTrigger>
                <TooltipContent side="right">{label}</TooltipContent>
              </Tooltip>
            ))}
          </div>
        </nav>
        <Outlet />
      </div>
    </TooltipProvider>
  )
}
