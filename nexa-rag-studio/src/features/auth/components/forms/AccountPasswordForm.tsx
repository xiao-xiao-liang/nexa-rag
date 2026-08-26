import React, { useState } from "react";
import { FeishuAuthInput } from "../shared/FeishuAuthInput";
import { FeishuAuthButton } from "../shared/FeishuAuthButton";
import { FeishuAuthDivider } from "../shared/FeishuAuthDivider";
import { FeishuAgreementCheckbox } from "../shared/FeishuAgreementCheckbox";
import { FeishuThirdPartyLogin } from "../shared/FeishuThirdPartyLogin";
import { AccountPasswordFormValues, OAuthProvider, BrandVariant } from "../../types";

interface AccountPasswordFormProps {
  variant?: BrandVariant;
  onSubmit?: (values: AccountPasswordFormValues) => Promise<void> | void;
  onForgotPassword?: () => void;
  onSwitchVerifyCode?: () => void;
  onSelectThirdParty?: (provider: OAuthProvider) => void;
  loading?: boolean;
  serverError?: string;
}

/**
 * 账号名 / 邮箱 + 密码登录表单 (支持 1:1 飞书风格及异常提示)
 */
export const AccountPasswordForm: React.FC<AccountPasswordFormProps> = ({
  variant = "feishu",
  onSubmit,
  onForgotPassword,
  onSwitchVerifyCode,
  onSelectThirdParty,
  loading = false,
  serverError,
}) => {
  const [accountName, setAccountName] = useState("");
  const [password, setPassword] = useState("");
  const [agreePolicy, setAgreePolicy] = useState(true);
  const [formError, setFormError] = useState<string | undefined>(undefined);
  const [errors, setErrors] = useState<{ accountName?: string; password?: string; agree?: boolean }>({});

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(undefined);
    const newErrors: typeof errors = {};

    const trimmedAccount = accountName.trim();
    if (!trimmedAccount) {
      newErrors.accountName = "请输入账号名或邮箱";
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
    try {
      await onSubmit?.({ accountName: trimmedAccount, password, agreePolicy });
    } catch (err: any) {
      setFormError(err?.message || "登录失败，请检查账号名和密码");
    }
  };

  const activeError = formError || serverError;

  return (
    <form onSubmit={handleSubmit} className="w-full flex flex-col">
      {/* 标题 */}
      <div className="mb-6 pr-10">
        <h2 className="text-[24px] font-semibold text-feishu-text-primary leading-9">
          {variant === "lark" ? "密码登录" : "使用密码登录"}
        </h2>
      </div>

      {/* 服务端/表单全局错误提示 */}
      {activeError && (
        <div className="mb-4 px-3 py-2 bg-[#feecee] text-[#f54a45] text-[13px] rounded-[6px] border border-[#f54a45]/20 flex items-center gap-1.5">
          <svg className="w-4 h-4 shrink-0 fill-current" viewBox="0 0 16 16">
            <path d="M8 1a7 7 0 100 14A7 7 0 008 1zm0 10.5a.75.75 0 110-1.5.75.75 0 010 1.5zM8.75 4.75a.75.75 0 00-1.5 0v3.5a.75.75 0 001.5 0v-3.5z" />
          </svg>
          <span className="flex-1">{activeError}</span>
        </div>
      )}

      {/* 账号名 / 邮箱输入 */}
      <div className="mb-3.5">
        <FeishuAuthInput
          placeholder="请输入账号名或绑定邮箱"
          value={accountName}
          onChange={(e) => {
            setAccountName(e.target.value);
            if (errors.accountName) setErrors((prev) => ({ ...prev, accountName: undefined }));
            if (formError) setFormError(undefined);
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
            if (formError) setFormError(undefined);
          }}
          error={errors.password}
          showPasswordToggle
          disabled={loading}
          autoComplete="current-password"
        />
      </div>

      {/* 忘记密码与切换验证码登录 */}
      <div className="flex justify-between items-center mb-3.5 text-[13px]">
        {onSwitchVerifyCode && (
          <button
            type="button"
            onClick={onSwitchVerifyCode}
            className="text-[#3370ff] hover:text-[#245bdb] hover:underline cursor-pointer select-none"
          >
            使用验证码登录
          </button>
        )}
        <button
          type="button"
          onClick={onForgotPassword}
          className="text-[#3370ff] hover:text-[#245bdb] hover:underline cursor-pointer select-none ml-auto"
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
