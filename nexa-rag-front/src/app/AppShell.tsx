import { useMemo, useState } from 'react'
import {
  Bot, ChevronDown, FileText, LibraryBig, Network, Plus, Search,
  Settings, SlidersHorizontal, Sparkles, Workflow,
} from 'lucide-react'
import { NavLink, Outlet, useNavigate, useSearchParams } from 'react-router-dom'
import { Skeleton } from '@/components/ui/skeleton'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { ConversationNavigationProvider, useConversationNavigation } from '@/features/conversations/ConversationNavigationContext'
import { cn } from '@/lib/utils'

const utilityNavigation = [
  { label: '知识库', icon: LibraryBig, to: '/knowledge-base' },
  { label: '提示词管理', icon: FileText },
  { label: '模型配置', icon: Bot },
  { label: '路由管理', icon: Network },
  { label: '模型治理', icon: SlidersHorizontal },
  { label: '设置', icon: Settings },
]

/** AI 中台页面外壳，提供会话优先的统一导航与内容出口。 */
export function AppShell() {
  return <ConversationNavigationProvider><AppShellContent /></ConversationNavigationProvider>
}

function AppShellContent() {
  const { conversations, error, loading, refresh } = useConversationNavigation()
  const [search, setSearch] = useState('')
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const selectedId = searchParams.get('conversation')
  const filteredConversations = useMemo(() => {
    const keyword = search.trim().toLocaleLowerCase()
    return keyword ? conversations.filter((conversation) => (conversation.title || '未命名会话').toLocaleLowerCase().includes(keyword)) : conversations
  }, [conversations, search])

  const openConversation = (conversationId: string) => {
    // 1. 使用地址栏保存当前会话，保证前进后退和刷新后仍能恢复上下文。
    navigate(`/chat?conversation=${encodeURIComponent(conversationId)}`)
  }

  return (
    <TooltipProvider delayDuration={250}>
      <div className="flex h-dvh min-h-[560px] overflow-hidden bg-[#f8f8fb] text-slate-900">
        <aside aria-label="会话与功能导航" className="hidden w-[276px] shrink-0 flex-col border-r border-[#e8e7ee] bg-[#fcfcfe] lg:flex">
          <div className="px-5 pb-4 pt-5">
            <NavLink to="/chat" className="flex items-center gap-2.5" aria-label="NexaRAG 首页">
              <span className="flex size-8 items-center justify-center rounded-[10px] bg-gradient-to-br from-[#7166f7] to-[#9b8cff] text-white shadow-sm"><Sparkles className="size-4" aria-hidden="true" /></span>
              <span className="text-[17px] font-semibold tracking-[-0.03em]">NexaRAG</span>
              <span className="rounded-md bg-[#f0eeff] px-1.5 py-0.5 text-[10px] font-medium tracking-wide text-[#6b5ce7]">BETA</span>
            </NavLink>
          </div>

          <div className="px-4">
            <NavLink to="/chat" end className="block">
              {({ isActive }) => <span className={cn('flex h-10 items-center justify-center gap-2 rounded-xl text-sm font-medium transition-colors', isActive ? 'bg-[#6f62e8] text-white shadow-sm' : 'bg-[#f1f0f5] text-slate-700 hover:bg-[#eae8f1]')}><Plus className="size-4" />新建对话</span>}
            </NavLink>
          </div>

          <div className="px-4 pt-4">
            <label className="flex h-9 items-center gap-2 rounded-xl border border-[#e7e5ed] bg-white px-3 text-[#9a98a6] focus-within:border-[#a69cf3] focus-within:ring-2 focus-within:ring-[#edeaff]">
              <Search className="size-3.5" aria-hidden="true" />
              <input aria-label="搜索会话" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="搜索会话" className="min-w-0 flex-1 bg-transparent text-xs text-slate-700 outline-none placeholder:text-[#aaa8b4]" />
            </label>
          </div>

          <section className="min-h-0 flex-1 overflow-y-auto px-3 pb-4 pt-5" aria-label="历史会话">
            <div className="mb-2 flex items-center justify-between px-2"><span className="text-[11px] font-semibold uppercase tracking-[0.12em] text-[#9a98a6]">历史会话</span><button type="button" className="text-[#a5a3af] hover:text-[#6256da]" aria-label="刷新会话" onClick={() => void refresh()}><Workflow className="size-3.5" /></button></div>
            {loading && <div className="space-y-2 px-2"><Skeleton className="h-9 w-full" /><Skeleton className="h-9 w-4/5" /><Skeleton className="h-9 w-11/12" /></div>}
            {error && <div className="mx-2 rounded-lg bg-[#fff1f0] p-2 text-xs leading-5 text-[#bc5349]"><p>{error}</p><button type="button" className="mt-1 font-medium underline" onClick={() => void refresh()}>重新加载</button></div>}
            {!loading && !error && filteredConversations.length === 0 && <p className="px-2 py-5 text-xs text-[#aaa8b4]">{search ? '没有匹配的会话' : '从一段新对话开始'}</p>}
            {!loading && !error && filteredConversations.length > 0 && <div className="space-y-4">{groupConversations(filteredConversations).map(({ label, items }) => <div key={label}><p className="px-2 pb-1.5 text-[11px] font-medium text-[#aaa8b4]">{label}</p><div className="space-y-0.5">{items.map((conversation) => <button key={conversation.conversationId} type="button" aria-label={`打开会话 ${conversation.title || '未命名会话'}`} onClick={() => openConversation(conversation.conversationId)} className={cn('group flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-[13px] transition-colors', selectedId === conversation.conversationId ? 'bg-[#eeecff] text-[#5649ce]' : 'text-[#565460] hover:bg-[#f1f0f5]')}><span className={cn('size-1.5 shrink-0 rounded-full', selectedId === conversation.conversationId ? 'bg-[#7166f7]' : 'bg-[#d1ceda]')} /><span className="truncate">{conversation.title || '未命名会话'}</span></button>)}</div></div>)}</div>}
          </section>

          <section className="border-t border-[#eceaf0] px-4 py-4" aria-label="功能配置">
            <p className="mb-2.5 px-1 text-[11px] font-semibold uppercase tracking-[0.12em] text-[#9a98a6]">功能配置</p>
            <div className="grid grid-cols-2 gap-1.5">{utilityNavigation.map(({ icon: Icon, label, to }) => to ? <NavLink key={label} to={to} className="flex min-w-0 items-center gap-1.5 rounded-lg px-2 py-2 text-xs text-[#656370] transition-colors hover:bg-[#f1f0f5]"><Icon className="size-3.5 shrink-0 text-[#8d87d5]" aria-hidden="true" /><span className="truncate">{label}</span></NavLink> : <Tooltip key={label}><TooltipTrigger asChild><button type="button" className="flex min-w-0 items-center gap-1.5 rounded-lg px-2 py-2 text-left text-xs text-[#656370] transition-colors hover:bg-[#f1f0f5]"><Icon className="size-3.5 shrink-0 text-[#8d87d5]" aria-hidden="true" /><span className="truncate">{label}</span></button></TooltipTrigger><TooltipContent side="right">页面将在后续阶段接入</TooltipContent></Tooltip>)}</div>
          </section>

          <div className="flex items-center gap-2.5 border-t border-[#eceaf0] px-5 py-3.5"><span className="flex size-7 items-center justify-center rounded-full bg-gradient-to-br from-[#f0b3a5] to-[#ce856f] text-[10px] font-semibold text-white">N</span><div className="min-w-0 flex-1"><p className="truncate text-xs font-medium text-[#4c4a55]">Nexa User</p><p className="text-[10px] text-[#9a98a6]">个人工作区</p></div><ChevronDown className="size-3.5 text-[#aaa8b4]" /></div>
        </aside>

        <main className="min-w-0 flex-1"><Outlet /></main>
      </div>
    </TooltipProvider>
  )
}

function groupConversations(conversations: ReturnType<typeof useConversationNavigation>['conversations']) {
  const today = new Date()
  return conversations.reduce<{ label: string; items: typeof conversations }[]>((groups, conversation) => {
    const updatedTime = new Date(conversation.updatedTime)
    const label = updatedTime.toDateString() === today.toDateString() ? '今天' : '更早'
    const currentGroup = groups.find((group) => group.label === label)
    if (currentGroup) {
      currentGroup.items.push(conversation)
    } else {
      groups.push({ label, items: [conversation] })
    }
    return groups
  }, [])
}
