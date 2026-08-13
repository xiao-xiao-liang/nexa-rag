import { HelpCircle, Search } from 'lucide-react'

/** 飞书风格顶栏：全局搜索样式 + 帮助 + 用户头像。全局搜索行为本轮不做。 */
export function TopBar() {
  return (
    <header className="flex h-11 shrink-0 items-center gap-4 border-b border-border bg-card px-4">
      <div className="relative mx-auto w-full max-w-md">
        <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-tertiary" aria-hidden="true" />
        <input
          aria-label="全局搜索"
          placeholder="搜索会话、文档、模型…"
          className="h-7 w-full rounded-md border border-transparent bg-muted pl-8 pr-3 text-xs text-foreground outline-none placeholder:text-tertiary focus:border-input focus:bg-card"
        />
      </div>
      <div className="ml-auto flex items-center gap-3 text-tertiary">
        <button type="button" aria-label="帮助" className="transition-colors hover:text-primary">
          <HelpCircle className="size-4" />
        </button>
        <span className="flex size-6 items-center justify-center rounded-full bg-tertiary/30 text-[10px] font-semibold text-secondary" aria-label="当前用户">
          N
        </span>
      </div>
    </header>
  )
}
