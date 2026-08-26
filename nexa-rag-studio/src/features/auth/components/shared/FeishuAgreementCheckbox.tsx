import React from "react";
import { Check } from "lucide-react";
import { BrandVariant } from "../../types";

interface FeishuAgreementCheckboxProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  variant?: BrandVariant;
  serviceUrl?: string;
  privacyUrl?: string;
  error?: boolean;
  className?: string;
}

/**
 * 飞书 1:1 协议与隐私条款勾选框 (.terms-and-policy-container)
 *
 * 原版 CSS 规格：
 * - 字体：font-size: 12px; line-height: 18px; color: #646a73;
 * - 国内版飞书 (with-doubao): 条款链接颜色为 #1f2329, hover: #646a73
 * - 国际版 Lark: 条款链接颜色为 #245bdb, hover: #3370ff
 * - 勾选框：16px x 16px, border-radius: 4px, border: 1px solid #8f959e / #bbbfc4
 */
export const FeishuAgreementCheckbox: React.FC<FeishuAgreementCheckboxProps> = ({
  checked,
  onChange,
  variant = "feishu",
  serviceUrl = "https://www.feishu.cn/zh-CN/terms",
  privacyUrl = "https://www.feishu.cn/zh-CN/privacy",
  error = false,
  className = "",
}) => {
  const isLark = variant === "lark";
  const linkClass = isLark
    ? "passport-policy-tip text-[#245bdb] hover:text-[#3370ff] active:text-[#1c4cba] hover:underline"
    : "passport-policy-tip text-[#1f2329] hover:text-[#646a73] active:text-[#1f2329] hover:underline font-normal";

  return (
    <div className={`terms-and-policy-container flex items-start gap-2 text-[12px] leading-[18px] text-[#646a73] ${className}`}>
      {/* 飞书 1:1 复选框 */}
      <button
        type="button"
        onClick={() => onChange(!checked)}
        className={`shrink-0 w-4 h-4 mt-[1px] rounded-[4px] border flex items-center justify-center transition-all duration-150 cursor-pointer ${
          checked
            ? "bg-[#3370ff] border-[#3370ff] text-white"
            : error
            ? "border-[#f54a45] bg-white"
            : "border-[#bbbfc4] hover:border-[#3370ff] bg-white"
        }`}
        role="checkbox"
        aria-checked={checked}
      >
        {checked && <Check className="w-3 h-3 stroke-[3]" />}
      </button>

      {/* 协议与隐私政策条款 */}
      <div className="select-none passport-policy-content">
        <span>我已阅读并同意 </span>
        <a
          href={serviceUrl}
          target="_blank"
          rel="noopener noreferrer"
          className={linkClass}
          onClick={(e) => e.stopPropagation()}
        >
          服务协议
        </a>
        <span> 和 </span>
        <a
          href={privacyUrl}
          target="_blank"
          rel="noopener noreferrer"
          className={linkClass}
          onClick={(e) => e.stopPropagation()}
        >
          隐私政策
        </a>
      </div>
    </div>
  );
};
