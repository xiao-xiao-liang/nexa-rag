import { NavLink } from 'react-router-dom'
import { cn } from '@/lib/utils'

interface SidebarNavItemProps {
  to: string
  end?: boolean
  label: string
  badge?: string
  isActive?: (isActive: boolean) => boolean
}

/** 飞书风格侧栏导航项：active 带左侧指示条，支持 New 徽标。 */
export function SidebarNavItem({ to, end, label, badge, isActive }: SidebarNavItemProps) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive: navActive }) => {
        const active = isActive ? isActive(navActive) : navActive
        return cn(
          'relative mx-2 block rounded px-3 py-1.5 text-sm transition-colors',
          active ? 'bg-primary-light font-medium text-primary' : 'text-secondary hover:bg-muted hover:text-foreground',
        )
      }}
    >
      {({ isActive: navActive }) => {
        const active = isActive ? isActive(navActive) : navActive
        return (
          <span className="flex items-center">
            {active && <span className="absolute left-0 top-1/2 h-3.5 w-0.5 -translate-y-1/2 rounded-full bg-primary" />}
            {label}
            {badge && <span className="ml-1.5 rounded-full border border-border bg-card px-1.5 text-[10px] leading-4 text-tertiary">{badge}</span>}
          </span>
        )
      }}
    </NavLink>
  )
}
