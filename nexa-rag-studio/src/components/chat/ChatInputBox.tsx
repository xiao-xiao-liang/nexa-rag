import React, { useRef, useState, useEffect, useLayoutEffect, useCallback } from "react";
import { Plus, AtSign, Mic, Square } from "lucide-react";
import { FeishuTooltip } from "../ui/tooltip";
import { SendFilledIcon } from "./FeishuChatIcons";
import { cn } from "@/lib/utils.ts";

export interface ChatInputBoxProps {
  value: string;
  onChange: (value: string) => void;
  onSend: () => void;
  onCancel?: () => void;
  isGenerating?: boolean;
  placeholder?: string;
  onAddAttachment?: () => void;
  onVoiceInput?: () => void;
}

/** 1:1 飞书 Floating Omnibox 输入框 (支持内容自适应撑起、2s自动隐藏滚动条、@实体、+附件、快捷键与生成控制) */
export const ChatInputBox: React.FC<ChatInputBoxProps> = ({
                                                            value,
                                                            onChange,
                                                            onSend,
                                                            onCancel,
                                                            isGenerating = false,
                                                            placeholder = "输入你的问题，按 Enter 发送，可通过 @ 引用人员、文档、数据表和技能",
                                                            onAddAttachment,
                                                            onVoiceInput,
                                                          }) => {
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const [isScrollbarVisible, setIsScrollbarVisible] = useState(false);
  const hideTimerRef = useRef<NodeJS.Timeout | null>(null);

  // 显现滚动条并在 1s 静止或移出后自动淡出消失
  const showScrollbarWithTimer = useCallback(() => {
    const textarea = textareaRef.current;
    if (!textarea) return;

    if (textarea.scrollHeight > textarea.clientHeight) {
      setIsScrollbarVisible(true);
      if (hideTimerRef.current) {
        clearTimeout(hideTimerRef.current);
      }
      hideTimerRef.current = setTimeout(() => {
        setIsScrollbarVisible(false);
      }, 1000);
    } else {
      setIsScrollbarVisible(false);
    }
  }, []);

  const handleMouseLeave = () => {
    if (hideTimerRef.current) {
      clearTimeout(hideTimerRef.current);
    }
    hideTimerRef.current = setTimeout(() => {
      setIsScrollbarVisible(false);
    }, 1000);
  };

  // 1:1 飞书文本框自适应撑起 (最小 44px 约 2 行，最大 240px 约 10 行，超出后呈现精细滚动条)
  const adjustHeight = useCallback(() => {
    const textarea = textareaRef.current;
    if (!textarea) return;
    textarea.style.height = "auto";
    const minHeight = 44;
    const maxHeight = 240;
    const scrollHeight = textarea.scrollHeight;
    const targetHeight = Math.min(Math.max(scrollHeight, minHeight), maxHeight);
    textarea.style.height = `${targetHeight}px`;
    textarea.style.overflowY = scrollHeight > maxHeight ? "auto" : "hidden";
  }, []);

  useLayoutEffect(() => {
    adjustHeight();
    showScrollbarWithTimer();
  }, [value, adjustHeight, showScrollbarWithTimer]);

  useEffect(() => {
    if (!isGenerating && textareaRef.current) {
      textareaRef.current.focus();
    }
  }, [isGenerating]);

  useEffect(() => {
    return () => {
      if (hideTimerRef.current) {
        clearTimeout(hideTimerRef.current);
      }
    };
  }, []);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      onSend();
    }
  };

  const handleAddAttachment = () => {
    if (onAddAttachment) {
      onAddAttachment();
    } else {
      alert("触发添加附件/技能组件");
    }
  };

  const handleMention = () => {
    onChange(`${value}@`);
    if (textareaRef.current) {
      textareaRef.current.focus();
    }
  };

  const handleVoice = () => {
    if (onVoiceInput) {
      onVoiceInput();
    } else {
      alert("语音输入准备中");
    }
  };

  return (
    <div className="px-7 pb-4 pt-0 shrink-0 bg-white select-none">
      <div
        onMouseMove={showScrollbarWithTimer}
        onMouseLeave={handleMouseLeave}
        className="max-w-190 mx-auto rounded-[18px] border border-[#DEE0E3] bg-white p-3.5 transition-all shadow-[0_2px_12px_rgba(0,0,0,0.03)]"
      >
        <textarea
          ref={textareaRef}
          rows={2}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={handleKeyDown}
          onScroll={showScrollbarWithTimer}
          onFocus={showScrollbarWithTimer}
          placeholder={placeholder}
          disabled={isGenerating}
          style={{ minHeight: "44px" }}
          className={cn(
            "w-full resize-none bg-transparent text-[14px] leading-5.5 text-feishu-text-primary outline-none feishu-chat-textarea transition-all",
            !isScrollbarVisible && "scrollbar-hidden"
          )}
        />

        <div className="flex items-center justify-between pt-2 mt-1">
          <div className="flex items-center gap-3 text-[#646A73]">
            <FeishuTooltip title="添加附件或技能" side="top" sideOffset={6}>
              <button
                type="button"
                className="hover:text-feishu-text-primary p-0.5 rounded transition-colors cursor-pointer"
                onClick={handleAddAttachment}
              >
                <Plus className="w-4 h-4 stroke-[2.2]" />
              </button>
            </FeishuTooltip>
            <FeishuTooltip title="引用人员、文档或数据表" side="top" sideOffset={6}>
              <button
                type="button"
                className="hover:text-feishu-text-primary p-0.5 rounded transition-colors cursor-pointer"
                onClick={handleMention}
              >
                <AtSign className="w-4 h-4 stroke-[2.2]" />
              </button>
            </FeishuTooltip>
          </div>

          <div className="flex items-center gap-2">
            <FeishuTooltip title="语音输入" side="top" sideOffset={6}>
              <button
                type="button"
                onClick={handleVoice}
                className="text-[#646A73] hover:text-feishu-text-primary p-1 rounded transition-colors cursor-pointer"
              >
                <Mic className="w-4 h-4" />
              </button>
            </FeishuTooltip>

            {isGenerating ? (
              <button
                type="button"
                onClick={onCancel}
                className="w-7 h-7 rounded-full bg-[#F54A45] hover:bg-[#D83B36] text-white flex items-center justify-center cursor-pointer transition-colors"
                title="停止生成"
              >
                <Square className="w-3 h-3 fill-current" />
              </button>
            ) : (
              <button
                type="button"
                onClick={onSend}
                disabled={!value.trim()}
                className={`w-7 h-7 rounded-full flex items-center justify-center transition-colors cursor-pointer ${
                  value.trim()
                    ? "bg-feishu-blue hover:bg-[#2A62EA] text-white"
                    : "bg-[#F2F3F5] text-[#C9CDD4] cursor-not-allowed"
                }`}
                title="发送"
              >
                <SendFilledIcon className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
