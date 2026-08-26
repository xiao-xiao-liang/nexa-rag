import React from "react";
import { AuthHeaderLogo } from "./AuthHeaderLogo";
import { AuthHeroVisual } from "./AuthHeroVisual";
import { FeishuLangDropdown } from "../shared/FeishuLangDropdown";
import { BrandVariant, SupportedLang } from "../../types";

interface AuthLayoutProps {
  variant?: BrandVariant;
  currentLang?: SupportedLang;
  onSelectLang?: (lang: SupportedLang) => void;
  children: React.ReactNode;
  bottomPrompt?: React.ReactNode;
}

/**
 * 飞书 1:1 响应式左右分栏骨架布局
 *
 * 原版 CSS 规格：
 * - 左侧面板：.web-login-left { position: relative; flex: 1; height: 100%; overflow: hidden; }
 * - 右侧插画区：.web-login-right { width: 520px; min-width: 520px; max-width: 520px; shrink-0; }
 * - 主卡片区域：.web-main-content { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); width: 444px; display: flex; flex-direction: column; align-items: center; }
 * - 底部引导：.login-content-bottom { position: absolute; bottom: -38px; width: 100%; height: 30px; font-size: 14px; text-align: center; }
 */
export const AuthLayout: React.FC<AuthLayoutProps> = ({
  variant = "feishu",
  currentLang = "zh-CN",
  onSelectLang,
  children,
  bottomPrompt,
}) => {
  return (
    <div className="passport-layout-web-login flex w-full h-screen min-h-[640px] bg-white overflow-hidden font-sans antialiased text-[#1f2329]">
      {/* 左侧认证交互区 (自适应剩余宽度的宽阔区域 flex-1) */}
      <div className="web-login-left relative flex-1 h-full bg-white overflow-hidden">
        {/* 顶部绝对定位 Logo 栏 */}
        <AuthHeaderLogo variant={variant} />

        {/* 绝对居中主卡片区域 (.web-main-content: 444px 居中) */}
        <div className="web-main-content absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[444px] max-w-[calc(100vw-32px)] flex flex-col items-center select-text">
          {/* 主卡片 */}
          <div className="w-full">{children}</div>

          {/* 卡片正下方的辅助引导文案 (.login-content-bottom) */}
          {bottomPrompt && (
            <div className="login-content-bottom absolute bottom-[-38px] w-full text-center text-[14px] leading-[22px] text-[#646a73] select-none">
              {bottomPrompt}
            </div>
          )}
        </div>

        {/* 底部绝对定位语言选择器 */}
        <FeishuLangDropdown currentLang={currentLang} onSelectLang={onSelectLang} />
      </div>

      {/* 右侧品牌插画与标语区 (原版严格固定 520px 宽度) */}
      <div className="hidden lg:flex w-[520px] min-w-[520px] max-w-[520px] shrink-0 h-full">
        <AuthHeroVisual variant={variant} />
      </div>
    </div>
  );
};
