import { SidebarNavItem } from '@/components/layout/SidebarNavItem'

interface PanelGroupItem {
  label: string
  to: string
  end: boolean
  badge?: string
}

const groups: { label: string; items: PanelGroupItem[] }[] = [
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
    items: [{ label: '提示词管理', to: '/prompts', end: true, badge: 'New' }],
  },
]

/** 模型管理模块面板：分组菜单。 */
export function ModelPanel() {
  return (
    <aside aria-label="模型管理导航" className="flex h-full w-full shrink-0 flex-col border-r border-border bg-card py-3">
      {groups.map((group) => (
        <div key={group.label} className="mb-3 flex flex-col">
          <p className="px-4 pb-1 text-xs font-medium text-tertiary">{group.label}</p>
          {group.items.map((item) => (
            <SidebarNavItem key={item.label} to={item.to} end={item.end} label={item.label} badge={item.badge} />
          ))}
        </div>
      ))}
    </aside>
  )
}
