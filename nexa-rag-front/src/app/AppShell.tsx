import { useMemo, useState, type KeyboardEvent, type MouseEvent } from 'react'
import {
  Bot, Check, ChevronDown, FileText, LibraryBig, MoreHorizontal, Network, PanelLeftClose, PanelLeftOpen,
  Pencil, Pin, PinOff, Plus, Search, Settings, SlidersHorizontal, Sparkles, Trash2, Workflow, X,
} from 'lucide-react'
import { NavLink, Outlet, useNavigate, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Skeleton } from '@/components/ui/skeleton'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { ConversationNavigationProvider, useConversationNavigation } from '@/features/conversations/ConversationNavigationContext'
import { cn } from '@/lib/utils'

const utilityNavigation = [
  { label: '知识库', icon: LibraryBig, to: '/knowledge-base' },
  { label: '提示词管理', icon: FileText },
  { label: '模型配置', icon: Bot, to: '/models' },
  { label: '路由管理', icon: Network },
  { label: '模型治理', icon: SlidersHorizontal },
  { label: '设置', icon: Settings },
]

/** AI 中台页面外壳，提供全功能侧边栏与动态流布局。 */
export function AppShell() {
  return (
    <ConversationNavigationProvider>
      <AppShellContent />
    </ConversationNavigationProvider>
  )
}

function AppShellContent() {
  const {
    conversations,
    pinnedIds,
    error,
    loading,
    refresh,
    renameConversation,
    deleteConversation,
    togglePinConversation,
  } = useConversationNavigation()

  const [search, setSearch] = useState('')
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editTitle, setEditTitle] = useState('')
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const selectedId = searchParams.get('conversation')

  const filteredConversations = useMemo(() => {
    const keyword = search.trim().toLocaleLowerCase()
    return keyword
      ? conversations.filter((conversation) =>
          (conversation.title || '未命名会话').toLocaleLowerCase().includes(keyword),
        )
      : conversations
  }, [conversations, search])

  const { pinnedItems, todayItems, earlierItems } = useMemo(() => {
    const today = new Date()
    const pinned: typeof conversations = []
    const todayGroup: typeof conversations = []
    const earlierGroup: typeof conversations = []

    filteredConversations.forEach((item) => {
      if (pinnedIds.includes(item.conversationId)) {
        pinned.push(item)
      } else {
        const updatedTime = new Date(item.updatedTime)
        if (updatedTime.toDateString() === today.toDateString()) {
          todayGroup.push(item)
        } else {
          earlierGroup.push(item)
        }
      }
    })

    return {
      pinnedItems: pinned,
      todayItems: todayGroup,
      earlierItems: earlierGroup,
    }
  }, [filteredConversations, pinnedIds])

  const openConversation = (conversationId: string) => {
    if (editingId) return
    navigate(`/chat?conversation=${encodeURIComponent(conversationId)}`)
  }

  const handleStartRename = (conversationId: string, currentTitle: string | null) => {
    setEditingId(conversationId)
    setEditTitle(currentTitle || '未命名会话')
  }

  const handleSaveRename = async (conversationId: string) => {
    const trimmed = editTitle.trim()
    if (!trimmed) return
    try {
      await renameConversation(conversationId, trimmed)
    } catch {
      // 捕获与静默恢复
    } finally {
      setEditingId(null)
    }
  }

  const handleKeyDownRename = (event: KeyboardEvent<HTMLInputElement>, conversationId: string) => {
    if (event.key === 'Enter') {
      event.preventDefault()
      void handleSaveRename(conversationId)
    } else if (event.key === 'Escape') {
      event.preventDefault()
      setEditingId(null)
    }
  }

  const handleConfirmDelete = async () => {
    if (!confirmDeleteId || isDeleting) return
    setIsDeleting(true)
    const idToDelete = confirmDeleteId
    try {
      await deleteConversation(idToDelete)
      if (selectedId === idToDelete) {
        navigate('/chat')
      }
    } catch {
      // 错误捕获
    } finally {
      setIsDeleting(false)
      setConfirmDeleteId(null)
    }
  }

  return (
    <TooltipProvider delayDuration={250}>
      <div className="flex h-dvh min-h-[560px] overflow-hidden bg-[#f8f8fb] text-slate-900">
        {/* 侧边栏 */}
        <aside
          aria-label="会话与功能导航"
          className={cn(
            'flex shrink-0 flex-col border-r border-[#e8e7ee] bg-[#fcfcfe] transition-all duration-200 ease-in-out',
            sidebarOpen ? 'w-[276px]' : 'w-0 border-r-0 opacity-0 overflow-hidden',
          )}
        >
          {/* Header 标识与收起按钮 */}
          <div className="flex items-center justify-between px-4 pb-3 pt-4">
            <NavLink to="/chat" className="flex items-center gap-2.5" aria-label="NexaRAG 首页">
              <span className="flex size-8 items-center justify-center rounded-[10px] bg-gradient-to-br from-[#7166f7] to-[#9b8cff] text-white shadow-sm">
                <Sparkles className="size-4" aria-hidden="true" />
              </span>
              <span className="text-[17px] font-semibold tracking-[-0.03em]">NexaRAG</span>
              <span className="rounded-md bg-[#f0eeff] px-1.5 py-0.5 text-[10px] font-medium tracking-wide text-[#6b5ce7]">BETA</span>
            </NavLink>
            <Tooltip>
              <TooltipTrigger asChild>
                <button
                  type="button"
                  onClick={() => setSidebarOpen(false)}
                  className="flex size-7 items-center justify-center rounded-lg text-[#9a98a6] hover:bg-[#f1f0f5] hover:text-[#5649ce]"
                  aria-label="收起侧边栏"
                >
                  <PanelLeftClose className="size-4" />
                </button>
              </TooltipTrigger>
              <TooltipContent side="right">收起侧边栏</TooltipContent>
            </Tooltip>
          </div>

          {/* 新建对话主按钮 */}
          <div className="px-4">
            <NavLink to="/chat" end className="block">
              {({ isActive }) => (
                <span
                  className={cn(
                    'flex h-10 items-center justify-center gap-2 rounded-xl text-sm font-medium transition-all shadow-sm',
                    isActive
                      ? 'bg-[#6f62e8] text-white hover:bg-[#5f52d9]'
                      : 'bg-[#f1f0f5] text-slate-700 hover:bg-[#eae8f1]',
                  )}
                >
                  <Plus className="size-4" />
                  新建对话
                </span>
              )}
            </NavLink>
          </div>

          {/* 搜索框 */}
          <div className="px-4 pt-3.5">
            <label className="flex h-9 items-center gap-2 rounded-xl border border-[#e7e5ed] bg-white px-3 text-[#9a98a6] focus-within:border-[#a69cf3] focus-within:ring-2 focus-within:ring-[#edeaff]">
              <Search className="size-3.5" aria-hidden="true" />
              <input
                aria-label="搜索会话"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="搜索会话"
                className="min-w-0 flex-1 bg-transparent text-xs text-slate-700 outline-none placeholder:text-[#aaa8b4]"
              />
            </label>
          </div>

          {/* 历史会话列表 */}
          <section className="min-h-0 flex-1 overflow-y-auto px-3 pb-4 pt-4" aria-label="历史会话">
            <div className="mb-2 flex items-center justify-between px-2">
              <span className="text-[11px] font-semibold uppercase tracking-[0.12em] text-[#9a98a6]">历史会话</span>
              <button
                type="button"
                className="text-[#a5a3af] hover:text-[#6256da]"
                aria-label="刷新会话"
                onClick={() => void refresh()}
              >
                <Workflow className="size-3.5" />
              </button>
            </div>

            {loading && (
              <div className="space-y-2 px-2">
                <Skeleton className="h-9 w-full rounded-lg" />
                <Skeleton className="h-9 w-4/5 rounded-lg" />
                <Skeleton className="h-9 w-11/12 rounded-lg" />
              </div>
            )}

            {error && (
              <div className="mx-2 rounded-lg bg-[#fff1f0] p-2.5 text-xs leading-5 text-[#bc5349]">
                <p>{error}</p>
                <button type="button" className="mt-1 font-medium underline" onClick={() => void refresh()}>
                  重新加载
                </button>
              </div>
            )}

            {!loading && !error && filteredConversations.length === 0 && (
              <p className="px-2 py-5 text-xs text-[#aaa8b4]">{search ? '没有匹配的会话' : '从一段新对话开始'}</p>
            )}

            {!loading && !error && filteredConversations.length > 0 && (
              <div className="space-y-4">
                {/* 📌 置顶会话 */}
                {pinnedItems.length > 0 && (
                  <div>
                    <p className="flex items-center gap-1 px-2 pb-1.5 text-[11px] font-medium text-[#aaa8b4]">
                      <Pin className="size-3 text-[#7166f7]" /> 置顶对话
                    </p>
                    <div className="space-y-0.5">
                      {pinnedItems.map((conversation) => (
                        <ConversationListItemRow
                          key={conversation.conversationId}
                          conversation={conversation}
                          isSelected={selectedId === conversation.conversationId}
                          isEditing={editingId === conversation.conversationId}
                          isPinned
                          editTitle={editTitle}
                          onEditTitleChange={setEditTitle}
                          onSaveRename={() => void handleSaveRename(conversation.conversationId)}
                          onCancelRename={() => setEditingId(null)}
                          onKeyDownRename={(e) => handleKeyDownRename(e, conversation.conversationId)}
                          onSelect={() => openConversation(conversation.conversationId)}
                          onTogglePin={() => togglePinConversation(conversation.conversationId)}
                          onStartRename={() => handleStartRename(conversation.conversationId, conversation.title)}
                          onRequestDelete={() => setConfirmDeleteId(conversation.conversationId)}
                        />
                      ))}
                    </div>
                  </div>
                )}

                {/* 📅 今天 */}
                {todayItems.length > 0 && (
                  <div>
                    <p className="px-2 pb-1.5 text-[11px] font-medium text-[#aaa8b4]">今天</p>
                    <div className="space-y-0.5">
                      {todayItems.map((conversation) => (
                        <ConversationListItemRow
                          key={conversation.conversationId}
                          conversation={conversation}
                          isSelected={selectedId === conversation.conversationId}
                          isEditing={editingId === conversation.conversationId}
                          isPinned={false}
                          editTitle={editTitle}
                          onEditTitleChange={setEditTitle}
                          onSaveRename={() => void handleSaveRename(conversation.conversationId)}
                          onCancelRename={() => setEditingId(null)}
                          onKeyDownRename={(e) => handleKeyDownRename(e, conversation.conversationId)}
                          onSelect={() => openConversation(conversation.conversationId)}
                          onTogglePin={() => togglePinConversation(conversation.conversationId)}
                          onStartRename={() => handleStartRename(conversation.conversationId, conversation.title)}
                          onRequestDelete={() => setConfirmDeleteId(conversation.conversationId)}
                        />
                      ))}
                    </div>
                  </div>
                )}

                {/* 🕒 更早 */}
                {earlierItems.length > 0 && (
                  <div>
                    <p className="px-2 pb-1.5 text-[11px] font-medium text-[#aaa8b4]">更早</p>
                    <div className="space-y-0.5">
                      {earlierItems.map((conversation) => (
                        <ConversationListItemRow
                          key={conversation.conversationId}
                          conversation={conversation}
                          isSelected={selectedId === conversation.conversationId}
                          isEditing={editingId === conversation.conversationId}
                          isPinned={false}
                          editTitle={editTitle}
                          onEditTitleChange={setEditTitle}
                          onSaveRename={() => void handleSaveRename(conversation.conversationId)}
                          onCancelRename={() => setEditingId(null)}
                          onKeyDownRename={(e) => handleKeyDownRename(e, conversation.conversationId)}
                          onSelect={() => openConversation(conversation.conversationId)}
                          onTogglePin={() => togglePinConversation(conversation.conversationId)}
                          onStartRename={() => handleStartRename(conversation.conversationId, conversation.title)}
                          onRequestDelete={() => setConfirmDeleteId(conversation.conversationId)}
                        />
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}
          </section>

          {/* 功能配置板块 */}
          <section className="border-t border-[#eceaf0] px-4 py-3" aria-label="功能配置">
            <p className="mb-2 px-1 text-[11px] font-semibold uppercase tracking-[0.12em] text-[#9a98a6]">功能配置</p>
            <div className="grid grid-cols-2 gap-1.5">
              {utilityNavigation.map(({ icon: Icon, label, to }) =>
                to ? (
                  <NavLink
                    key={label}
                    to={to}
                    className="flex min-w-0 items-center gap-1.5 rounded-lg px-2 py-1.5 text-xs text-[#656370] transition-colors hover:bg-[#f1f0f5]"
                  >
                    <Icon className="size-3.5 shrink-0 text-[#8d87d5]" aria-hidden="true" />
                    <span className="truncate">{label}</span>
                  </NavLink>
                ) : (
                  <Tooltip key={label}>
                    <TooltipTrigger asChild>
                      <button
                        type="button"
                        className="flex min-w-0 items-center gap-1.5 rounded-lg px-2 py-1.5 text-left text-xs text-[#656370] transition-colors hover:bg-[#f1f0f5]"
                      >
                        <Icon className="size-3.5 shrink-0 text-[#8d87d5]" aria-hidden="true" />
                        <span className="truncate">{label}</span>
                      </button>
                    </TooltipTrigger>
                    <TooltipContent side="right">页面将在后续阶段接入</TooltipContent>
                  </Tooltip>
                ),
              )}
            </div>
          </section>

          {/* 底部用户中心卡片 */}
          <div className="flex items-center gap-2.5 border-t border-[#eceaf0] px-5 py-3">
            <span className="flex size-7 items-center justify-center rounded-full bg-gradient-to-br from-[#f0b3a5] to-[#ce856f] text-[10px] font-semibold text-white">
              N
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-xs font-medium text-[#4c4a55]">Nexa User</p>
              <p className="text-[10px] text-[#9a98a6]">个人工作区</p>
            </div>
            <ChevronDown className="size-3.5 text-[#aaa8b4]" />
          </div>
        </aside>

        {/* 主画布内容区 */}
        <main className="relative min-w-0 flex-1">
          {/* 侧边栏折叠时的浮动展开按钮 */}
          {!sidebarOpen && (
            <Tooltip>
              <TooltipTrigger asChild>
                <button
                  type="button"
                  onClick={() => setSidebarOpen(true)}
                  className="absolute left-4 top-4 z-20 flex size-9 items-center justify-center rounded-xl border border-[#e8e7ee] bg-white text-[#757280] shadow-sm transition-colors hover:bg-[#f4f2f8] hover:text-[#6256da]"
                  aria-label="展开侧边栏"
                >
                  <PanelLeftOpen className="size-4" />
                </button>
              </TooltipTrigger>
              <TooltipContent side="right">展开侧边栏</TooltipContent>
            </Tooltip>
          )}
          <Outlet />
        </main>
      </div>

      {/* 删除对话确认 Dialog */}
      <Dialog open={!!confirmDeleteId} onOpenChange={(open) => !open && setConfirmDeleteId(null)}>
        <DialogContent className="max-w-md bg-white">
          <DialogHeader>
            <DialogTitle className="text-base font-semibold text-slate-900">确认删除该对话？</DialogTitle>
            <DialogDescription className="mt-2 text-xs text-slate-500">
              删除后，该对话及其关联的历史聊天消息将被移除，且无法恢复。
            </DialogDescription>
          </DialogHeader>
          <div className="mt-4 flex justify-end gap-2.5">
            <Button
              variant="outline"
              size="sm"
              disabled={isDeleting}
              onClick={() => setConfirmDeleteId(null)}
              className="rounded-lg text-xs"
            >
              取消
            </Button>
            <Button
              variant="destructive"
              size="sm"
              disabled={isDeleting}
              onClick={() => void handleConfirmDelete()}
              className="rounded-lg bg-red-600 text-xs hover:bg-red-700"
            >
              {isDeleting ? '正在删除…' : '确认删除'}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </TooltipProvider>
  )
}

interface ConversationListItemRowProps {
  conversation: { conversationId: string; title: string | null }
  isSelected: boolean
  isEditing: boolean
  isPinned: boolean
  editTitle: string
  onEditTitleChange: (val: string) => void
  onSaveRename: () => void
  onCancelRename: () => void
  onKeyDownRename: (e: KeyboardEvent<HTMLInputElement>) => void
  onSelect: () => void
  onTogglePin: () => void
  onStartRename: () => void
  onRequestDelete: () => void
}

function ConversationListItemRow({
  conversation,
  isSelected,
  isEditing,
  isPinned,
  editTitle,
  onEditTitleChange,
  onSaveRename,
  onCancelRename,
  onKeyDownRename,
  onSelect,
  onTogglePin,
  onStartRename,
  onRequestDelete,
}: ConversationListItemRowProps) {
  if (isEditing) {
    return (
      <div className="flex items-center gap-1.5 rounded-lg bg-[#eeecff] px-2.5 py-1.5">
        <input
          autoFocus
          value={editTitle}
          onChange={(e) => onEditTitleChange(e.target.value)}
          onKeyDown={onKeyDownRename}
          className="min-w-0 flex-1 bg-transparent text-xs text-[#5649ce] outline-none"
        />
        <button
          type="button"
          title="保存"
          onClick={onSaveRename}
          className="text-[#6b5ce7] hover:text-[#5243d4]"
        >
          <Check className="size-3.5" />
        </button>
        <button type="button" title="取消" onClick={onCancelRename} className="text-gray-400 hover:text-gray-600">
          <X className="size-3.5" />
        </button>
      </div>
    )
  }

  return (
    <div
      onClick={onSelect}
      className={cn(
        'group relative flex cursor-pointer items-center justify-between rounded-lg px-2.5 py-2 text-left text-[13px] transition-colors',
        isSelected ? 'bg-[#eeecff] text-[#5649ce]' : 'text-[#565460] hover:bg-[#f1f0f5]',
      )}
    >
      <div className="flex min-w-0 items-center gap-2">
        <span
          className={cn('size-1.5 shrink-0 rounded-full', isSelected ? 'bg-[#7166f7]' : 'bg-[#d1ceda]')}
        />
        <span className="truncate">{conversation.title || '未命名会话'}</span>
      </div>

      {/* 悬浮 `...` 更多操作下拉菜单 */}
      <div onClick={(e: MouseEvent) => e.stopPropagation()}>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button
              type="button"
              className={cn(
                'flex size-6 items-center justify-center rounded-md text-[#9a98a6] hover:bg-[#e4e2f0] hover:text-[#5649ce] opacity-0 group-hover:opacity-100 transition-opacity',
                isSelected && 'text-[#6b5ce7]',
              )}
              aria-label="更多操作"
            >
              <MoreHorizontal className="size-3.5" />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-36">
            <DropdownMenuItem onClick={onTogglePin} className="gap-2 text-xs cursor-pointer">
              {isPinned ? <PinOff className="size-3.5 text-slate-500" /> : <Pin className="size-3.5 text-slate-500" />}
              {isPinned ? '取消置顶' : '置顶对话'}
            </DropdownMenuItem>
            <DropdownMenuItem onClick={onStartRename} className="gap-2 text-xs cursor-pointer">
              <Pencil className="size-3.5 text-slate-500" />
              重命名
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={onRequestDelete} className="gap-2 text-xs text-red-600 focus:text-red-600 focus:bg-red-50 cursor-pointer">
              <Trash2 className="size-3.5 text-red-600" />
              删除对话
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </div>
  )
}
