import React, { useState, useRef, useEffect } from "react";
import {
  MoreHorizontal,
  Edit2,
  Trash2,
  Check,
  X,
} from "lucide-react";
import { ChatConversationVO } from "../../types";
import { FEISHU_FONT_FAMILY } from "../ui/feishu-table";
import { FeishuTooltip } from "../ui/tooltip";
import { FeishuInput, FeishuInputRef } from "../ui/feishu-table/FeishuInput";
import { cn } from "../../lib/utils";

export interface ConversationSidebarProps {
  conversations: ChatConversationVO[];
  activeId: string | null;
  onSelect: (conversationId: string) => void;
  onNew: () => void;
  onRename: (conversationId: string, newTitle: string) => Promise<void>;
  onDelete: (conversationId: string) => Promise<void>;
  isCollapsed: boolean;
  onToggleCollapse: () => void;
}

// 1:1 飞书官方 SideExpandOutlined 展开/收起图标
const SideExpandIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
    <path
      d="M22 4a1 1 0 0 0-1-1H3a1 1 0 0 0 0 2h18a1 1 0 0 0 1-1Zm-11.111 7c.614 0 1.111.448 1.111 1s-.498 1-1.111 1H3.11C2.497 13 2 12.552 2 12s.497-1 1.111-1h7.778ZM12 20c0-.552-.498-1-1.111-1H3.11C2.497 19 2 19.448 2 20s.497 1 1.111 1h7.778c.614 0 1.111-.448 1.111-1Zm3.41-3.136a1.117 1.117 0 0 1 0-1.729l4.951-3.917c.675-.534 1.639-.026 1.639.865v7.834c0 .89-.964 1.4-1.639.865l-4.951-3.918Z"
      fill="currentColor"
    />
  </svg>
);

// 1:1 飞书官方 AddChatAiOutlined 新对话图标
const AddChatAiIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
    <path
      d="m2.76 19.317.98-1.437A9.91 9.91 0 0 1 1 11C1 5.477 5.5 1 11.05 1h.183c2.428 0 4.65.908 6.359 2.41a10.015 10.015 0 0 1 2.943 4.28c.017.05.342 1.424.438 2.674l-1.971-.822a17.978 17.978 0 0 0-.246-1.286A8.113 8.113 0 0 0 16.4 4.872l-.02-.017a7.762 7.762 0 0 0-5.147-1.95h-.184C6.476 2.905 2.86 6.58 2.86 11c0 .152.005.302.013.451v.006a7.984 7.984 0 0 0 2.2 5.094l1.073 1.129L5.18 19H11v2H3.655c-.869 0-1.384-.968-.896-1.683Z"
      fill="currentColor"
    />
    <path
      d="M18 12a1 1 0 0 1 1 1v3h3a1 1 0 1 1 0 2h-3v3a1 1 0 1 1-2 0v-3h-3a1 1 0 1 1 0-2h3v-3a1 1 0 0 1 1-1Z"
      fill="currentColor"
    />
  </svg>
);

// 1:1 飞书官方 SearchOutlined 搜索图标
const FeishuSearchIcon = ({ className }: { className?: string }) => (
  <svg
    width="16"
    height="16"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={cn("w-3.5 h-3.5 shrink-0", className)}
    data-icon="SearchOutlined"
  >
    <path
      d="M16.473 17.887A9.46 9.46 0 0 1 10.5 20a9.5 9.5 0 1 1 9.5-9.5 9.46 9.46 0 0 1-2.113 5.973l3.773 3.773a.996.996 0 0 1-.007 1.407.996.996 0 0 1-1.407.007l-3.773-3.773ZM18 10.5a7.5 7.5 0 1 0-15 0 7.5 7.5 0 0 0 15 0Z"
      fill="currentColor"
    />
  </svg>
);

/**
 * 1:1 飞书「知识问答 / 会话列表」设计规范实现 (纯白背景 + 同行向左平滑展开搜索)
 */
export const ConversationSidebar: React.FC<ConversationSidebarProps> = ({
  conversations,
  activeId,
  onSelect,
  onNew,
  onRename,
  onDelete,
  isCollapsed,
  onToggleCollapse,
}) => {
  const [searchKeyword, setSearchKeyword] = useState("");
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editTitle, setEditTitle] = useState("");
  const [menuOpenId, setMenuOpenId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const searchContainerRef = useRef<HTMLDivElement>(null);
  const searchInputRef = useRef<FeishuInputRef>(null);

  // 点击外部且无搜索词时平滑收起搜索框
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (
        searchContainerRef.current &&
        !searchContainerRef.current.contains(e.target as Node)
      ) {
        if (!searchKeyword.trim()) {
          setIsSearchOpen(false);
        }
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [searchKeyword]);

  const handleToggleSearch = () => {
    setIsSearchOpen(true);
    setTimeout(() => searchInputRef.current?.focus(), 80);
  };

  const handleClearSearch = () => {
    setSearchKeyword("");
    setIsSearchOpen(false);
  };

  const filteredConversations = conversations.filter((c) =>
    (c.title || "").toLowerCase().includes(searchKeyword.toLowerCase())
  );

  const handleStartRename = (conv: ChatConversationVO, e: React.MouseEvent) => {
    e.stopPropagation();
    setEditingId(conv.conversationId);
    setEditTitle(conv.title);
    setMenuOpenId(null);
  };

  const handleSaveRename = async (convId: string, e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (editTitle.trim()) {
      await onRename(convId, editTitle.trim());
    }
    setEditingId(null);
  };

  const handleCancelRename = () => {
    setEditingId(null);
    setEditTitle("");
  };

  const handleDeleteConfirm = async (convId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    await onDelete(convId);
    setDeletingId(null);
    setMenuOpenId(null);
  };

  // 1. 折叠态 (Mini Bar)
  if (isCollapsed) {
    return (
      <div
        style={{ fontFamily: FEISHU_FONT_FAMILY }}
        className="w-[52px] h-full bg-white border-r border-[#EFF0F1] flex flex-col items-center py-3 shrink-0 select-none z-20"
      >
        <FeishuTooltip title="展开侧边栏" side="right">
          <button
            type="button"
            onClick={onToggleCollapse}
            className="w-8 h-8 rounded-[6px] hover:bg-[#F2F3F5] active:scale-[0.96] text-[#646A73] hover:text-[#1F2329] flex items-center justify-center transition-all duration-100 cursor-pointer"
          >
            <SideExpandIcon />
          </button>
        </FeishuTooltip>

        <div className="w-6 h-[1px] bg-[#EFF0F1] my-2.5" />

        <FeishuTooltip title="新对话" side="right">
          <button
            type="button"
            onClick={onNew}
            className={`w-8 h-8 rounded-[6px] active:scale-[0.96] flex items-center justify-center transition-all duration-100 cursor-pointer ${
              activeId === null
                ? "bg-[#EFF0F1] text-[#1F2329]"
                : "text-[#646A73] hover:text-[#1F2329] hover:bg-[#F2F3F5]"
            }`}
          >
            <AddChatAiIcon />
          </button>
        </FeishuTooltip>
      </div>
    );
  }

  return (
    <aside
      style={{ fontFamily: FEISHU_FONT_FAMILY }}
      className="w-[280px] h-full bg-white border-r border-[#EFF0F1] flex flex-col shrink-0 select-none z-20"
    >
      {/* 1. Header 标题区 (1:1 飞书 header-scblcY / title-XEJZ6G) */}
      <div className="h-[52px] px-4 flex items-center justify-between shrink-0">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onToggleCollapse}
            className="w-7 h-7 rounded-[6px] hover:bg-[#F2F3F5] active:scale-[0.96] text-[#646A73] hover:text-[#1F2329] flex items-center justify-center transition-all duration-100 cursor-pointer"
          >
            <SideExpandIcon />
          </button>
          <span className="text-[14px] font-semibold text-[#1F2329]">
            飞书知识问答
          </span>
        </div>
      </div>

      {/* 2. 主操作入口: 「新对话」按钮 (1:1 飞书 navigationItem-T03oiD + 按压缩放动效) */}
      <div className="px-3 pb-2">
        <button
          type="button"
          onClick={onNew}
          className={`w-full h-[40px] px-3 rounded-[8px] flex items-center gap-2.5 text-[14px] font-medium active:scale-[0.98] transition-all duration-100 cursor-pointer ${
            activeId === null
              ? "bg-[#EFF0F1] text-[#1F2329]"
              : "text-[#1F2329] hover:bg-[#F2F3F5]"
          }`}
        >
          <div className="w-[18px] h-[18px] text-[#646A73] flex items-center justify-center shrink-0">
            <AddChatAiIcon />
          </div>
          <span>新对话</span>
        </button>
      </div>

      {/* 3. 历史对话标题行：同行向左平滑展开搜索 (最左端紧贴文字"历史对话"右边) */}
      <div ref={searchContainerRef} className="px-3 pt-1 shrink-0">
        <div className="h-[1px] bg-[#EFF0F1] my-1" />

        <div className="h-[34px] px-1 flex items-center justify-between gap-2 select-none relative">
          {/* 左侧文字 "历史对话" */}
          <span className="text-[12px] text-[#8F959E] font-medium shrink-0 pl-1">
            历史对话
          </span>

          {/* 右侧向左展开的搜索区域 */}
          <div
            className={cn(
              "flex items-center justify-end transition-all duration-200 ease-out",
              isSearchOpen ? "flex-1 min-w-0" : "w-[24px]"
            )}
          >
            {isSearchOpen ? (
              <div className="w-full animate-in fade-in-50 slide-in-from-right-4 duration-200">
                <FeishuInput
                  ref={searchInputRef}
                  value={searchKeyword}
                  onChange={(val) => setSearchKeyword(val)}
                  onClear={handleClearSearch}
                  placeholder="搜索历史对话..."
                  allowClear={true}
                  prefix={<FeishuSearchIcon className="text-[#8F959E]" />}
                  containerClassName="w-full h-[28px]"
                  inputClassName="text-[12px]"
                />
              </div>
            ) : (
              <button
                type="button"
                onClick={handleToggleSearch}
                className="flex items-center justify-center w-6 h-6 rounded-[4px] text-[#8F959E] hover:text-[#1F2329] hover:bg-[#EFF0F1] active:scale-[0.96] cursor-pointer transition-all duration-150"
                title="搜索历史对话"
              >
                <FeishuSearchIcon />
              </button>
            )}
          </div>
        </div>
      </div>

      {/* 4. 历史会话列表 (1:1 飞书 topicItemWrap-IFj5Ny + 按压缩放动效) */}
      <div className="flex-1 overflow-y-auto px-2 py-1 standard-scrollbar space-y-0.5 mt-0.5">
        {filteredConversations.length === 0 ? (
          <div className="py-8 text-center text-[12px] text-[#8F959E]">
            {searchKeyword ? "无匹配对话" : "暂无历史对话"}
          </div>
        ) : (
          filteredConversations.map((conv) => {
            const isActive = activeId === conv.conversationId;
            const isEditing = editingId === conv.conversationId;

            return (
              <div
                key={conv.conversationId}
                onClick={() => !isEditing && onSelect(conv.conversationId)}
                className={`group relative flex items-center justify-between h-[38px] px-3 rounded-[8px] text-[14px] active:scale-[0.98] transition-all duration-100 cursor-pointer select-none ${
                  isActive
                    ? "bg-[#EFF0F1] text-[#1F2329] font-medium"
                    : "text-[#1F2329] hover:bg-[#F2F3F5]"
                }`}
              >
                {isEditing ? (
                  <form
                    onSubmit={(e) => handleSaveRename(conv.conversationId, e)}
                    className="flex items-center gap-1 w-full"
                    onClick={(e) => e.stopPropagation()}
                  >
                    <input
                      type="text"
                      value={editTitle}
                      onChange={(e) => setEditTitle(e.target.value)}
                      autoFocus
                      className="w-full h-6 px-1.5 text-[13px] bg-white border border-[#3370FF] rounded focus:outline-none text-[#1F2329]"
                      onKeyDown={(e) => {
                        if (e.key === "Escape") handleCancelRename();
                      }}
                    />
                    <button
                      type="submit"
                      className="p-1 text-[#3370FF] hover:bg-[#E8F3FF] active:scale-[0.96] rounded cursor-pointer transition-all"
                    >
                      <Check className="w-3.5 h-3.5" />
                    </button>
                    <button
                      type="button"
                      onClick={handleCancelRename}
                      className="p-1 text-[#8F959E] hover:bg-[#F2F3F5] active:scale-[0.96] rounded cursor-pointer transition-all"
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </form>
                ) : (
                  <>
                    <span className="truncate text-[14px] flex-1 min-w-0 pr-1" title={conv.title}>
                      {conv.title || "未命名对话"}
                    </span>

                    {/* 操作菜单按钮 (Hover 或菜单打开时显现) */}
                    <div
                      className={`relative shrink-0 transition-opacity ${
                        menuOpenId === conv.conversationId
                          ? "opacity-100"
                          : "opacity-0 group-hover:opacity-100"
                      }`}
                      onClick={(e) => e.stopPropagation()}
                    >
                      <button
                        type="button"
                        onClick={() =>
                          setMenuOpenId(
                            menuOpenId === conv.conversationId
                              ? null
                              : conv.conversationId
                          )
                        }
                        className="w-6 h-6 rounded hover:bg-[#DEE0E3]/60 active:scale-[0.96] text-[#646A73] flex items-center justify-center transition-all duration-100 cursor-pointer"
                      >
                        <MoreHorizontal className="w-3.5 h-3.5" />
                      </button>

                      {/* 飞书 Universe 浮层下拉菜单 */}
                      {menuOpenId === conv.conversationId && (
                        <>
                          <div
                            className="fixed inset-0 z-30"
                            onClick={() => setMenuOpenId(null)}
                          />
                          <div className="absolute right-0 top-7 w-32 bg-white rounded-[8px] border border-[#DEE0E3] shadow-[0_4px_16px_rgba(31,35,41,0.1)] py-1 z-40 animate-in fade-in zoom-in-95 duration-100">
                            <button
                              type="button"
                              onClick={(e) => handleStartRename(conv, e)}
                              className="w-full px-3 py-1.5 text-left text-[12px] text-[#1F2329] hover:bg-[#F2F3F5] flex items-center gap-2 transition-colors cursor-pointer"
                            >
                              <Edit2 className="w-3 h-3 text-[#646A73]" />
                              <span>重命名</span>
                            </button>
                            <div className="h-[1px] bg-[#EFF0F1] my-1" />
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                setDeletingId(conv.conversationId);
                                setMenuOpenId(null);
                              }}
                              className="w-full px-3 py-1.5 text-left text-[12px] text-[#F53F3F] hover:bg-[#FFF2F0] flex items-center gap-2 transition-colors cursor-pointer"
                            >
                              <Trash2 className="w-3 h-3 text-[#F53F3F]" />
                              <span>删除会话</span>
                            </button>
                          </div>
                        </>
                      )}
                    </div>
                  </>
                )}
              </div>
            );
          })
        )}
      </div>

      {/* 5. 删除确认模态弹窗 (飞书 Universe 风格) */}
      {deletingId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1F2329]/40 backdrop-blur-[1px]">
          <div className="w-full max-w-[340px] bg-white rounded-[10px] border border-[#DEE0E3] shadow-[0_8px_24px_rgba(31,35,41,0.12)] p-5 animate-in zoom-in-95 duration-100">
            <h4 className="text-[15px] font-semibold text-[#1F2329]">删除会话确认</h4>
            <p className="text-[13px] text-[#646A73] mt-2 leading-relaxed">
              确定要删除该会话吗？删除后该会话下的所有历史消息记录将无法恢复。
            </p>
            <div className="flex items-center justify-end gap-2 mt-5">
              <button
                type="button"
                onClick={() => setDeletingId(null)}
                className="h-[28px] px-3 rounded-[6px] border border-[#DEE0E3] bg-white hover:bg-[#F2F3F5] active:scale-[0.96] text-[13px] text-[#1F2329] transition-all cursor-pointer"
              >
                取消
              </button>
              <button
                type="button"
                onClick={(e) => handleDeleteConfirm(deletingId, e)}
                className="h-[28px] px-3 rounded-[6px] bg-[#F53F3F] hover:bg-[#E02020] active:scale-[0.96] text-[13px] text-white transition-all cursor-pointer"
              >
                确定删除
              </button>
            </div>
          </div>
        </div>
      )}
    </aside>
  );
};
