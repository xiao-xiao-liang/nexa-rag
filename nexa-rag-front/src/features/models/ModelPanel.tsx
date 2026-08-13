import { NavLink } from 'react-router-dom'
import { cn } from '@/lib/utils'

const groups = [
  {
    label: '模型管理',
    items: [
      { label: '模型配置', to: '/models', end: true },
      { label: '路由管理', to: '/models/routes', end: true },
      { label: '治理参数', to: '/models/governance', end: true },
    ],
  },
  {
    label: '模板',
    items: [{ label: '提示词管理', to: '/prompts', end: true }],
  },
]

/** 模型管理模块面板：分组菜单。 */
export function ModelPanel() {
  return (
    <aside aria-label="模型管理导航" className="flex w-[232px] shrink-0 flex-col border-r border-border bg-card py-3">
      {groups.map((group) => (
        <div key={group.label} className="mb-3">
          <p className="px-4 pb-1 text-xs font-medium text-tertiary">{group.label}</p>
          {group.items.map((item) => (
            <NavLink
              key={item.label}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'mx-2 rounded px-3 py-1.5 text-sm transition-colors',
                  isActive ? 'bg-primary-light font-medium text-primary' : 'text-secondary hover:bg-muted hover:text-foreground',
                )}
            >
              {item.label}
            </NavLink>
          ))}
        </div>
      ))}
    </aside>
  )
}
