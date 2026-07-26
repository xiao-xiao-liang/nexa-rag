import type { HTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

/** Shadcn 风格的加载骨架组件。 */
export function Skeleton({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      aria-hidden="true"
      className={cn('animate-pulse rounded-md bg-muted', className)}
      {...props}
    />
  )
}
