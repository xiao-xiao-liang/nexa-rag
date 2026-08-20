import React, { useRef, useEffect } from "react";
import { Plus, AtSign, Mic, Square } from "lucide-react";
import { FeishuTooltip } from "../ui/tooltip";
import { SendFilledIcon } from "./FeishuChatIcons";

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

/** 1:1 飞书 Floating Omnibox 输入框 (支持 @实体、+附件、快捷键与生成控制) */
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

  useEffect(() => {
    if (!isGenerating && textareaRef.current) {
      textareaRef.current.focus();
    }
  }, [isGenerating]);

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
    <div className="px-7 pb-5 pt-0 shrink-0 bg-white select-none">
      <div className="max-w-[760px] mx-auto rounded-[18px] border border-[#DEE0E3] bg-white p-3.5 transition-all focus-within:border-[#3370FF] shadow-[0_2px_12px_rgba(0,0,0,0.03)]">
        <textarea
          ref={textareaRef}
          rows={2}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
          disabled={isGenerating}
          className="w-full resize-none bg-transparent text-[14px] leading-[22px] text-[#1F2329] outline-none placeholder:text-[#8F959E]"
        />

        <div className="flex items-center justify-between pt-2 mt-1">
          <div className="flex items-center gap-3 text-[#646A73]">
            <FeishuTooltip title="添加附件或技能" side="top" sideOffset={6}>
              <button
                type="button"
                className="hover:text-[#1F2329] p-0.5 rounded transition-colors cursor-pointer"
                onClick={handleAddAttachment}
              >
                <Plus className="w-4 h-4 stroke-[2.2]" />
              </button>
            </FeishuTooltip>
            <FeishuTooltip title="引用人员、文档或数据表" side="top" sideOffset={6}>
              <button
                type="button"
                className="hover:text-[#1F2329] p-0.5 rounded transition-colors cursor-pointer"
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
                className="text-[#646A73] hover:text-[#1F2329] p-1 rounded transition-colors cursor-pointer"
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
                    ? "bg-[#3370FF] hover:bg-[#2A62EA] text-white"
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
