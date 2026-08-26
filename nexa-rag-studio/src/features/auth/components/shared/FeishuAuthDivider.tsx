import React from "react";

interface FeishuAuthDividerProps {
  text?: string;
  className?: string;
}

/**
 * 飞书 1:1 文字分割线 (.ud__divider--with-text / .enter-credential__doubao_divider-container)
 *
 * 原版 CSS 规格：
 * - 上下间距：margin: 24px 0 (.enter-credential__doubao_divider-container / .ud__divider--horizontal)
 * - 边框颜色：border-top: 1px solid rgba(31,35,41,0.15)
 * - 中间文案：font-size: 14px; line-height: 22px; color: #8f959e; padding: 0 14px;
 */
export const FeishuAuthDivider: React.FC<FeishuAuthDividerProps> = ({
  text = "或",
  className = "",
}) => {
  return (
    <div className={`enter-credential__doubao_divider-container relative flex items-center justify-center my-[24px] ${className}`}>
      <div className="grow border-t border-[rgba(31,35,41,0.15)]" />
      <span className="shrink-0 px-[14px] text-[14px] leading-[22px] text-[#8f959e] select-none bg-white font-normal">
        {text}
      </span>
      <div className="grow border-t border-[rgba(31,35,41,0.15)]" />
    </div>
  );
};
