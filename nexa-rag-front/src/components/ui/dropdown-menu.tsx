import * as DropdownMenuPrimitive from '@radix-ui/react-dropdown-menu'
import type { ComponentPropsWithoutRef } from 'react'
import { cn } from '@/lib/utils'

/** 下拉菜单根组件。 */
export const DropdownMenu = DropdownMenuPrimitive.Root

/** 下拉菜单触发器组件。 */
export const DropdownMenuTrigger = DropdownMenuPrimitive.Trigger

/** 下拉菜单分组组件。 */
export const DropdownMenuGroup = DropdownMenuPrimitive.Group

/** 下拉菜单分隔线组件。 */
export function DropdownMenuSeparator({ className, ...props }: ComponentPropsWithoutRef<typeof DropdownMenuPrimitive.Separator>) {
  return <DropdownMenuPrimitive.Separator className={cn('-mx-1 my-1 h-px bg-border', className)} {...props} />
}

/** 下拉菜单标签组件。 */
export function DropdownMenuLabel({ className, ...props }: ComponentPropsWithoutRef<typeof DropdownMenuPrimitive.Label>) {
  return <DropdownMenuPrimitive.Label className={cn('px-2 py-1.5 text-xs font-semibold', className)} {...props} />
}

/** Shadcn 风格的下拉菜单内容组件。 */
export function DropdownMenuContent({ className, sideOffset = 6, ...props }: ComponentPropsWithoutRef<typeof DropdownMenuPrimitive.Content>) {
  return (
    <DropdownMenuPrimitive.Portal>
      <DropdownMenuPrimitive.Content
        sideOffset={sideOffset}
        className={cn('z-50 min-w-36 overflow-hidden rounded-lg border bg-card p-1 text-card-foreground shadow-lg', className)}
        {...props}
      />
    </DropdownMenuPrimitive.Portal>
  )
}

/** Shadcn 风格的下拉菜单项组件。 */
export function DropdownMenuItem({ className, ...props }: ComponentPropsWithoutRef<typeof DropdownMenuPrimitive.Item>) {
  return (
    <DropdownMenuPrimitive.Item
      className={cn('relative flex cursor-default select-none items-center rounded-md px-2 py-1.5 text-sm outline-none hover:bg-muted focus:bg-muted data-[disabled]:pointer-events-none data-[disabled]:opacity-50', className)}
      {...props}
    />
  )
}
