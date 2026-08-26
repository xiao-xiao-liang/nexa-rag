import React, { useState } from "react";
import { FeishuAuthInput } from "../shared/FeishuAuthInput";
import { FeishuAuthButton } from "../shared/FeishuAuthButton";
import { FeishuAuthDivider } from "../shared/FeishuAuthDivider";
import { FeishuAgreementCheckbox } from "../shared/FeishuAgreementCheckbox";
import { FeishuThirdPartyLogin } from "../shared/FeishuThirdPartyLogin";
import { EmailPasswordFormValues, OAuthProvider, BrandVariant } from "../../types";

interface EmailPasswordFormProps {
  variant?: BrandVariant;
  onSubmit?: (values: EmailPasswordFormValues) => void;
  onForgotPassword?: () => void;
  onSwitchBrand?: () => void;
  onSelectThirdParty?: (provider: OAuthProvider) => void;
  loading?: boolean;
}

/**
 * 邮箱登录表单 (1:1 飞书原版样式与间距规范)
 */
export const EmailPasswordForm: React.FC<EmailPasswordFormProps> = ({
  variant = "feishu",
  onSubmit,
  onForgotPassword,
  onSwitchBrand,
  onSelectThirdParty,
  loading = false,
}) => {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [agreePolicy, setAgreePolicy] = useState(false);
  const [errors, setErrors] = useState<{ email?: string; password?: string; agree?: boolean }>({});

  const isLark = variant === "lark";
  const switchBrandText = isLark ? "切换至飞书登录" : "切换至Lark登录";

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const newErrors: typeof errors = {};

    if (!email.trim()) {
      newErrors.email = "请输入你的邮箱";
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      newErrors.email = "邮箱格式不正确";
    }

    if (!agreePolicy) {
      newErrors.agree = true;
    }

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }

    setErrors({});
    onSubmit?.({ email: email.trim(), password, agreePolicy });
  };

  return (
    <form onSubmit={handleSubmit} className="w-full flex flex-col">
      {/* 邮箱输入框 (复用手机号输入标准：40px 高度, 16px 字号, 12px 下边距) */}
      <div className="mb-[12px]">
        <FeishuAuthInput
          type="email"
          placeholder="请输入你的邮箱"
          value={email}
          onChange={(e) => {
            setEmail(e.target.value);
            if (errors.email) setErrors((prev) => ({ ...prev, email: undefined }));
          }}
          error={errors.email}
          allowClear
          disabled={loading}
          autoComplete="email"
        />
      </div>

      {/* 下一步主按钮 (40px 高度, 16px 字号) */}
      <FeishuAuthButton type="submit" size="lg" loading={loading}>
        下一步
      </FeishuAuthButton>

      {/* 分割线 (上下边距严格为 24px) */}
      <FeishuAuthDivider text={isLark ? "更多登录方式" : "或"} />

      {/* 快捷登录按钮组 (每个 40px 高度, 间距 12px) */}
      <FeishuThirdPartyLogin variant={variant} onSelectProvider={onSelectThirdParty} />

      {/* 协议与隐私政策 (上边距 16px) */}
      <div className="mt-[16px]">
        <FeishuAgreementCheckbox
          checked={agreePolicy}
          variant={variant}
          onChange={(checked) => {
            setAgreePolicy(checked);
            if (errors.agree) setErrors((prev) => ({ ...prev, agree: false }));
          }}
          error={errors.agree}
        />
      </div>

      {/* 底部切换至 Lark/飞书 登录 (上边距 12px) */}
      {onSwitchBrand && (
        <div className="mt-[12px] text-left">
          <button
            type="button"
            onClick={onSwitchBrand}
            className="text-[14px] leading-[22px] text-[#1f2329] hover:text-[#646a73] hover:underline cursor-pointer select-none font-normal"
          >
            {switchBrandText}
          </button>
        </div>
      )}
    </form>
  );
};
