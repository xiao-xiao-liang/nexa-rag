import { cn } from '@/lib/utils'

export interface PaginationProps {
  total: number
  current: number
  totalPages: number
  onPageChange: (page: number) => void
  className?: string
  totalLabel?: string
}

/** 飞书风格分页组件。 */
export function Pagination({ total, current, totalPages, onPageChange, className, totalLabel = '条' }: PaginationProps) {
  return (
    <div className={cn('flex items-center justify-between text-xs text-tertiary', className)}>
      <span>共 {total} {totalLabel}</span>
      <div className="flex items-center gap-2">
        <button
          type="button"
          disabled={current <= 1}
          onClick={() => onPageChange(current - 1)}
          className="h-7 rounded border border-border bg-card px-3 text-secondary transition-colors hover:bg-muted disabled:cursor-not-allowed disabled:opacity-40"
        >
          上一页
        </button>
        <span className="rounded border border-primary bg-primary-light px-3 py-1 text-primary">
          第 {current} / {totalPages} 页
        </span>
        <button
          type="button"
          disabled={current >= totalPages}
          onClick={() => onPageChange(current + 1)}
          className="h-7 rounded border border-border bg-card px-3 text-secondary transition-colors hover:bg-muted disabled:cursor-not-allowed disabled:opacity-40"
        >
          下一页
        </button>
      </div>
    </div>
  )
}
