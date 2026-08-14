import * as ScrollAreaPrimitive from '@radix-ui/react-scroll-area'
import type { ComponentPropsWithoutRef } from 'react'
import { cn } from '@/lib/utils'

/** Shadcn 风格的可滚动区域组件。 */
export function ScrollArea({
  className,
  children,
  hideScrollbar = false,
  ...props
}: ComponentPropsWithoutRef<typeof ScrollAreaPrimitive.Root> & { hideScrollbar?: boolean }) {
  return (
    <ScrollAreaPrimitive.Root className={cn('relative overflow-hidden', className)} {...props}>
      <ScrollAreaPrimitive.Viewport className="size-full rounded-[inherit]">
        {children}
      </ScrollAreaPrimitive.Viewport>
      {!hideScrollbar && <ScrollBar />}
      {!hideScrollbar && <ScrollAreaPrimitive.Corner />}
    </ScrollAreaPrimitive.Root>
  )
}

/** 滚动区域的滚动条组件。 */
export function ScrollBar({
  className,
  orientation = 'vertical',
  ...props
}: ComponentPropsWithoutRef<typeof ScrollAreaPrimitive.ScrollAreaScrollbar>) {
  return (
    <ScrollAreaPrimitive.ScrollAreaScrollbar
      orientation={orientation}
      className={cn(
        'flex touch-none select-none p-0.5 transition-colors',
        orientation === 'vertical' && 'h-full w-1.5 border-l border-l-transparent',
        orientation === 'horizontal' && 'h-1.5 flex-col border-t border-t-transparent',
        className,
      )}
      {...props}
    >
      <ScrollAreaPrimitive.ScrollAreaThumb className="relative flex-1 rounded-full bg-border/60 hover:bg-border" />
    </ScrollAreaPrimitive.ScrollAreaScrollbar>
  )
}
