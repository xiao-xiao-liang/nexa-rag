import React, { useMemo } from "react";
import { ChatMessageVO } from "../../types";
import { FeishuCopyButton } from "./FeishuCopyButton";
import { AgentToolExecutionBox } from "./AgentToolExecutionBox";
import { FeishuChatSkeleton } from "./FeishuChatSkeleton";
import { FeishuMarkdown, parseFeishuMessageContent } from "./markdown";
import { FileLinkBitableOutlinedIcon } from "./FeishuChatIcons";
import { shouldShowToolExecutionBox } from "./tool-execution-state";

export interface ChatMessageItemProps {
  message: ChatMessageVO;
  index?: number;
  isGenerating?: boolean;
  isCopied?: boolean;
  onCopy?: (msgId: string, text: string) => void;
  elapsedSeconds?: number;
}

/** 格式化飞书消息时间戳：当天显示 HH:mm，非当天显示 MM月DD日 HH:mm (两位月份与两位日期) */
function formatFeishuMessageTime(timeStr?: string | number): string {
  if (!timeStr) return "刚刚";

  // 如果已经是标准格式 "08月15日 20:55"
  if (typeof timeStr === "string" && /^\d{2}月\d{2}日\s+\d{2}:\d{2}$/.test(timeStr)) {
    return timeStr;
  }

  try {
    let d: Date | null = null;

    if (typeof timeStr === "number") {
      d = new Date(timeStr);
    } else if (typeof timeStr === "string") {
      const trimmed = timeStr.trim();

      // 如果仅为时间且长度 <= 5 (如 "18:15", "8:05") -> 默认为当天时间
      if (/^\d{1,2}:\d{2}$/.test(trimmed)) {
        const [h, m] = trimmed.split(":");
        return `${h.padStart(2, "0")}:${m.padStart(2, "0")}`;
      }

      // 纯数字时间戳字符串 (如 "1787220974620")
      if (/^\d{10,13}$/.test(trimmed)) {
        d = new Date(parseInt(trimmed, 10));
      } else {
        // 匹配 "8/18 20:55", "08/18 20:55", "8-18 20:55", "08月18日 20:55"
        const customMatch = trimmed.match(/^(\d{1,2})[\/\-月](\d{1,2})日?\s+(\d{1,2}):(\d{1,2})(?::\d{1,2})?$/);
        if (customMatch) {
          const currentYear = new Date().getFullYear();
          const m = parseInt(customMatch[1], 10) - 1;
          const day = parseInt(customMatch[2], 10);
          const hh = parseInt(customMatch[3], 10);
          const mm = parseInt(customMatch[4], 10);
          d = new Date(currentYear, m, day, hh, mm);
        } else {
          d = new Date(trimmed);
        }
      }
    }

    if (!d || isNaN(d.getTime())) {
      return String(timeStr);
    }

    const now = new Date();
    const isToday =
      d.getFullYear() === now.getFullYear() &&
      d.getMonth() === now.getMonth() &&
      d.getDate() === now.getDate();

    const hh = String(d.getHours()).padStart(2, "0");
    const mm = String(d.getMinutes()).padStart(2, "0");

    if (isToday) {
      return `${hh}:${mm}`;
    }

    const month = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");
    return `${month}月${day}日 ${hh}:${mm}`;
  } catch {
    return String(timeStr);
  }
}

/**
 * 1:1 飞书统一消息项渲染组件 (严格对齐 index.html 与 style.css 原生 DOM 规范)
 */
export const ChatMessageItem: React.FC<ChatMessageItemProps> = ({
  message,
  isGenerating = false,
  isCopied = false,
  onCopy,
  elapsedSeconds = 1,
}) => {
  // 大小写安全匹配用户角色 (兼容 "user"、"USER"、"User")
  const roleStr = (message.role || "").toLowerCase();
  const isUser = roleStr === "user";
  // 助手占位消息在流式生成开始前可能尚未持有正文，统一按空文本处理。
  const content = message.content ?? "";

  const hasMention = content.startsWith("@");
  const cleanContent = hasMention
    ? content.replace(/^@[^\s]+\s*/, "")
    : content;
  const mentionText = hasMention
    ? content.match(/^@([^\s]+)/)?.[1]
    : null;

  const displayTime = formatFeishuMessageTime(message.createdTime);

  // 解析飞书消息内容（兼容 ops 结构体与纯 Markdown）
  const parsed = useMemo(() => {
    return parseFeishuMessageContent(content);
  }, [content]);

  // 提取需要显示的纯文本（用于一键复制）
  const copyableText = parsed.markdownContent || content;
  const operations = message.operations ?? [];

  const handleCopyText = (text: string) => {
    if (onCopy) {
      onCopy(message.messageId, text);
    } else {
      navigator.clipboard.writeText(text);
    }
  };

  // =========================================================================
  // 1. 用户消息：右侧飞书淡蓝气泡 (#F0F4FF) + 实体 Mention 胶囊 + 悬停右对齐时间/复制
  // =========================================================================
  if (isUser) {
    return (
      <div className="group flex flex-col items-end gap-[4px] self-stretch ml-12 select-none">
        {/* 用户气泡 (1:1 飞书: bg-[#F0F4FF], rounded-[10px], px-3 py-[9px], text-[14px], leading-[22px]) */}
        <div className="rounded-[10px] bg-[#F0F4FF] px-3 py-[9px] text-[14px] leading-[22px] text-[#1F2329] max-w-[85%] whitespace-pre-wrap break-words flex items-center gap-1 flex-wrap shadow-2xs">
          {mentionText && (
            <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-[4px] bg-white border border-[#B3D4FF] text-[13px] text-[#1F2329] font-medium mr-1 select-none shadow-2xs">
              <FileLinkBitableOutlinedIcon className="w-3.5 h-3.5 text-[#3370FF]" />
              <span>{mentionText}</span>
            </span>
          )}
          <span>{cleanContent}</span>
        </div>

        {/* 用户消息底部操作栏 (默认透明，hover 显现：右对齐，先时间后复制按钮) */}
        <div className="flex items-center gap-1.5 h-5 opacity-0 group-hover:opacity-100 transition-opacity duration-150 pr-1">
          <span className="text-[12px] leading-[20px] text-[rgba(31,35,41,0.6)] select-none">
            {displayTime}
          </span>
          <FeishuCopyButton
            onCopy={() => handleCopyText(content)}
            isCopied={isCopied}
          />
        </div>
      </div>
    );
  }

  const hasOperations = Boolean(operations && operations.length > 0);
  const showToolBox = shouldShowToolExecutionBox(operations, isGenerating);

  // =========================================================================
  // 2. 智能助手消息：左侧通透排版 + 工具链执行折叠卡 + 1:1 FeishuMarkdown 渲染器
  // =========================================================================
  return (
    <div className="group space-y-1 select-none pr-12">
      {showToolBox && (
        <AgentToolExecutionBox
          defaultOpen
          messageStatus={message.status}
          operations={operations}
        />
      )}

      {!content && isGenerating ? (
        <FeishuChatSkeleton
          elapsedSeconds={elapsedSeconds}
          showHeader={!hasOperations}
        />
      ) : (

        <>
          {/* 正文：严格 1:1 飞书官方 Markdown Component Renderer */}
          <FeishuMarkdown
            content={content}
            isGenerating={isGenerating}
          />

          {/* 助手消息底部操作栏 (默认透明，hover 显现：左对齐，先复制按钮后时间) */}
          <div className="flex items-center gap-1.5 h-5 opacity-0 group-hover:opacity-100 transition-opacity duration-150 pt-1">
            <FeishuCopyButton
              onCopy={() => handleCopyText(copyableText)}
              isCopied={isCopied}
            />
            <span className="text-[12px] leading-[20px] text-[rgba(31,35,41,0.6)] select-none">
              {displayTime}
            </span>
          </div>
        </>
      )}
    </div>
  );
};
