import type { TextareaHTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

/** Shadcn 风格的基础多行输入组件。 */
export function Textarea({ className, ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      className={cn(
        'flex min-h-20 w-full rounded-md border border-input bg-transparent px-2.5 py-2 text-sm outline-none placeholder:text-tertiary focus-visible:ring-2 focus-visible:ring-ring/30 disabled:cursor-not-allowed disabled:opacity-50',
        className,
      )}
      {...props}
    />
  )
}
