import { cva, type VariantProps } from 'class-variance-authority'
import type { HTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

const tagVariants = cva('inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-xs leading-5', {
  variants: {
    variant: {
      success: 'bg-success-light text-success',
      info: 'bg-primary-light text-primary',
      warning: 'bg-warning-light text-warning',
      danger: 'bg-danger-light text-danger',
      neutral: 'bg-muted text-secondary',
    },
  },
  defaultVariants: { variant: 'neutral' },
})

export type TagVariant = NonNullable<VariantProps<typeof tagVariants>['variant']>

/** 飞书风格状态标签。 */
export function Tag({
  className,
  variant,
  ...props
}: HTMLAttributes<HTMLSpanElement> & VariantProps<typeof tagVariants>) {
  return <span className={cn(tagVariants({ variant }), className)} {...props} />
}
