import * as TooltipPrimitive from '@radix-ui/react-tooltip'
import type { ComponentPropsWithoutRef } from 'react'
import { cn } from '@/lib/utils'

/** Tooltip 的全局上下文提供者。 */
export const TooltipProvider = TooltipPrimitive.Provider

/** Tooltip 根组件。 */
export const Tooltip = TooltipPrimitive.Root

/** Tooltip 触发器组件。 */
export const TooltipTrigger = TooltipPrimitive.Trigger

/** Shadcn 风格的 Tooltip 内容组件。 */
export function TooltipContent({ className, sideOffset = 6, ...props }: ComponentPropsWithoutRef<typeof TooltipPrimitive.Content>) {
  return (
    <TooltipPrimitive.Portal>
      <TooltipPrimitive.Content
        sideOffset={sideOffset}
        className={cn('z-50 rounded-md bg-foreground px-3 py-1.5 text-xs text-background shadow-md', className)}
        {...props}
      />
    </TooltipPrimitive.Portal>
  )
}
