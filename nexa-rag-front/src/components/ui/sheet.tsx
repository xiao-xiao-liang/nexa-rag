import * as DialogPrimitive from '@radix-ui/react-dialog'
import { cva, type VariantProps } from 'class-variance-authority'
import type { ComponentPropsWithoutRef, HTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

/** 抽屉根组件。 */
export const Sheet = DialogPrimitive.Root

/** 抽屉触发器组件。 */
export const SheetTrigger = DialogPrimitive.Trigger

/** 抽屉关闭按钮组件。 */
export const SheetClose = DialogPrimitive.Close

/** 抽屉遮罩层组件。 */
export function SheetOverlay({ className, ...props }: ComponentPropsWithoutRef<typeof DialogPrimitive.Overlay>) {
  return <DialogPrimitive.Overlay className={cn('fixed inset-0 z-50 bg-slate-950/40', className)} {...props} />
}

const sheetVariants = cva('fixed z-50 flex flex-col gap-4 bg-card p-6 text-card-foreground shadow-xl', {
  variants: {
    side: {
      top: 'inset-x-0 top-0 border-b',
      bottom: 'inset-x-0 bottom-0 border-t',
      left: 'inset-y-0 left-0 h-full w-3/4 border-r sm:max-w-sm',
      right: 'inset-y-0 right-0 h-full w-3/4 border-l sm:max-w-sm',
    },
  },
  defaultVariants: {
    side: 'right',
  },
})

/** Shadcn 风格的抽屉内容组件。 */
export function SheetContent({
  side,
  className,
  children,
  ...props
}: ComponentPropsWithoutRef<typeof DialogPrimitive.Content> & VariantProps<typeof sheetVariants>) {
  return (
    <DialogPrimitive.Portal>
      <SheetOverlay />
      <DialogPrimitive.Content className={cn(sheetVariants({ side }), className)} {...props}>
        {children}
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  )
}

/** 抽屉标题区域。 */
export function SheetHeader({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('flex flex-col gap-1.5 text-left', className)} {...props} />
}

/** 抽屉标题组件。 */
export function SheetTitle({ className, ...props }: ComponentPropsWithoutRef<typeof DialogPrimitive.Title>) {
  return <DialogPrimitive.Title className={cn('text-lg font-semibold', className)} {...props} />
}

/** 抽屉说明组件。 */
export function SheetDescription({ className, ...props }: ComponentPropsWithoutRef<typeof DialogPrimitive.Description>) {
  return <DialogPrimitive.Description className={cn('text-sm text-muted-foreground', className)} {...props} />
}
