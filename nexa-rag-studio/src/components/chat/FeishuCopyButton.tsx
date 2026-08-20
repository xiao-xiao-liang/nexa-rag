import React from "react";
import { FeishuTooltip } from "../ui/tooltip";
import { BaseAgentCopyOutlinedIcon } from "./FeishuChatIcons";

export interface FeishuCopyButtonProps {
  onCopy: () => void;
  isCopied: boolean;
  tooltipText?: string;
  copiedTooltipText?: string;
  className?: string;
}

/** 1:1 飞书原版复制按钮 (集成 FeishuTooltip 与复制成功状态) */
export const FeishuCopyButton: React.FC<FeishuCopyButtonProps> = ({
  onCopy,
  isCopied,
  tooltipText = "复制",
  copiedTooltipText = "已复制",
  className = "",
}) => {
  return (
    <FeishuTooltip
      title={isCopied ? copiedTooltipText : tooltipText}
      side="top"
      sideOffset={6}
    >
      <button
        type="button"
        onClick={onCopy}
        className={`w-6 h-6 rounded-[8px] flex items-center justify-center text-[#8F959E] hover:text-[#1F2329] hover:bg-[rgba(115,132,157,0.08)] cursor-pointer transition-colors ${className}`}
        aria-label="复制"
      >
        <BaseAgentCopyOutlinedIcon className="w-3.5 h-3.5" />
      </button>
    </FeishuTooltip>
  );
};
