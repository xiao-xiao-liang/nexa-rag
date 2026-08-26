import React, { useState, useEffect } from "react";
import { FeishuCodeInput } from "../shared/FeishuCodeInput";
import { FeishuAuthButton } from "../shared/FeishuAuthButton";
import { BrandVariant } from "../../types";

export interface VerificationCodeFormProps {
  variant?: BrandVariant;
  credentialType: "mobile" | "email";
  credentialValue: string;
  countryCode?: string;
  onSubmit: (code: string) => Promise<void> | void;
  onSendCode: () => Promise<boolean> | boolean;
  onBack: () => void;
  onSwitchOtherMethod?: () => void;
  onFindAccount?: () => void;
  loading?: boolean;
}

/**
 * 手机号/邮箱脱敏工具函数
 */
function maskCredential(type: "mobile" | "email", val: string, countryCode = "+86"): string {
  if (type === "mobile") {
    // 例如 +86 18751611869 -> +86187******69
    const clean = val.replace(/\D/g, "");
    if (clean.length >= 7) {
      const prefix = clean.slice(0, 3);
      const suffix = clean.slice(-2);
      const stars = "*".repeat(clean.length - 5);
      return `${countryCode}${prefix}${stars}${suffix}`;
    }
    return `${countryCode}${val}`;
  } else {
    // 邮箱脱敏：例如 user@example.com -> u***r@example.com
    const [name, domain] = val.split("@");
    if (!domain) return val;
    if (name.length <= 2) {
      return `${name[0]}***@${domain}`;
    }
    return `${name[0]}***${name[name.length - 1]}@${domain}`;
  }
}

/**
 * 飞书 1:1 安全验证码卡片表单 (.meta__verify-credential__container)
 *
 * 原版 CSS 规格 (1:1 结构还原)：
 * - 左上角返回导航：图标 14px, 文字 14px, margin-bottom: 16px, padding 2px 4px;
 * - 主标题：font-size: 22px; font-weight: 600; line-height: 30px; color: #1f2329; margin-bottom: 8px;
 * - 副标题描述：line-height: 20px; color: #646a73; 脱敏手机/邮箱加粗 (#1f2329), 帮助文档链接 #3370ff;
 * - 6位验证码：3+3 分组, 40px × 40px 格子, 7px 分隔线, 聚焦变蓝 #3370ff;
 * - 倒计时文案：font-size: 14px; line-height: 20px; color: #646a73;
 * - 辅助链接：其他验证方式、手机号已停用？找回账号
 * - 底部按钮：40px 高度下一步主按钮，使用 mt-auto 沉底固定在卡片最下方，未输满 6 位置灰禁用。
 */
export const VerificationCodeForm: React.FC<VerificationCodeFormProps> = ({
  variant = "feishu",
  credentialType,
  credentialValue,
  countryCode = "+86",
  onSubmit,
  onSendCode,
  onBack,
  onSwitchOtherMethod,
  onFindAccount,
  loading = false,
}) => {
  const [code, setCode] = useState("");
  const [countdown, setCountdown] = useState(60);
  const [isSending, setIsSending] = useState(false);
  const [error, setError] = useState<string | undefined>();

  const isMobile = credentialType === "mobile";
  const maskedTarget = maskCredential(credentialType, credentialValue, countryCode);

  // 倒计时计时器
  useEffect(() => {
    if (countdown <= 0) return;
    const timer = setInterval(() => {
      setCountdown((prev) => (prev <= 1 ? 0 : prev - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [countdown]);

  const handleResend = async () => {
    if (countdown > 0 || isSending) return;
    try {
      setIsSending(true);
      setError(undefined);
      const success = await onSendCode();
      if (success) {
        setCountdown(60);
        setCode("");
      }
    } finally {
      setIsSending(false);
    }
  };

  const handleCodeSubmit = async (verifyCode: string) => {
    if (verifyCode.length !== 6 || loading) return;
    setError(undefined);
    try {
      await onSubmit(verifyCode);
    } catch (err: any) {
      setError(err?.message || "验证码错误或已失效，请重新输入");
    }
  };

  return (
    <div className="meta__verify-credential__container w-full h-full flex-1 flex flex-col justify-between">
      {/* 上半部分主体内容 */}
      <div className="flex flex-col w-full">
        {/* 1. 左上角返回导航按钮 (.new-back, margin-bottom: 16px, 1:1 飞书文本按钮紧凑尺寸) */}
        <div className="mb-[16px] -ml-[4px]">
          <button
            type="button"
            onClick={onBack}
            className="ud__button ud__button--text ud__button--text-default ud__button--size-md new-back inline-flex items-center px-[4px] py-[2px] text-[14px] leading-[22px] text-[#1f2329] hover:bg-[rgba(31,35,41,0.1)] active:bg-[rgba(31,35,41,0.2)] rounded-[4px] transition-colors cursor-pointer select-none font-normal"
          >
            {/* 飞书 1:1 LeftBoldOutlined 矢量粗箭头 (右外边距严格 4px) */}
            <span className="ud__button__icon-inline ud__button__icon-inline-start mr-[4px] flex items-center">
              <svg
                className="w-[14px] h-[14px] text-[#1f2329] shrink-0"
                viewBox="0 0 24 24"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
              >
                <path
                  d="m16.314 3.515-.707-.707a1 1 0 0 0-1.414 0l-7.779 7.778a2 2 0 0 0 0 2.829l7.779 7.778a1 1 0 0 0 1.414 0l.707-.707a1 1 0 0 0 0-1.414L9.243 12l7.07-7.072a1 1 0 0 0 0-1.414Z"
                  fill="currentColor"
                />
              </svg>
            </span>
            <span className="new-back__content">返回</span>
          </button>
        </div>

        {/* 2. 主标题与脱敏副标题 (.step-title / .step-title__subtitle) */}
        <div className="mb-[20px]">
          <h2 className="step-title text-[22px] font-semibold leading-[30px] text-[#1f2329] mb-[8px]">
            {isMobile ? "输入手机号验证码" : "输入邮箱验证码"}
          </h2>
          <p className="step-title__subtitle text-[14px] leading-[20px] text-[#646a73]">
            请输入发送至 <strong className="font-semibold text-[#1f2329]">{maskedTarget}</strong> 的 6
            位验证码，10 分钟内有效。如未收到，请重新获取验证码或
            <a
              href="https://www.feishu.cn/hc/zh-CN"
              target="_blank"
              rel="noreferrer"
              className="text-[#3370ff] hover:underline ml-0.5"
            >
              查看帮助文档
            </a>
          </p>
        </div>

        {/* 3. 6 位分格验证码输入组件 (.base-code-box) */}
        <div className="mb-[12px]">
          <FeishuCodeInput
            value={code}
            onChange={(val) => {
              setCode(val);
              if (error) setError(undefined);
            }}
            onComplete={handleCodeSubmit}
            error={!!error}
            disabled={loading}
            autoFocus
          />
          {/* 错误提示文字 */}
          {error && <div className="text-[12px] text-[#f54a45] leading-tight -mt-1 mb-2">{error}</div>}
        </div>

        {/* 4. 倒计时 / 重新获取验证码 (.base-code-box-count) */}
        <div className="mb-[16px]">
          {countdown > 0 ? (
            <div className="base-code-box-count text-[14px] leading-[20px] text-[#646a73]">
              {countdown} 秒后可重新获取验证码
            </div>
          ) : (
            <button
              type="button"
              onClick={handleResend}
              disabled={isSending}
              className="text-[14px] leading-[20px] text-[#3370ff] hover:text-[#245bdb] cursor-pointer select-none font-normal"
            >
              {isSending ? "发送中..." : "重新获取验证码"}
            </button>
          )}
        </div>

        {/* 5. 辅助操作与账号找回链接 */}
        <div className="flex flex-col gap-[12px]">
          <button
            type="button"
            onClick={onSwitchOtherMethod}
            className="ud__button--link text-[14px] leading-[20px] text-[#3370ff] hover:text-[#245bdb] cursor-pointer text-left select-none font-normal w-fit"
          >
            其他验证方式
          </button>

          <div className="forget-text text-[14px] leading-[20px] text-[#646a73]">
            <span>{isMobile ? "手机号已停用？" : "邮箱无法访问？"}</span>
            <button
              type="button"
              onClick={onFindAccount}
              className="text-[#3370ff] hover:text-[#245bdb] ml-1 cursor-pointer font-normal"
            >
              找回账号
            </button>
          </div>
        </div>
      </div>

      {/* 6. 卡片底部提交主按钮 (.ud__modal__footer .step-box__footer，通过 mt-auto 沉底固定在最下方) */}
      <div className="ud__modal__footer step-box__footer w-full mt-auto pt-[24px]">
        <FeishuAuthButton
          type="button"
          size="lg"
          disabled={code.length < 6 || loading}
          loading={loading}
          onClick={() => handleCodeSubmit(code)}
        >
          下一步
        </FeishuAuthButton>
      </div>
    </div>
  );
};
