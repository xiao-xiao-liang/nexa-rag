import React from "react";

interface FeishuAuthCardProps {
  children: React.ReactNode;
  cornerSwitch?: React.ReactNode;
  className?: string;
}

/**
 * 飞书 1:1 登录卡片容器 (.login-content-container)
 *
 * 原版 CSS 规格 (1:1 源码结构)：
 * - 容器总宽度：444px
 * - 最小高度：550px
 * - 内部内边距：40px (.ud__modal__header 24px + .step-box__header 16px)
 * - 内部元素净宽：364px (444px - 40px * 2)
 * - 边框：1px solid #dee0e3
 * - 圆角：12px
 * - 阴影：box-shadow: 0 4px 8px rgba(31,35,41,.03), 0 3px 6px -6px rgba(31,35,41,.05), 0 6px 18px 6px rgba(31,35,41,.03);
 */
export const FeishuAuthCard: React.FC<FeishuAuthCardProps> = ({
  children,
  cornerSwitch,
  className = "",
}) => {
  return (
    <div
      className={`login-content-container relative w-[444px] min-h-[550px] bg-white rounded-[12px] border border-[#dee0e3] shadow-[0_4px_8px_rgba(31,35,41,0.03),0_3px_6px_-6px_rgba(31,35,41,0.05),0_6px_18px_6px_rgba(31,35,41,0.03)] overflow-hidden flex flex-col ${className}`}
    >
      {/* 右上角折角切换按钮插槽 */}
      {cornerSwitch}

      {/* 卡片主体内容 (原版内边距合计 40px，内部净宽 364px) */}
      <div className="new-account-login-box w-full h-full min-h-[550px] p-[40px] flex flex-col flex-1">
        {children}
      </div>
    </div>
  );
};
