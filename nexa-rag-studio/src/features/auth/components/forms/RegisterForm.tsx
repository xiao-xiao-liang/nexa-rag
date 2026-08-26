import React, { useState } from "react";
import { FeishuAuthInput } from "../shared/FeishuAuthInput";
import { FeishuAuthButton } from "../shared/FeishuAuthButton";
import { FeishuAgreementCheckbox } from "../shared/FeishuAgreementCheckbox";
import { RegisterFormValues } from "../../types";

interface RegisterFormProps {
  onSubmit?: (values: RegisterFormValues) => Promise<void> | void;
  onSendCode?: (email: string) => Promise<boolean> | boolean;
  onBackToLogin?: () => void;
  loading?: boolean;
  serverError?: string;
}

const ACCOUNT_NAME_REGEX = /^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$/;

/**
 * 新用户邮箱注册表单 (已对接后端无密码注册与验证码挑战流程)
 */
export const RegisterForm: React.FC<RegisterFormProps> = ({
  onSubmit,
  onSendCode,
  onBackToLogin,
  loading = false,
  serverError,
}) => {
  const [accountName, setAccountName] = useState("");
  const [email, setEmail] = useState("");
  const [verificationCode, setVerificationCode] = useState("");
  const [agreePolicy, setAgreePolicy] = useState(true);
  const [formError, setFormError] = useState<string | undefined>(undefined);
  const [errors, setErrors] = useState<{
    accountName?: string;
    email?: string;
    code?: string;
    agree?: boolean;
  }>({});

  const handleSendCode = async (): Promise<boolean> => {
    setFormError(undefined);
    if (!email.trim()) {
      setErrors((prev) => ({ ...prev, email: "请输入注册邮箱" }));
      return false;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      setErrors((prev) => ({ ...prev, email: "邮箱格式不正确" }));
      return false;
    }
    setErrors((prev) => ({ ...prev, email: undefined }));

    if (onSendCode) {
      try {
        const success = await onSendCode(email.trim());
        return success;
      } catch (err: any) {
        setFormError(err?.message || "发送验证码失败，请稍后重试");
        return false;
      }
    }
    return true;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(undefined);
    const newErrors: typeof errors = {};

    const trimmedAccount = accountName.trim();
    const trimmedEmail = email.trim();
    const trimmedCode = verificationCode.trim();

    if (!trimmedAccount) {
      newErrors.accountName = "请输入账号名";
    } else if (trimmedAccount.length > 39) {
      newErrors.accountName = "账号名不能超过 39 个字符";
    } else if (!ACCOUNT_NAME_REGEX.test(trimmedAccount)) {
      newErrors.accountName = "支持字母、数字及中划线，首尾须为字母或数字";
    }

    if (!trimmedEmail) {
      newErrors.email = "请输入注册邮箱";
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      newErrors.email = "邮箱格式不正确";
    }

    if (!trimmedCode) {
      newErrors.code = "请输入验证码";
    } else if (!/^\d{6}$/.test(trimmedCode)) {
      newErrors.code = "验证码必须为 6 位数字";
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
      await onSubmit?.({
        accountName: trimmedAccount,
        email: trimmedEmail,
        verificationCode: trimmedCode,
        agreePolicy,
      });
    } catch (err: any) {
      setFormError(err?.message || "注册失败，请检查填写信息后重试");
    }
  };

  const activeError = formError || serverError;

  return (
    <form onSubmit={handleSubmit} className="w-full flex flex-col">
      {/* 标题 */}
      <div className="mb-6">
        <h3 className="text-[20px] font-semibold text-[#1f2329] tracking-tight">注册账号</h3>
        <p className="text-[13px] text-[#646a73] mt-1">使用已验证邮箱完成快速注册并自动登录</p>
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

      {/* 账号名 */}
      <div className="mb-3.5">
        <FeishuAuthInput
          placeholder="设置账号名 (如 alex-dev，支持数字/字母/中划线)"
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

      {/* 邮箱 */}
      <div className="mb-3.5">
        <FeishuAuthInput
          type="email"
          placeholder="请输入注册邮箱"
          value={email}
          onChange={(e) => {
            setEmail(e.target.value);
            if (errors.email) setErrors((prev) => ({ ...prev, email: undefined }));
            if (formError) setFormError(undefined);
          }}
          error={errors.email}
          allowClear
          disabled={loading}
          autoComplete="email"
        />
      </div>

      {/* 验证码 */}
      <div className="mb-4">
        <FeishuAuthInput
          type="text"
          placeholder="请输入 6 位验证码"
          value={verificationCode}
          onChange={(e) => {
            setVerificationCode(e.target.value);
            if (errors.code) setErrors((prev) => ({ ...prev, code: undefined }));
            if (formError) setFormError(undefined);
          }}
          error={errors.code}
          maxLength={6}
          disabled={loading}
          sendCodeConfig={{
            onSend: handleSendCode,
            countdownSeconds: 60,
          }}
        />
      </div>

      {/* 注册主按钮 */}
      <FeishuAuthButton type="submit" size="lg" loading={loading}>
        立即注册并登录
      </FeishuAuthButton>

      {/* 协议勾选 */}
      <div className="mt-3.5 mb-2">
        <FeishuAgreementCheckbox
          checked={agreePolicy}
          onChange={(checked) => {
            setAgreePolicy(checked);
            if (errors.agree) setErrors((prev) => ({ ...prev, agree: false }));
          }}
          error={errors.agree}
        />
      </div>

      {/* 返回登录 */}
      <div className="mt-4 pt-3 border-t border-[#dee0e3]/60 flex items-center justify-center">
        <button
          type="button"
          onClick={onBackToLogin}
          className="text-[13px] text-[#3370ff] hover:text-[#245bdb] hover:underline cursor-pointer select-none"
        >
          已有账号？返回登录
        </button>
      </div>
    </form>
  );
};
