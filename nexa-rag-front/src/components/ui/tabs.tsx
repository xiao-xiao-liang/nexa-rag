import { cn } from '@/lib/utils'

export interface TabItem<T extends string> {
  value: T
  label: string
}

export interface TabsProps<T extends string> {
  items: TabItem<T>[]
  value: T
  onChange: (value: T) => void
  className?: string
}

/** 飞书风格下划线 Tab。 */
export function Tabs<T extends string>({ items, value, onChange, className }: TabsProps<T>) {
  return (
    <div className={cn('flex items-center gap-5 border-b border-border', className)}>
      {items.map((item) => {
        const isActive = item.value === value
        return (
          <button
            key={item.value}
            type="button"
            onClick={() => onChange(item.value)}
            className={cn(
              'relative pb-2.5 text-sm transition-colors',
              isActive ? 'font-medium text-primary' : 'text-secondary hover:text-foreground',
            )}
          >
            {item.label}
            {isActive && <span className="absolute inset-x-0 bottom-0 h-0.5 rounded-full bg-primary" />}
          </button>
        )
      })}
    </div>
  )
}
