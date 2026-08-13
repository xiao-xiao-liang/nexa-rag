import { LibraryBig, MessageSquareText, Settings, SlidersHorizontal, type LucideIcon } from 'lucide-react'
import { NavLink } from 'react-router-dom'
import { cn } from '@/lib/utils'

export interface IconRailItem {
  key: string
  label: string
  icon: LucideIcon
  to: string
}

export const iconRailItems: IconRailItem[] = [
  { key: 'chat', label: '对话', icon: MessageSquareText, to: '/chat' },
  { key: 'knowledge', label: '知识库', icon: LibraryBig, to: '/knowledge-base' },
  { key: 'models', label: '模型管理', icon: SlidersHorizontal, to: '/models' },
  { key: 'settings', label: '设置', icon: Settings, to: '/settings' },
]

/** 飞书风格 48px 图标栏。 */
export function IconRail({ activeKey }: { activeKey: string }) {
  return (
    <nav aria-label="主导航" className="flex w-12 shrink-0 flex-col items-center border-r border-border bg-card py-2">
      <NavLink
        to="/chat"
        aria-label="NexaRAG 首页"
        className="mb-3 flex size-7 items-center justify-center rounded-md bg-primary text-sm font-bold text-primary-foreground"
      >
        N
      </NavLink>
      {iconRailItems.map(({ key, label, icon: Icon, to }) => {
        const isActive = key === activeKey
        return (
          <NavLink
            key={key}
            to={to}
            aria-label={label}
            title={label}
            className={cn(
              'mb-1 flex size-9 items-center justify-center rounded-md transition-colors',
              isActive ? 'bg-primary-light text-primary' : 'text-tertiary hover:bg-muted hover:text-secondary',
            )}
          >
            <Icon className="size-[18px]" />
          </NavLink>
        )
      })}
      <div className="flex-1" />
      <span className="mt-3 flex size-6 items-center justify-center rounded-full bg-tertiary/30 text-[10px] font-semibold text-secondary" aria-label="当前用户">
        N
      </span>
    </nav>
  )
}
