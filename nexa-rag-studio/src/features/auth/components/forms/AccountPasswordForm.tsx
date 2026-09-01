import React, { useState } from "react";
import { FeishuAuthInput } from "@/features/auth";
import { FeishuAuthButton } from "@/features/auth";
import { FeishuAuthDivider } from "@/features/auth";
import { FeishuAgreementCheckbox } from "@/features/auth";
import { FeishuThirdPartyLogin } from "@/features/auth";
import { AccountPasswordFormValues, OAuthProvider, BrandVariant } from "../../types";

interface AccountPasswordFormProps {
  variant?: BrandVariant;
  onSubmit?: (values: AccountPasswordFormValues) => void;
  onForgotPassword?: () => void;
  onSelectThirdParty?: (provider: OAuthProvider) => void;
  loading?: boolean;
}

/**
 * 账号名 + 密码登录表单
 */
export const AccountPasswordForm: React.FC<AccountPasswordFormProps> = ({
                                                                          variant = "feishu",
                                                                          onSubmit,
                                                                          onForgotPassword,
                                                                          onSelectThirdParty,
                                                                          loading = false,
                                                                        }) => {
  const [accountName, setAccountName] = useState("");
  const [password, setPassword] = useState("");
  const [agreePolicy, setAgreePolicy] = useState(false);
  const [errors, setErrors] = useState<{ accountName?: string; password?: string; agree?: boolean }>({});

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const newErrors: typeof errors = {};

    if (!accountName.trim()) {
      newErrors.accountName = "请输入账号名";
    }
    if (!password) {
      newErrors.password = "请输入密码";
    }
    if (!agreePolicy) {
      newErrors.agree = true;
    }

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }

    setErrors({});
    onSubmit?.({ accountName: accountName.trim(), password, agreePolicy });
  };

  return (
    <form onSubmit={handleSubmit} className="w-full flex flex-col">
      {/* 账号名输入 */}
      <div className="mb-3.5">
        <FeishuAuthInput
          placeholder="请输入你的账号名"
          value={accountName}
          onChange={(e) => {
            setAccountName(e.target.value);
            if (errors.accountName) setErrors((prev) => ({ ...prev, accountName: undefined }));
          }}
          error={errors.accountName}
          allowClear
          disabled={loading}
          autoComplete="username"
        />
      </div>

      {/* 密码输入 */}
      <div className="mb-2">
        <FeishuAuthInput
          type="password"
          placeholder="请输入你的密码"
          value={password}
          onChange={(e) => {
            setPassword(e.target.value);
            if (errors.password) setErrors((prev) => ({ ...prev, password: undefined }));
          }}
          error={errors.password}
          showPasswordToggle
          disabled={loading}
          autoComplete="current-password"
        />
      </div>

      {/* 忘记密码链接 */}
      <div className="flex justify-end mb-3.5">
        <button
          type="button"
          onClick={onForgotPassword}
          className="text-[13px] text-feishu-blue hover:text-[#245bdb] hover:underline cursor-pointer select-none"
        >
          忘记密码？
        </button>
      </div>

      {/* 登录主按钮 */}
      <FeishuAuthButton type="submit" size="lg" loading={loading}>
        登录
      </FeishuAuthButton>

      {/* 协议勾选 */}
      <div className="mt-3">
        <FeishuAgreementCheckbox
          checked={agreePolicy}
          onChange={(checked) => {
            setAgreePolicy(checked);
            if (errors.agree) setErrors((prev) => ({ ...prev, agree: false }));
          }}
          error={errors.agree}
        />
      </div>

      {/* 分割线 */}
      <FeishuAuthDivider text="或" className="my-3.5" />

      {/* 第三方快捷登录 */}
      <FeishuThirdPartyLogin variant={variant} onSelectProvider={onSelectThirdParty} />
    </form>
  );
};
