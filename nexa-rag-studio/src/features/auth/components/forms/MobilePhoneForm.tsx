import React, { useState } from "react";
import { FeishuMobileInput } from "../shared/FeishuMobileInput";
import { FeishuAuthButton } from "../shared/FeishuAuthButton";
import { FeishuAuthDivider } from "../shared/FeishuAuthDivider";
import { FeishuAgreementCheckbox } from "../shared/FeishuAgreementCheckbox";
import { FeishuThirdPartyLogin } from "../shared/FeishuThirdPartyLogin";
import { OAuthProvider, BrandVariant } from "../../types";

interface MobilePhoneFormProps {
  variant?: BrandVariant;
  onNext?: (phone: string, countryCode: string) => void;
  onSwitchBrand?: () => void;
  onSelectThirdParty?: (provider: OAuthProvider) => void;
  loading?: boolean;
}

/**
 * 手机号登录表单 (1:1 飞书原版独立双栏输入框与间距排版)
 */
export const MobilePhoneForm: React.FC<MobilePhoneFormProps> = ({
  variant = "feishu",
  onNext,
  onSwitchBrand,
  onSelectThirdParty,
  loading = false,
}) => {
  const [countryCode, setCountryCode] = useState("+86");
  const [phone, setPhone] = useState("");
  const [agreePolicy, setAgreePolicy] = useState(false);
  const [errors, setErrors] = useState<{ phone?: string; agree?: boolean }>({});

  const isLark = variant === "lark";
  const switchBrandText = isLark ? "切换至飞书登录" : "切换至Lark登录";

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const newErrors: typeof errors = {};

    if (!phone.trim()) {
      newErrors.phone = "请输入手机号";
    }

    if (!agreePolicy) {
      newErrors.agree = true;
    }

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }

    setErrors({});
    onNext?.(phone.trim(), countryCode);
  };

  return (
    <form onSubmit={handleSubmit} className="w-full flex flex-col">
      {/* 飞书 1:1 独立双栏拼接输入框 (高度 40px, 下边距 12px) */}
      <div className="mb-[12px]">
        <FeishuMobileInput
          countryCode={countryCode}
          phone={phone}
          onPhoneChange={(val) => {
            setPhone(val);
            if (errors.phone) setErrors((prev) => ({ ...prev, phone: undefined }));
          }}
          error={errors.phone}
          disabled={loading}
        />
      </div>

      {/* 下一步主按钮 (高度 40px, 字号 16px) */}
      <FeishuAuthButton type="submit" size="lg" loading={loading}>
        下一步
      </FeishuAuthButton>

      {/* 分割线 (上下边距 16px) */}
      <FeishuAuthDivider text={isLark ? "更多登录方式" : "或"} />

      {/* 快捷登录按钮组 (每个高度 40px, 间距 12px) */}
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

      {/* 底部切换至 Lark/飞书 登录 (上边距 12px, 原版黑色文本 #1f2329) */}
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
