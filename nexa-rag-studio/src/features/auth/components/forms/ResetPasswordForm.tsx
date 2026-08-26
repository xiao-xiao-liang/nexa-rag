import React, { useState } from "react";
import { FeishuAuthInput } from "../shared/FeishuAuthInput";
import { FeishuAuthButton } from "../shared/FeishuAuthButton";
import { ResetPasswordFormValues } from "../../types";

interface ResetPasswordFormProps {
  onSubmit?: (values: ResetPasswordFormValues) => void;
  onSendCode?: (email: string) => Promise<boolean> | boolean;
  onBackToLogin?: () => void;
  loading?: boolean;
}

/**
 * 忘记密码 / 重置密码表单
 */
export const ResetPasswordForm: React.FC<ResetPasswordFormProps> = ({
  onSubmit,
  onSendCode,
  onBackToLogin,
  loading = false,
}) => {
  const [email, setEmail] = useState("");
  const [verificationCode, setVerificationCode] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [errors, setErrors] = useState<{
    email?: string;
    code?: string;
    newPassword?: string;
    confirmPassword?: string;
  }>({});

  const handleSendCode = async (): Promise<boolean> => {
    if (!email.trim()) {
      setErrors((prev) => ({ ...prev, email: "请输入绑定邮箱后再获取验证码" }));
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

    if (!newPassword) {
      newErrors.newPassword = "请输入新密码";
    } else if (newPassword.length < 6) {
      newErrors.newPassword = "密码长度至少 6 位";
    }

    if (newPassword !== confirmPassword) {
      newErrors.confirmPassword = "两次输入的密码不一致";
    }

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }

    setErrors({});
    onSubmit?.({
      email: email.trim(),
      verificationCode: verificationCode.trim(),
      newPassword,
      confirmPassword,
    });
  };

  return (
    <form onSubmit={handleSubmit} className="w-full flex flex-col">
      {/* 标题 */}
      <div className="mb-6">
        <h3 className="text-[20px] font-semibold text-[#1f2329] tracking-tight">重置密码</h3>
        <p className="text-[13px] text-[#646a73] mt-1">验证邮箱后设置新密码，将撤销全部历史登录态</p>
      </div>

      {/* 绑定邮箱 */}
      <div className="mb-3.5">
        <FeishuAuthInput
          type="email"
          placeholder="请输入绑定的邮箱"
          value={email}
          onChange={(e) => {
            setEmail(e.target.value);
            if (errors.email) setErrors((prev) => ({ ...prev, email: undefined }));
          }}
          error={errors.email}
          allowClear
          disabled={loading}
        />
      </div>

      {/* 邮箱验证码 */}
      <div className="mb-3.5">
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

      {/* 新密码 */}
      <div className="mb-3.5">
        <FeishuAuthInput
          type="password"
          placeholder="设置新密码 (不少于 6 位)"
          value={newPassword}
          onChange={(e) => {
            setNewPassword(e.target.value);
            if (errors.newPassword) setErrors((prev) => ({ ...prev, newPassword: undefined }));
          }}
          error={errors.newPassword}
          showPasswordToggle
          disabled={loading}
        />
      </div>

      {/* 确认新密码 */}
      <div className="mb-5">
        <FeishuAuthInput
          type="password"
          placeholder="请再次输入新密码"
          value={confirmPassword}
          onChange={(e) => {
            setConfirmPassword(e.target.value);
            if (errors.confirmPassword) setErrors((prev) => ({ ...prev, confirmPassword: undefined }));
          }}
          error={errors.confirmPassword}
          showPasswordToggle
          disabled={loading}
        />
      </div>

      {/* 提交主按钮 */}
      <FeishuAuthButton type="submit" size="lg" loading={loading}>
        确认重置密码
      </FeishuAuthButton>

      {/* 返回登录 */}
      <div className="mt-4 pt-3 border-t border-[#dee0e3]/60 flex items-center justify-center">
        <button
          type="button"
          onClick={onBackToLogin}
          className="text-[13px] text-[#3370ff] hover:text-[#245bdb] hover:underline cursor-pointer select-none"
        >
          想起密码？返回登录
        </button>
      </div>
    </form>
  );
};
