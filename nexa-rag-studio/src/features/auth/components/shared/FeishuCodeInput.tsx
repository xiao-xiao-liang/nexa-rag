import React, { useRef, useEffect } from "react";

export interface FeishuCodeInputProps {
  value: string;
  onChange: (value: string) => void;
  onComplete?: (value: string) => void;
  length?: number;
  error?: boolean;
  disabled?: boolean;
  autoFocus?: boolean;
  className?: string;
}

/**
 * 飞书 1:1 六位分格验证码输入框 (.pp-base-code-box .base-code-box-container)
 *
 * 原版 CSS 规格 (1:1 源码结构)：
 * - 布局：3 + 3 分组，中间以 7px × 1px 分隔线段 (.code-input-seg) 隔开
 * - 单格尺寸：40px × 40px, border-radius: 6px, border: 1px solid #d0d3d6
 * - 字体排版：16px, font-weight: 500, line-height: 16px, text-align: center, color: #1f2329
 * - 聚焦状态：border: 1px solid #3370ff (无厚重外发光阴影)
 * - 错误状态：border-color: #f54a45
 * - 交互逻辑：单字自动步进、Backspace 回退删除前移、支持 6 位纯文本整段粘贴
 */
export const FeishuCodeInput: React.FC<FeishuCodeInputProps> = ({
  value,
  onChange,
  onComplete,
  length = 6,
  error = false,
  disabled = false,
  autoFocus = true,
  className = "",
}) => {
  const inputsRef = useRef<(HTMLInputElement | null)[]>([]);

  // 将字符串切分为单字符数组
  const digits = Array.from({ length }, (_, i) => value[i] || "");

  // 初始自动聚焦第 1 个空格子
  useEffect(() => {
    if (!autoFocus || disabled) return;
    const firstEmptyIndex = digits.findIndex((d) => !d);
    const targetIndex = firstEmptyIndex === -1 ? length - 1 : firstEmptyIndex;
    const timer = setTimeout(() => {
      inputsRef.current[targetIndex]?.focus({ preventScroll: true });
    }, 50);
    return () => clearTimeout(timer);
  }, []);

  const handleInputChange = (index: number, e: React.ChangeEvent<HTMLInputElement>) => {
    const rawVal = e.target.value;
    // 仅保留数字
    const cleanVal = rawVal.replace(/\D/g, "");
    if (!cleanVal) {
      // 清空当前格
      const nextDigits = [...digits];
      nextDigits[index] = "";
      const nextVal = nextDigits.join("").slice(0, length);
      onChange(nextVal);
      return;
    }

    if (cleanVal.length === 1) {
      const nextDigits = [...digits];
      nextDigits[index] = cleanVal;
      const nextVal = nextDigits.join("").slice(0, length);
      onChange(nextVal);

      // 自动跳到下一格
      if (index < length - 1) {
        inputsRef.current[index + 1]?.focus({ preventScroll: true });
      }

      if (nextVal.length === length) {
        onComplete?.(nextVal);
      }
    } else {
      // 粘贴或批量输入多位数字
      const nextDigits = [...digits];
      for (let i = 0; i < cleanVal.length && index + i < length; i++) {
        nextDigits[index + i] = cleanVal[i];
      }
      const nextVal = nextDigits.join("").slice(0, length);
      onChange(nextVal);

      const nextFocusIndex = Math.min(index + cleanVal.length, length - 1);
      inputsRef.current[nextFocusIndex]?.focus({ preventScroll: true });

      if (nextVal.length === length) {
        onComplete?.(nextVal);
      }
    }
  };

  const handleKeyDown = (index: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Backspace") {
      if (!digits[index] && index > 0) {
        // 当前格为空时按 Backspace，回退到上一格并清空
        const nextDigits = [...digits];
        nextDigits[index - 1] = "";
        const nextVal = nextDigits.join("").slice(0, length);
        onChange(nextVal);
        inputsRef.current[index - 1]?.focus({ preventScroll: true });
      } else if (digits[index]) {
        // 清空当前格
        const nextDigits = [...digits];
        nextDigits[index] = "";
        const nextVal = nextDigits.join("").slice(0, length);
        onChange(nextVal);
      }
    } else if (e.key === "ArrowLeft" && index > 0) {
      inputsRef.current[index - 1]?.focus({ preventScroll: true });
    } else if (e.key === "ArrowRight" && index < length - 1) {
      inputsRef.current[index + 1]?.focus({ preventScroll: true });
    }
  };

  const handlePaste = (e: React.ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault();
    const pasteData = e.clipboardData.getData("text").replace(/\D/g, "").slice(0, length);
    if (pasteData) {
      onChange(pasteData);
      const targetIndex = Math.min(pasteData.length, length - 1);
      inputsRef.current[targetIndex]?.focus({ preventScroll: true });
      if (pasteData.length === length) {
        onComplete?.(pasteData);
      }
    }
  };

  return (
    <div className={`pp-base-code-box base-code-box-container w-full ${className}`}>
      <div className="base-code-box flex items-center justify-between w-full mb-[15px]">
        {/* 左 3 格 */}
        {digits.slice(0, 3).map((digit, idx) => (
          <div key={idx} className="newLogin_codeInput-wrap">
            <input
              ref={(el) => {
                inputsRef.current[idx] = el;
              }}
              type="tel"
              maxLength={1}
              value={digit}
              disabled={disabled}
              onChange={(e) => handleInputChange(idx, e)}
              onKeyDown={(e) => handleKeyDown(idx, e)}
              onPaste={handlePaste}
              className={`base-code-box-input w-[40px] h-[40px] p-0 text-[16px] font-medium leading-[16px] text-center text-[#1f2329] bg-white border rounded-[6px] outline-none transition-colors duration-150 select-none ${
                error
                  ? "border-[#f54a45]"
                  : "border-[#d0d3d6] hover:border-[#3370ff] focus:border-[#3370ff]"
              } ${disabled ? "bg-[#eff0f1] text-[#8f959e] cursor-not-allowed" : ""}`}
            />
          </div>
        ))}

        {/* 飞书 1:1 中间 7px × 1px 分隔短横线 (.code-input-seg) */}
        <div className="code-input-seg w-[7px] h-[1px] bg-[#1f2329] opacity-80 shrink-0 select-none" />

        {/* 右 3 格 */}
        {digits.slice(3, 6).map((digit, idx) => {
          const actualIdx = idx + 3;
          return (
            <div key={actualIdx} className="newLogin_codeInput-wrap">
              <input
                ref={(el) => {
                  inputsRef.current[actualIdx] = el;
                }}
                type="tel"
                maxLength={1}
                value={digit}
                disabled={disabled}
                onChange={(e) => handleInputChange(actualIdx, e)}
                onKeyDown={(e) => handleKeyDown(actualIdx, e)}
                onPaste={handlePaste}
                className={`base-code-box-input w-[40px] h-[40px] p-0 text-[16px] font-medium leading-[16px] text-center text-[#1f2329] bg-white border rounded-[6px] outline-none transition-colors duration-150 select-none ${
                  error
                    ? "border-[#f54a45]"
                    : "border-[#d0d3d6] hover:border-[#3370ff] focus:border-[#3370ff]"
                } ${disabled ? "bg-[#eff0f1] text-[#8f959e] cursor-not-allowed" : ""}`}
              />
            </div>
          );
        })}
      </div>
    </div>
  );
};
