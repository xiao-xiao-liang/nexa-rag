import React from "react";
import { Share2, Settings, MessageSquarePlus } from "lucide-react";
import { FeishuTooltip } from "../ui/tooltip";
import { FeishuAgentCubeLogo } from "./FeishuChatIcons";

export interface ChatHeaderProps {
  title?: string;
  subtitle?: string;
  onNewConversation?: () => void;
  onShare?: () => void;
  onSettings?: () => void;
}

/** 1:1 飞书智能体纯白沉浸式顶栏 (无边框、立体彩色 Logo 与快捷动作) */
export const ChatHeader: React.FC<ChatHeaderProps> = ({
  title = "未命名智能体",
  subtitle = "内容由 AI 生成",
  onNewConversation,
  onShare,
  onSettings,
}) => {
  const handleShare = () => {
    if (onShare) {
      onShare();
    } else {
      alert("已复制对话分享链接");
    }
  };

  const handleSettings = () => {
    if (onSettings) {
      onSettings();
    } else {
      alert("打开智能体设置");
    }
  };

  return (
    <header className="h-14 px-6 flex items-center justify-between shrink-0 bg-white z-10 select-none">
      <div className="flex items-center gap-3">
        <FeishuAgentCubeLogo />
        <div className="flex flex-col">
          <h1 className="text-[14px] font-semibold text-[#1F2329] leading-[22px]">
            {title}
          </h1>
          <span className="text-[12px] text-[#8F959E] leading-[18px]">
            {subtitle}
          </span>
        </div>
      </div>

      <div className="flex items-center gap-4 text-[13px] text-[#646A73]">
        <FeishuTooltip title="分享当前对话" side="bottom" sideOffset={6}>
          <button
            type="button"
            className="flex items-center gap-1 hover:text-[#1F2329] transition-colors cursor-pointer"
            onClick={handleShare}
          >
            <Share2 className="w-3.5 h-3.5" />
            <span>分享</span>
          </button>
        </FeishuTooltip>

        <FeishuTooltip title="开启新对话" side="bottom" sideOffset={6}>
          <button
            type="button"
            onClick={onNewConversation}
            className="flex items-center gap-1 hover:text-[#1F2329] transition-colors cursor-pointer"
          >
            <MessageSquarePlus className="w-3.5 h-3.5" />
            <span>新对话</span>
          </button>
        </FeishuTooltip>

        <FeishuTooltip title="设置" side="bottom" sideOffset={6}>
          <button
            type="button"
            onClick={handleSettings}
            className="p-1 text-[#646A73] hover:text-[#1F2329] hover:bg-[#F2F3F5] rounded transition-colors cursor-pointer"
          >
            <Settings className="w-4 h-4" />
          </button>
        </FeishuTooltip>
      </div>
    </header>
  );
};
