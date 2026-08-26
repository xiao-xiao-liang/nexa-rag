import React from "react";
import { ChevronDown, X } from "lucide-react";

export interface FeishuMobileInputProps {
  countryCode?: string;
  phone: string;
  onPhoneChange: (phone: string) => void;
  onCountryCodeClick?: () => void;
  error?: string;
  disabled?: boolean;
  placeholder?: string;
  className?: string;
  inputRef?: React.RefObject<HTMLInputElement | null>;
  autoFocus?: boolean;
}

/**
 * 飞书 1:1 手机号输入框组件 (.pp-mobile-input / .mobile-input)
 *
 * 原版 CSS 规格 (1:1 源码结构)：
 * - 总高度：40px
 * - 左栏 (.mobile-input-left)：
 *   - 宽度：min-width: 80px; width: 80px;
 *   - 内边距：padding: 0 8px;
 *   - 区号文字 (.mobile-input-code)：width: 42px; text-align: center; font-size: 16px; color: #1f2329;
 *   - 下拉箭头：14px × 14px 线条下拉箭头 (ChevronDown, strokeWidth: 1.75, color: #646a73)
 *   - 边框与圆角：border: 1px solid #dee0e3; rounded-l-[6px] rounded-r-0;
 *   - 交互：hover / focus 独立变蓝 (#3370ff) 并提升 z-index: 1
 * - 右栏 (.mobile-input-right)：
 *   - 高度：40px; flex-1; -ml-[1px] 边框重叠;
 *   - 边框与圆角：border: 1px solid #dee0e3; rounded-r-[6px] rounded-l-0;
 *   - 输入文字 (.mobile-input-phone)：font-size: 16px; line-height: 24px; color: #1f2329; padding: 0 10px;
 *   - 交互：hover / focus 独立变蓝 (#3370ff) 并提升 z-index: 1
 */
export const FeishuMobileInput: React.FC<FeishuMobileInputProps> = ({
  countryCode = "+86",
  phone,
  onPhoneChange,
  onCountryCodeClick,
  error,
  disabled = false,
  placeholder = "请输入你的手机号",
  className = "",
  inputRef,
  autoFocus = false,
}) => {
  return (
    <div className={`pp-mobile-input mobile-input-container w-full h-[40px] ${className}`}>
      <div className="mobile-input flex items-center w-full h-[40px] relative">
        {/* 左侧独立区号容器 (固定宽度 80px, 左圆角 6px, 独立边框, 悬浮/聚焦独立变蓝) */}
        <div
          onClick={onCountryCodeClick}
          className={`mobile-input-left relative flex items-center justify-center w-[80px] min-w-[80px] h-[40px] px-[8px] bg-white rounded-l-[6px] rounded-r-0 border transition-colors duration-150 cursor-pointer select-none ${
            error
              ? "border-[#f54a45] z-[1]"
              : "border-[#dee0e3] hover:border-[#3370ff] focus-within:border-[#3370ff] hover:z-[1] focus-within:z-[1]"
          } ${disabled ? "bg-[#f5f6f7] text-[#8f959e] cursor-not-allowed" : ""}`}
        >
          {/* 区号文字 (42px 居中, 16px 字号) */}
          <span className="mobile-input-code w-[42px] text-center text-[16px] leading-[24px] text-[#1f2329] font-normal">
            {countryCode}
          </span>
          {/* 飞书 1:1 干净的线条型下拉小箭头 */}
          <ChevronDown className="w-[14px] h-[14px] text-[#646a73] shrink-0 pointer-events-none stroke-[1.75]" />
        </div>

        {/* 右侧独立手机号输入容器 (高度 40px, 右圆角 6px, 16px 字号, -ml-[1px] 重叠, 悬浮/聚焦独立变蓝) */}
        <div
          className={`mobile-input-right relative flex-1 flex items-center h-[40px] -ml-[1px] px-[10px] bg-white rounded-r-[6px] rounded-l-0 border transition-colors duration-150 ${
            error
              ? "border-[#f54a45] z-[1]"
              : "border-[#dee0e3] hover:border-[#3370ff] focus-within:border-[#3370ff] hover:z-[1] focus-within:z-[1]"
          } ${disabled ? "bg-[#f5f6f7] cursor-not-allowed" : ""}`}
        >
          <input
            ref={inputRef}
            autoFocus={autoFocus}
            type="tel"
            value={phone}
            onChange={(e) => onPhoneChange(e.target.value)}
            placeholder={placeholder}
            disabled={disabled}
            className="mobile-input-phone w-full h-full text-[16px] leading-[24px] text-[#1f2329] placeholder-[#8f959e] bg-transparent outline-none disabled:cursor-not-allowed font-sans"
          />
          {phone.length > 0 && !disabled && (
            <button
              type="button"
              onClick={() => onPhoneChange("")}
              className="pl-2 pr-1 text-[#8f959e] hover:text-[#1f2329] transition-colors cursor-pointer"
              tabIndex={-1}
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
