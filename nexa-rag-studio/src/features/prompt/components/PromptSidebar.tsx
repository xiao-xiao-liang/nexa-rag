import React, { useState, useMemo } from "react";
import {
  Search,
  Check,
  Copy,
  X,
  FileCode,
  Sparkles,
  GitBranch,
  MessageSquare,
  Box,
} from "lucide-react";
import { PromptResponse } from "../../../types";
import { FeishuPill } from "../../../components/ui/feishu-table";

interface PromptSidebarProps {
  prompts: PromptResponse[];
  selectedPrompt: PromptResponse | null;
  onSelectPrompt: (promptCode: string) => void;
  className?: string;
}

export const PromptSidebar: React.FC<PromptSidebarProps> = ({
  prompts,
  selectedPrompt,
  onSelectPrompt,
  className = "",
}) => {
  const [searchKeyword, setSearchKeyword] = useState<string>("");
  const [statusFilter, setStatusFilter] = useState<"ALL" | "ENABLED" | "DISABLED">("ALL");
  const [copiedCode, setCopiedCode] = useState<string | null>(null);
  const [collapsedGroups, setCollapsedGroups] = useState<Record<string, boolean>>({});

  const handleCopyCode = (e: React.MouseEvent, code: string) => {
    e.stopPropagation();
    navigator.clipboard.writeText(code);
    setCopiedCode(code);
    setTimeout(() => setCopiedCode(null), 2000);
  };

  const toggleGroup = (groupKey: string) => {
    setCollapsedGroups((prev) => ({
      ...prev,
      [groupKey]: !prev[groupKey],
    }));
  };

  // 过滤后的 Prompt 列表
  const filteredPrompts = useMemo(() => {
    return prompts.filter((p) => {
      const name = p.name || p.promptName || "";
      const code = p.promptCode || "";
      const matchKey =
        !searchKeyword.trim() ||
        name.toLowerCase().includes(searchKeyword.trim().toLowerCase()) ||
        code.toLowerCase().includes(searchKeyword.trim().toLowerCase());

      if (!matchKey) return false;
      if (statusFilter === "ENABLED") return p.enabled !== false;
      if (statusFilter === "DISABLED") return p.enabled === false;
      return true;
    });
  }, [prompts, searchKeyword, statusFilter]);

  // 按照链路分组分类（纯文字与专业线性图标分类，无廉价 Emoji）
  const groupedPrompts = useMemo(() => {
    const groups: {
      key: string;
      label: string;
      IconComponent: React.ComponentType<{ className?: string }>;
      items: PromptResponse[];
    }[] = [
      { key: "rag", label: "RAG 检索问答链路", IconComponent: Sparkles, items: [] },
      { key: "intent", label: "意图识别与重写", IconComponent: GitBranch, items: [] },
      { key: "session", label: "会话与辅助链路", IconComponent: MessageSquare, items: [] },
      { key: "other", label: "通用与全局模板", IconComponent: Box, items: [] },
    ];

    filteredPrompts.forEach((p) => {
      const code = (p.promptCode || "").toLowerCase();
      if (code.includes("answer") || code.includes("evidence") || code.includes("rag")) {
        groups[0].items.push(p);
      } else if (code.includes("intent") || code.includes("rewrite")) {
        groups[1].items.push(p);
      } else if (code.includes("title") || code.includes("summary")) {
        groups[2].items.push(p);
      } else {
        groups[3].items.push(p);
      }
    });

    return groups.filter((g) => g.items.length > 0);
  }, [filteredPrompts]);

  return (
    <aside
      className={`flex flex-col h-full bg-white border-r border-[#EFF0F1] select-none ${className}`}
    >
      {/* 1. 顶部搜索与状态筛选栏 */}
      <div className="p-3 pb-2.5 space-y-2.5 border-b border-[#EFF0F1] bg-white shrink-0">
        {/* 飞书标准 32px 纯白搜索框 */}
        <div className="relative flex items-center h-8 w-full rounded-[6px] bg-white px-2.5 text-[14px] text-[#646A73] transition-colors focus-within:ring-2 focus-within:ring-[#3370FF]/15 focus-within:border-[#3370FF] border border-[#DEE0E3]">
          <Search className="w-3.5 h-3.5 text-[#8F959E] shrink-0 mr-1.5" />
          <input
            type="text"
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            placeholder="搜索编码、模板名称…"
            className="w-full bg-transparent outline-none placeholder:text-[#8F959E] text-[#1F2329] text-[13px]"
          />
          {searchKeyword && (
            <button
              type="button"
              onClick={() => setSearchKeyword("")}
              className="flex h-5 w-5 items-center justify-center rounded-[4px] hover:bg-[#F2F3F5] text-[#8F959E] hover:text-[#1F2329] transition-colors cursor-pointer"
            >
              <X className="w-3 h-3" />
            </button>
          )}
        </div>

        {/* 飞书 1:1 胶囊筛选 (rounded-full) */}
        <div className="flex items-center gap-1.5">
          <button
            type="button"
            onClick={() => setStatusFilter("ALL")}
            className={`px-2.5 py-0.5 rounded-full text-[12px] font-medium transition-colors cursor-pointer ${
              statusFilter === "ALL"
                ? "bg-[#E8F3FF] text-[#3370FF]"
                : "text-[#646A73] hover:text-[#1F2329] hover:bg-[#F2F3F5]"
            }`}
          >
            全部 ({prompts.length})
          </button>
          <button
            type="button"
            onClick={() => setStatusFilter("ENABLED")}
            className={`px-2.5 py-0.5 rounded-full text-[12px] font-medium transition-colors cursor-pointer ${
              statusFilter === "ENABLED"
                ? "bg-[#E6F8F5] text-[#10A893]"
                : "text-[#646A73] hover:text-[#1F2329] hover:bg-[#F2F3F5]"
            }`}
          >
            启用 ({prompts.filter((p) => p.enabled !== false).length})
          </button>
          <button
            type="button"
            onClick={() => setStatusFilter("DISABLED")}
            className={`px-2.5 py-0.5 rounded-full text-[12px] font-medium transition-colors cursor-pointer ${
              statusFilter === "DISABLED"
                ? "bg-[#FFF2F0] text-[#F53F3F]"
                : "text-[#646A73] hover:text-[#1F2329] hover:bg-[#F2F3F5]"
            }`}
          >
            停用 ({prompts.filter((p) => p.enabled === false).length})
          </button>
        </div>
      </div>

      {/* 2. 飞书 CRM 1:1 标准树形导航列表 */}
      <div className="flex-1 overflow-y-auto px-2 py-2 space-y-2 bg-white">
        {groupedPrompts.length === 0 ? (
          <div className="py-12 text-center text-[13px] text-[#8F959E]">
            <span>未找到匹配的 Prompt</span>
          </div>
        ) : (
          groupedPrompts.map((group) => {
            const isCollapsed = collapsedGroups[group.key];
            const GroupIcon = group.IconComponent;

            return (
              <div key={group.key} className="space-y-0.5">
                {/* 飞书 1:1 分组头部 (小三角 + 纯文本，无廉价表情) */}
                <button
                  type="button"
                  onClick={() => toggleGroup(group.key)}
                  className="w-full flex items-center justify-between px-2 py-1.5 text-[12px] font-medium text-[#646A73] hover:bg-[#F2F3F5] rounded-[4px] transition-colors cursor-pointer"
                >
                  <div className="flex items-center gap-1.5 min-w-0">
                    <span className="flex w-3.5 h-3.5 items-center justify-center shrink-0">
                      <svg
                        className={`w-2.5 h-2.5 text-[#8F959E] fill-current transition-transform duration-150 ${
                          isCollapsed ? "-rotate-90" : "rotate-0"
                        }`}
                        viewBox="0 0 10 10"
                      >
                        <polygon points="1,3 9,3 5,8" />
                      </svg>
                    </span>
                    <GroupIcon className="w-3.5 h-3.5 text-[#8F959E] shrink-0" />
                    <span className="truncate text-[#1F2329] font-medium text-[12.5px]">
                      {group.label}
                    </span>
                  </div>

                  <span className="tabular-nums text-[12px] font-normal text-[#8F959E] pr-1">
                    {group.items.length}
                  </span>
                </button>

                {/* 飞书 CRM 标准条目 (无生硬绿圆点) */}
                {!isCollapsed && (
                  <div className="space-y-0.5 pl-2">
                    {group.items.map((p) => {
                      const isSelected = selectedPrompt?.promptCode === p.promptCode;
                      const displayName = p.name || p.promptName || p.promptCode;
                      const stableVer =
                        p.releases?.[0]?.stableVersionId ||
                        p.activeStableVersionId ||
                        p.versions?.[0]?.versionId ||
                        1;
                      const canaryVer =
                        p.releases?.[0]?.canaryVersionId || p.activeCanaryVersionId;
                      const canaryPct = p.canaryPercentage ?? 0;
                      const isEnabled = p.enabled !== false;

                      return (
                        <div
                          key={p.promptCode}
                          onClick={() => onSelectPrompt(p.promptCode)}
                          className={`group/item relative px-2.5 py-2 rounded-[6px] cursor-pointer transition-all duration-150 ${
                            isSelected
                              ? "bg-[#E8F3FF] text-[#3370FF]"
                              : "hover:bg-[#F2F3F5] text-[#1F2329] bg-white"
                          }`}
                        >
                          {/* 第一行：线性文件图标 + 名称 + 纯净版本胶囊 */}
                          <div className="flex items-center justify-between gap-1.5 min-w-0">
                            <div className="flex items-center gap-2 min-w-0 flex-1">
                              <FileCode
                                className={`w-3.5 h-3.5 shrink-0 transition-colors ${
                                  isSelected
                                    ? "text-[#3370FF]"
                                    : isEnabled
                                    ? "text-[#646A73]"
                                    : "text-[#8F959E]"
                                }`}
                              />
                              <span
                                className={`text-[13px] truncate ${
                                  isSelected
                                    ? "font-medium text-[#3370FF]"
                                    : isEnabled
                                    ? "font-normal text-[#1F2329]"
                                    : "font-normal text-[#8F959E]"
                                }`}
                                title={displayName}
                              >
                                {displayName}
                              </span>

                              {!isEnabled && (
                                <span className="text-[11px] text-[#8F959E] bg-[#F2F3F5] px-1 py-0.2 rounded shrink-0">
                                  停用
                                </span>
                              )}
                            </div>

                            {/* 版本徽章：采用清爽飞书胶囊 */}
                            <div className="shrink-0 flex items-center gap-1">
                              {canaryVer ? (
                                <FeishuPill variant="orange" showDot={false}>
                                  v{stableVer} / 灰{canaryPct}%
                                </FeishuPill>
                              ) : (
                                <span
                                  className={`text-[11px] px-1.5 py-0.5 rounded-[4px] transition-colors tabular-nums ${
                                    isSelected
                                      ? "bg-[#D0E5FF] text-[#1F4EC9] font-medium"
                                      : "bg-[#F2F3F5] text-[#646A73]"
                                  }`}
                                >
                                  v{stableVer}
                                </span>
                              )}
                            </div>
                          </div>

                          {/* 第二行：英文 code + 悬浮复制按钮 */}
                          <div className="flex items-center justify-between mt-1 pl-5.5 text-[11px] text-[#8F959E]">
                            <span className="truncate max-w-[150px]" title={p.promptCode}>
                              {p.promptCode}
                            </span>

                            <button
                              type="button"
                              onClick={(e) => handleCopyCode(e, p.promptCode)}
                              title="复制编码"
                              className="opacity-0 group-hover/item:opacity-100 flex h-5 w-5 items-center justify-center rounded-[4px] hover:bg-white text-[#8F959E] hover:text-[#1F2329] transition-all cursor-pointer shrink-0"
                            >
                              {copiedCode === p.promptCode ? (
                                <Check className="size-3 text-[#00B42A]" />
                              ) : (
                                <Copy className="size-3" />
                              )}
                            </button>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>
    </aside>
  );
};
