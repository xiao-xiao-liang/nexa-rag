import { FileUp, FolderOpen } from 'lucide-react'
import { NavLink, useLocation } from 'react-router-dom'
import { cn } from '@/lib/utils'

const items = [
  { label: '知识库概览', to: '/knowledge-base?view=overview', end: true },
  { label: '全部文档', to: '/knowledge-base?view=documents', end: true },
  { label: '处理中', to: '/knowledge-base?view=documents&status=PROCESSING', end: true },
  { label: '处理失败', to: '/knowledge-base?view=documents&status=FAILED', end: true },
]

function isItemActive(item: { to: string }, isActive: boolean, search: string): boolean {
  if (!isActive) return false
  if (item.to.includes('status=PROCESSING')) return search.includes('status=PROCESSING')
  if (item.to.includes('status=FAILED')) return search.includes('status=FAILED')
  if (item.to.includes('view=documents')) return search.includes('view=documents')
  return !search.includes('view=documents')
}

/** 知识库模块面板：库导航 + 视图 + 快捷筛选 + 上传入口。 */
export function KnowledgePanel() {
  const { search } = useLocation()
  return (
    <aside aria-label="知识库导航" className="flex w-[232px] shrink-0 flex-col border-r border-border bg-card py-2">
      <div className="mb-1 flex items-center justify-between px-4 py-1.5">
        <span className="text-sm font-semibold text-foreground">知识库</span>
        <FolderOpen className="size-4 text-tertiary" aria-hidden="true" />
      </div>
      <div className="mx-2 mb-2 flex items-center gap-2 rounded-md bg-primary-light px-2.5 py-1.5 text-xs font-medium text-primary">
        默认知识库
      </div>
      <p className="px-4 pb-1 pt-2 text-xs font-medium text-tertiary">视图</p>
      {items.slice(0, 2).map((item) => (
        <NavLink
          key={item.label}
          to={item.to}
          end={item.end}
          className={({ isActive }) =>
            cn(
              'mx-2 rounded px-3 py-1.5 text-sm transition-colors',
              isItemActive(item, isActive, search)
                ? 'bg-primary-light font-medium text-primary'
                : 'text-secondary hover:bg-muted hover:text-foreground',
            )}
        >
          {item.label}
        </NavLink>
      ))}
      <p className="px-4 pb-1 pt-3 text-xs font-medium text-tertiary">快捷筛选</p>
      {items.slice(2).map((item) => (
        <NavLink
          key={item.label}
          to={item.to}
          end={item.end}
          className={({ isActive }) =>
            cn(
              'mx-2 rounded px-3 py-1.5 text-sm transition-colors',
              isItemActive(item, isActive, search)
                ? 'bg-primary-light font-medium text-primary'
                : 'text-secondary hover:bg-muted hover:text-foreground',
            )}
        >
          {item.label}
        </NavLink>
      ))}
      <div className="flex-1" />
      <NavLink
        to="/knowledge-base?view=documents&upload=1"
        className="mx-3 mb-2 flex items-center justify-center gap-1.5 rounded-md bg-primary py-1.5 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
      >
        <FileUp className="size-3.5" />
        上传文档
      </NavLink>
    </aside>
  )
}
