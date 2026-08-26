import React, { useState } from "react";
import { FeishuAuthInput } from "../shared/FeishuAuthInput";
import { FeishuAuthButton } from "../shared/FeishuAuthButton";
import { FeishuAuthDivider } from "../shared/FeishuAuthDivider";
import { FeishuAgreementCheckbox } from "../shared/FeishuAgreementCheckbox";
import { FeishuThirdPartyLogin } from "../shared/FeishuThirdPartyLogin";
import { EmailCodeFormValues, OAuthProvider, BrandVariant } from "../../types";

interface EmailCodeFormProps {
  variant?: BrandVariant;
  onSubmit?: (values: EmailCodeFormValues) => void;
  onSendCode?: (email: string) => Promise<boolean> | boolean;
  onSelectThirdParty?: (provider: OAuthProvider) => void;
  loading?: boolean;
}

/**
 * 邮箱 + 验证码免密登录表单
 */
export const EmailCodeForm: React.FC<EmailCodeFormProps> = ({
  variant = "feishu",
  onSubmit,
  onSendCode,
  onSelectThirdParty,
  loading = false,
}) => {
  const [email, setEmail] = useState("");
  const [verificationCode, setVerificationCode] = useState("");
  const [agreePolicy, setAgreePolicy] = useState(false);
  const [errors, setErrors] = useState<{ email?: string; code?: string; agree?: boolean }>({});

  const handleSendCode = async (): Promise<boolean> => {
    if (!email.trim()) {
      setErrors((prev) => ({ ...prev, email: "请输入邮箱后再获取验证码" }));
      return false;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      setErrors((prev) => ({ ...prev, email: "邮箱格式不正确" }));
      return false;
    }
    setErrors((prev) => ({ ...prev, email: undefined }));
    if (onSendCode) {
      return await onSendCode(email.trim());
    }
    return true;
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const newErrors: typeof errors = {};

    if (!email.trim()) {
      newErrors.email = "请输入邮箱地址";
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      newErrors.email = "邮箱格式不正确";
    }

    if (!verificationCode.trim()) {
      newErrors.code = "请输入验证码";
    } else if (!/^\d{6}$/.test(verificationCode.trim())) {
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
    onSubmit?.({ email: email.trim(), verificationCode: verificationCode.trim(), agreePolicy });
  };

  return (
    <form onSubmit={handleSubmit} className="w-full flex flex-col">
      {/* 邮箱输入 */}
      <div className="mb-3.5">
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

      {/* 验证码输入框 + 获取验证码倒计时 */}
      <div className="mb-4">
        <FeishuAuthInput
          type="text"
          placeholder="请输入 6 位邮箱验证码"
          value={verificationCode}
          onChange={(e) => {
            setVerificationCode(e.target.value);
            if (errors.code) setErrors((prev) => ({ ...prev, code: undefined }));
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

      {/* 提交按钮 */}
      <FeishuAuthButton type="submit" size="lg" loading={loading}>
        登录 / 注册
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
      <FeishuAuthDivider text={variant === "lark" ? "更多登录方式" : "或"} className="my-3.5" />

      {/* 第三方登录 */}
      <FeishuThirdPartyLogin variant={variant} onSelectProvider={onSelectThirdParty} />
    </form>
  );
};
