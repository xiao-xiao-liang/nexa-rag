import React from "react";
import { Loader2 } from "lucide-react";

export interface FeishuAuthButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "outlined" | "text" | "link";
  size?: "lg" | "md" | "sm";
  block?: boolean;
  loading?: boolean;
  icon?: React.ReactNode;
}

/**
 * 飞书 1:1 按钮组件 (.ud__button)
 *
 * 原版 CSS 规格：
 * - 大号 (lg)：height: 40px; line-height: 24px; padding: 7px 15px; font-size: 16px; border-radius: 6px;
 * - 主按钮：background: #3370ff; hover: #4e83fd; active: #245bdb; disabled: #bbbfc4;
 * - 线框按钮：border: 1px solid #dee0e3; color: #1f2329; hover: bg-[#eff0f1];
 */
export const FeishuAuthButton: React.FC<FeishuAuthButtonProps> = ({
  children,
  variant = "primary",
  size = "lg",
  block = true,
  loading = false,
  disabled = false,
  icon,
  className = "",
  type = "button",
  ...props
}) => {
  const sizeClasses = {
    lg: "h-[40px] px-[15px] py-[7px] text-[16px] leading-[24px] rounded-[6px]",
    md: "h-[36px] px-4 text-[14px] leading-[22px] rounded-[6px]",
    sm: "h-[28px] px-3 text-[12px] leading-[20px] rounded-[4px]",
  }[size];

  const variantClasses = {
    primary:
      "bg-[#3370ff] hover:bg-[#4e83fd] active:bg-[#245bdb] text-white font-normal disabled:bg-[#bbbfc4] disabled:border-[#bbbfc4] disabled:cursor-not-allowed",
    outlined:
      "bg-white border border-[#dee0e3] hover:bg-[#eff0f1] hover:border-[#dee0e3] active:bg-[#e1e4e8] text-[#1f2329] font-normal disabled:bg-[#f5f6f7] disabled:text-[#8f959e] disabled:border-[#dee0e3] disabled:cursor-not-allowed",
    text: "bg-transparent hover:bg-[rgba(31,35,41,0.08)] text-[#1f2329] font-normal disabled:text-[#8f959e] disabled:cursor-not-allowed",
    link: "bg-transparent text-[#3370ff] hover:text-[#245bdb] hover:underline p-0 h-auto font-normal disabled:text-[#8f959e] disabled:cursor-not-allowed",
  }[variant];

  return (
    <button
      type={type}
      disabled={disabled || loading}
      className={`inline-flex items-center justify-center gap-2 transition-colors duration-200 cursor-pointer select-none ${
        block ? "w-full" : ""
      } ${sizeClasses} ${variantClasses} ${className}`}
      {...props}
    >
      {loading ? (
        <Loader2 className="w-4 h-4 animate-spin text-current" />
      ) : (
        icon && <span className="inline-flex items-center">{icon}</span>
      )}
      <span>{children}</span>
    </button>
  );
};
