import type { ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

interface EmptyStateProps {
  title: string
  description?: string
  icon?: LucideIcon
  action?: ReactNode
  className?: string
}

/** 飞书风格空状态：浅色图标占位 + 标题 + 说明 + 可选操作按钮。 */
export function EmptyState({ title, description, icon: Icon, action, className }: EmptyStateProps) {
  return (
    <div className={cn('flex flex-col items-center justify-center px-6 py-12 text-center', className)}>
      <span className="flex size-11 items-center justify-center rounded-lg bg-muted text-tertiary">
        {Icon ? <Icon className="size-5" /> : <span className="flex items-end gap-1" aria-hidden="true">
          <span className="h-2 w-2 rounded-[3px] bg-border" />
          <span className="h-3.5 w-2 rounded-[3px] bg-border" />
          <span className="h-5 w-2 rounded-[3px] bg-border" />
        </span>}
      </span>
      <p className="mt-3 text-sm font-medium text-foreground">{title}</p>
      {description && <p className="mt-1 max-w-sm text-xs leading-relaxed text-tertiary">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}
