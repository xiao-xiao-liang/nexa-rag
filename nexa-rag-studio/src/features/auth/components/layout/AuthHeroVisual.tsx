import React from "react";
import { BrandVariant } from "../../types";
import heroImg from "../../../../assets/auth/image-009.png";

interface AuthHeroVisualProps {
  variant?: BrandVariant;
  className?: string;
}

/**
 * 飞书/Lark 1:1 登录页右侧品牌插画与标语区域 (.web-login-right)
 *
 * 原版 CSS 规格 (1:1 结构还原)：
 * - 右侧外层容器 (.web-login-right)：width: 520px; min-width: 520px; max-width: 520px;
 * - 插画根容器 (.passport-lottie-root)：width: 420px;
 * - 插画宽度：width: 420px; h-auto;
 * - 标语大标题 (.login-slogan-title)：font-size: 24px; font-weight: 600; line-height: 40px; color: #1f2329; margin-top: 24px;
 * - 标语副标题 (.login-slogan-subtitle)：font-size: 16px; font-weight: 400; line-height: 24px; color: #646a73; margin-top: 12px; margin-bottom: 38px;
 */
export const AuthHeroVisual: React.FC<AuthHeroVisualProps> = ({
  variant = "feishu",
  className = "",
}) => {
  const isLark = variant === "lark";

  const title = isLark ? "你的一站式工作平台" : "先进团队 先用飞书";
  const subtitle = isLark ? "人、事、信息，一处搞定" : "字节跳动旗下 AI 工作平台";

  return (
    <div
      className={`web-login-right relative flex flex-col items-center justify-center w-[520px] min-w-[520px] max-w-[520px] h-full bg-[#f3f4fb] bg-gradient-to-b from-[#f3f4fb] to-[#eaedf7] select-none px-[20px] overflow-hidden ${className}`}
    >
      {/* 居中插画卡片根节点 (.passport-lottie-root) */}
      <div className="passport-lottie-root relative flex flex-col items-center w-[420px] max-w-[420px] text-center">
        {/* 固定高清单帧插画 (.lottie-content) */}
        <div className="lottie-content relative w-[420px] flex items-center justify-center">
          <img
            src={heroImg}
            alt="Feishu Collaboration Hero"
            className="lottie-bg w-[420px] h-auto object-contain select-none pointer-events-none drop-shadow-sm"
          />
        </div>

        {/* 标语文案 (.login-slogan) */}
        <div className="login-slogan mt-[24px]">
          <h2 className="login-slogan-title text-[24px] font-semibold text-[#1f2329] leading-[40px] text-center tracking-[-0.2px]">
            {title}
          </h2>
          <p className="login-slogan-subtitle min-h-[24px] mt-[12px] mb-[38px] text-[16px] font-normal text-[#646a73] leading-[24px] text-center">
            {subtitle}
          </p>
        </div>
      </div>
    </div>
  );
};
