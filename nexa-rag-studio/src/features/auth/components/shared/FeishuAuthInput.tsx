import React, { useState } from "react";
import { Eye, EyeOff, X } from "lucide-react";

export interface FeishuAuthInputProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "prefix"> {
  label?: string;
  error?: string;
  prefix?: React.ReactNode;
  allowClear?: boolean;
  showPasswordToggle?: boolean;
  sendCodeConfig?: {
    onSend: () => Promise<boolean> | boolean;
    countdownSeconds?: number;
    disabled?: boolean;
  };
  inputRef?: React.RefObject<HTMLInputElement | null>;
}

/**
 * 飞书 1:1 标准风格单栏输入框 (.ud__input / .ud__input--size-lg)
 *
 * 原版 CSS 规格 (复用手机号输入框标准，单盒模型)：
 * - 高度：40px
 * - 圆角：6px (完整四角)
 * - 边框：1px solid #dee0e3
 * - 字体与字号：16px, line-height: 24px, #1f2329, 占位符 #8f959e
 * - 内边距：padding: 0 12px
 * - 聚焦/悬浮样式：border-color 为 #3370ff (无模糊阴影外环)
 */
export const FeishuAuthInput: React.FC<FeishuAuthInputProps> = ({
  type = "text",
  value = "",
  onChange,
  placeholder,
  error,
  prefix,
  allowClear = false,
  showPasswordToggle = false,
  sendCodeConfig,
  disabled = false,
  className = "",
  inputRef,
  autoFocus = false,
  ...props
}) => {
  const [showPassword, setShowPassword] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [isSending, setIsSending] = useState(false);

  const isPassword = type === "password";
  const actualType = isPassword ? (showPassword ? "text" : "password") : type;

  const handleClear = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (onChange) {
      const syntheticEvent = {
        target: { value: "" },
        currentTarget: { value: "" },
      } as React.ChangeEvent<HTMLInputElement>;
      onChange(syntheticEvent);
    }
  };

  const handleSendCode = async (e: React.MouseEvent) => {
    e.preventDefault();
    if (countdown > 0 || isSending || !sendCodeConfig || sendCodeConfig.disabled) return;

    try {
      setIsSending(true);
      const success = await sendCodeConfig.onSend();
      if (success) {
        setCountdown(sendCodeConfig.countdownSeconds || 60);
        const timer = setInterval(() => {
          setCountdown((prev) => {
            if (prev <= 1) {
              clearInterval(timer);
              return 0;
            }
            return prev - 1;
          });
        }, 1000);
      }
    } finally {
      setIsSending(false);
    }
  };

  return (
    <div className={`w-full ${className}`}>
      <div
        className={`relative flex items-center w-full h-[40px] bg-white rounded-[6px] border transition-colors duration-150 ${
          error
            ? "border-[#f54a45]"
            : "border-[#dee0e3] hover:border-[#3370ff] focus-within:border-[#3370ff]"
        } ${disabled ? "bg-[#f5f6f7] text-[#8f959e] cursor-not-allowed" : ""}`}
      >
        {/* 左侧前缀插槽 */}
        {prefix && <div className="flex items-center h-full text-[#1f2329] pl-[12px]">{prefix}</div>}

        {/* 原生 Input (16px 字号, 1:1 飞书标准) */}
        <input
          ref={inputRef}
          autoFocus={autoFocus}
          type={actualType}
          value={value}
          onChange={onChange}
          placeholder={placeholder}
          disabled={disabled}
          className="flex-1 h-full px-[12px] text-[16px] leading-[24px] text-[#1f2329] placeholder-[#8f959e] bg-transparent outline-none disabled:cursor-not-allowed font-sans"
          {...props}
        />

        {/* 右侧操作区 */}
        <div className="flex items-center pr-3 gap-1.5 text-[#8f959e]">
          {/* 一键清空按钮 */}
          {allowClear && String(value).length > 0 && !disabled && (
            <button
              type="button"
              onClick={handleClear}
              className="p-1 hover:text-[#1f2329] transition-colors rounded-full cursor-pointer"
              tabIndex={-1}
            >
              <X className="w-4 h-4" />
            </button>
          )}

          {/* 密码明密文切换 */}
          {showPasswordToggle && isPassword && !disabled && (
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className="p-1 hover:text-[#1f2329] transition-colors cursor-pointer"
              tabIndex={-1}
            >
              {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            </button>
          )}

          {/* 发送验证码按钮 */}
          {sendCodeConfig && (
            <button
              type="button"
              onClick={handleSendCode}
              disabled={countdown > 0 || isSending || sendCodeConfig.disabled || disabled}
              className={`text-[13px] font-medium whitespace-nowrap pl-2 border-l border-[#dee0e3] transition-colors ${
                countdown > 0 || isSending || sendCodeConfig.disabled || disabled
                  ? "text-[#8f959e] cursor-not-allowed"
                  : "text-[#3370ff] hover:text-[#245bdb] cursor-pointer"
              }`}
            >
              {countdown > 0 ? `${countdown}s 后重发` : isSending ? "发送中..." : "获取验证码"}
            </button>
          )}
        </div>
      </div>

      {/* 错误提示文字 */}
      {error && <div className="mt-1 text-[12px] text-[#f54a45] leading-tight">{error}</div>}
    </div>
  );
};
