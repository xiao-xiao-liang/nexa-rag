import * as DialogPrimitive from '@radix-ui/react-dialog'
import type { ComponentPropsWithoutRef, HTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

/** 对话框根组件。 */
export const Dialog = DialogPrimitive.Root

/** 对话框触发器组件。 */
export const DialogTrigger = DialogPrimitive.Trigger

/** 对话框关闭按钮组件。 */
export const DialogClose = DialogPrimitive.Close

/** Shadcn 风格的对话框遮罩层。 */
export function DialogOverlay({ className, ...props }: ComponentPropsWithoutRef<typeof DialogPrimitive.Overlay>) {
  return <DialogPrimitive.Overlay className={cn('fixed inset-0 z-50 bg-slate-950/40', className)} {...props} />
}

/** Shadcn 风格的对话框内容组件。 */
export function DialogContent({ className, children, ...props }: ComponentPropsWithoutRef<typeof DialogPrimitive.Content>) {
  return (
    <DialogPrimitive.Portal>
      <DialogOverlay />
      <DialogPrimitive.Content
        className={cn('fixed left-1/2 top-1/2 z-50 grid w-[calc(100%-2rem)] max-w-lg -translate-x-1/2 -translate-y-1/2 gap-4 rounded-xl border bg-card p-6 text-card-foreground shadow-xl', className)}
        {...props}
      >
        {children}
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  )
}

/** 对话框标题区域。 */
export function DialogHeader({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('flex flex-col gap-1.5 text-left', className)} {...props} />
}

/** 对话框标题组件。 */
export function DialogTitle({ className, ...props }: ComponentPropsWithoutRef<typeof DialogPrimitive.Title>) {
  return <DialogPrimitive.Title className={cn('text-lg font-semibold', className)} {...props} />
}

/** 对话框说明组件。 */
export function DialogDescription({ className, ...props }: ComponentPropsWithoutRef<typeof DialogPrimitive.Description>) {
  return <DialogPrimitive.Description className={cn('text-sm text-muted-foreground', className)} {...props} />
}
