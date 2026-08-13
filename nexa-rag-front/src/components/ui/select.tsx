import * as React from 'react'
import { ChevronDown, Check } from 'lucide-react'
import { cn } from '@/lib/utils'

export interface SelectOption {
  value: string
  label: string
  description?: string
  icon?: React.ReactNode
}

export interface CustomSelectProps {
  value: string
  onChange: (value: string) => void
  options: SelectOption[]
  placeholder?: string
  className?: string
  triggerClassName?: string
  disabled?: boolean
}

export function CustomSelect({
  value,
  onChange,
  options,
  placeholder = '请选择...',
  className,
  triggerClassName,
  disabled = false,
}: CustomSelectProps) {
  const [open, setOpen] = React.useState(false)
  const containerRef = React.useRef<HTMLDivElement>(null)

  const selectedOption = options.find((opt) => opt.value === value)

  // 点击外部自动收起下拉
  React.useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  return (
    <div ref={containerRef} className={cn('relative inline-block w-full text-left', className)}>
      <button
        type="button"
        disabled={disabled}
        onClick={() => setOpen(!open)}
        className={cn(
          'flex w-full items-center justify-between gap-2 rounded-md border border-input bg-card px-3 py-1.5 text-xs text-foreground shadow-xs transition-all duration-200',
          'hover:border-primary hover:bg-muted',
          'focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary',
          open && 'border-primary ring-2 ring-primary/30',
          disabled && 'cursor-not-allowed opacity-50 bg-muted',
          triggerClassName,
        )}
      >
        <div className="flex items-center gap-2 truncate">
          {selectedOption?.icon}
          <span className="truncate font-medium">
            {selectedOption ? selectedOption.label : placeholder}
          </span>
        </div>
        <ChevronDown
          className={cn(
            'size-3.5 shrink-0 text-tertiary transition-transform duration-200',
            open && 'rotate-180 text-primary',
          )}
        />
      </button>

      {open && (
        <div className="absolute left-0 right-0 z-50 mt-1.5 max-h-60 overflow-y-auto rounded-md border border-border bg-card p-1 shadow-lg animate-in fade-in-80 zoom-in-95">
          {options.length === 0 ? (
            <div className="px-3 py-2 text-center text-xs text-tertiary">无可选数据</div>
          ) : (
            options.map((opt) => {
              const isSelected = opt.value === value
              return (
                <div
                  key={opt.value}
                  onClick={() => {
                    onChange(opt.value)
                    setOpen(false)
                  }}
                  className={cn(
                    'flex cursor-pointer items-center justify-between rounded px-2.5 py-1.5 text-xs transition-colors',
                    isSelected
                      ? 'bg-primary-light font-semibold text-primary'
                      : 'text-secondary hover:bg-muted',
                  )}
                >
                  <div className="flex items-center gap-2 truncate">
                    {opt.icon}
                    <span className="truncate">{opt.label}</span>
                  </div>
                  {isSelected && <Check className="size-3.5 text-primary" />}
                </div>
              )
            })
          )}
        </div>
      )}
    </div>
  )
}
