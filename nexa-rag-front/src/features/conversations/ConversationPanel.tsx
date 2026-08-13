import { useMemo, useState, type KeyboardEvent, type MouseEvent } from 'react'
import {
  Check, MoreHorizontal, PanelLeftClose, PanelLeftOpen, Pencil, Pin, PinOff, Plus, Search, Trash2, Workflow, X,
} from 'lucide-react'
import { NavLink, useNavigate, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Skeleton } from '@/components/ui/skeleton'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { useConversationNavigation } from '@/features/conversations/ConversationNavigationContext'
import { cn } from '@/lib/utils'

/** 对话模块面板：会话列表（搜索、置顶分组、重命名、删除），支持折叠。 */
export function ConversationPanel() {
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
  const [open, setOpen] = useState(true)
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
      // 重命名失败时静默恢复，保留用户输入
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
      // 删除失败保持当前状态
    } finally {
      setIsDeleting(false)
      setConfirmDeleteId(null)
    }
  }

  if (!open) {
    return (
      <aside aria-label="会话面板（已折叠）" className="flex w-8 shrink-0 flex-col items-center border-r border-border bg-card py-2">
        <Tooltip>
          <TooltipTrigger asChild>
            <button
              type="button"
              onClick={() => setOpen(true)}
              aria-label="展开会话面板"
              className="mt-1 flex size-7 items-center justify-center rounded text-tertiary transition-colors hover:bg-muted hover:text-primary"
            >
              <PanelLeftOpen className="size-4" />
            </button>
          </TooltipTrigger>
          <TooltipContent side="right">展开会话面板</TooltipContent>
        </Tooltip>
      </aside>
    )
  }

  return (
    <aside aria-label="会话列表" className="flex w-[232px] shrink-0 flex-col border-r border-border bg-card">
      {/* 头部：新建对话 + 收起 */}
      <div className="flex items-center px-3 pb-2 pt-3">
        <NavLink
          to="/chat"
          end
          className="flex h-8 flex-1 items-center justify-center gap-1.5 rounded-md bg-primary text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
        >
          <Plus className="size-3.5" />
          新建对话
        </NavLink>
        <Tooltip>
          <TooltipTrigger asChild>
            <button
              type="button"
              onClick={() => setOpen(false)}
              aria-label="收起会话面板"
              className="ml-1.5 flex size-7 shrink-0 items-center justify-center rounded text-tertiary transition-colors hover:bg-muted hover:text-primary"
            >
              <PanelLeftClose className="size-4" />
            </button>
          </TooltipTrigger>
          <TooltipContent side="right">收起会话面板</TooltipContent>
        </Tooltip>
      </div>

      {/* 搜索框 */}
      <div className="px-3 pb-3">
        <label className="flex h-8 items-center gap-2 rounded-md border border-border bg-muted px-2.5 text-tertiary focus-within:border-primary focus-within:ring-2 focus-within:ring-primary/30">
          <Search className="size-3.5" aria-hidden="true" />
          <input
            aria-label="搜索会话"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="搜索会话"
            className="min-w-0 flex-1 bg-transparent text-xs text-foreground outline-none placeholder:text-tertiary"
          />
        </label>
      </div>

      {/* 历史会话列表 */}
      <section className="min-h-0 flex-1 overflow-y-auto px-2.5 pb-4" aria-label="历史会话">
        <div className="mb-2 flex items-center justify-between px-2">
          <span className="text-xs font-medium text-tertiary">历史会话</span>
          <button
            type="button"
            aria-label="刷新会话"
            onClick={() => void refresh()}
            className="text-tertiary transition-colors hover:text-primary"
          >
            <Workflow className="size-3.5" />
          </button>
        </div>

        {loading && (
          <div className="space-y-2 px-2">
            <Skeleton className="h-8 w-full rounded-md" />
            <Skeleton className="h-8 w-4/5 rounded-md" />
            <Skeleton className="h-8 w-11/12 rounded-md" />
          </div>
        )}

        {error && (
          <div className="mx-1 rounded-md bg-danger-light p-2.5 text-xs leading-5 text-danger">
            <p>{error}</p>
            <button type="button" className="mt-1 font-medium underline" onClick={() => void refresh()}>
              重新加载
            </button>
          </div>
        )}

        {!loading && !error && filteredConversations.length === 0 && (
          <p className="px-2 py-5 text-xs text-tertiary">{search ? '没有匹配的会话' : '从一段新对话开始'}</p>
        )}

        {!loading && !error && filteredConversations.length > 0 && (
          <div className="space-y-4">
            {pinnedItems.length > 0 && (
              <div>
                <p className="flex items-center gap-1 px-2 pb-1.5 text-xs font-medium text-tertiary">
                  <Pin className="size-3 text-primary" /> 置顶对话
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

            {todayItems.length > 0 && (
              <div>
                <p className="px-2 pb-1.5 text-xs font-medium text-tertiary">今天</p>
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

            {earlierItems.length > 0 && (
              <div>
                <p className="px-2 pb-1.5 text-xs font-medium text-tertiary">更早</p>
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

      {/* 删除对话确认 Dialog */}
      <Dialog open={!!confirmDeleteId} onOpenChange={(openDialog) => !openDialog && setConfirmDeleteId(null)}>
        <DialogContent className="max-w-md bg-card">
          <DialogHeader>
            <DialogTitle className="text-base font-semibold text-foreground">确认删除该对话？</DialogTitle>
            <DialogDescription className="mt-2 text-xs text-secondary">
              删除后，该对话及其关联的历史聊天消息将被移除，且无法恢复。
            </DialogDescription>
          </DialogHeader>
          <div className="mt-4 flex justify-end gap-2.5">
            <Button
              variant="outline"
              size="sm"
              disabled={isDeleting}
              onClick={() => setConfirmDeleteId(null)}
              className="rounded text-xs"
            >
              取消
            </Button>
            <Button
              variant="danger"
              size="sm"
              disabled={isDeleting}
              onClick={() => void handleConfirmDelete()}
              className="rounded text-xs"
            >
              {isDeleting ? '正在删除…' : '确认删除'}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </aside>
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
      <div className="flex items-center gap-1.5 rounded-md bg-primary-light px-2.5 py-1.5">
        <input
          autoFocus
          value={editTitle}
          onChange={(e) => onEditTitleChange(e.target.value)}
          onKeyDown={onKeyDownRename}
          className="min-w-0 flex-1 bg-transparent text-xs text-primary outline-none"
        />
        <button
          type="button"
          title="保存"
          onClick={onSaveRename}
          className="text-primary hover:text-primary/80"
        >
          <Check className="size-3.5" />
        </button>
        <button type="button" title="取消" onClick={onCancelRename} className="text-tertiary hover:text-secondary">
          <X className="size-3.5" />
        </button>
      </div>
    )
  }

  return (
    <div
      onClick={onSelect}
      className={cn(
        'group relative flex cursor-pointer items-center justify-between rounded-md px-2.5 py-2 text-left text-[13px] transition-colors',
        isSelected ? 'bg-primary-light text-primary' : 'text-secondary hover:bg-muted',
      )}
    >
      <div className="flex min-w-0 items-center gap-2">
        <span
          className={cn('size-1.5 shrink-0 rounded-full', isSelected ? 'bg-primary' : 'bg-border')}
        />
        <span className="truncate">{conversation.title || '未命名会话'}</span>
      </div>

      <div onClick={(e: MouseEvent) => e.stopPropagation()}>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button
              type="button"
              className={cn(
                'flex size-6 items-center justify-center rounded text-tertiary opacity-0 transition-opacity group-hover:opacity-100 hover:bg-muted hover:text-primary',
                isSelected && 'opacity-100 text-primary',
              )}
              aria-label="更多操作"
            >
              <MoreHorizontal className="size-3.5" />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-36">
            <DropdownMenuItem onClick={onTogglePin} className="gap-2 text-xs cursor-pointer">
              {isPinned ? <PinOff className="size-3.5 text-secondary" /> : <Pin className="size-3.5 text-secondary" />}
              {isPinned ? '取消置顶' : '置顶对话'}
            </DropdownMenuItem>
            <DropdownMenuItem onClick={onStartRename} className="gap-2 text-xs cursor-pointer">
              <Pencil className="size-3.5 text-secondary" />
              重命名
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={onRequestDelete} className="gap-2 text-xs text-danger focus:text-danger focus:bg-danger-light cursor-pointer">
              <Trash2 className="size-3.5 text-danger" />
              删除对话
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </div>
  )
}
