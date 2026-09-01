import React, { useState, useRef, useEffect } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { AuthLayout } from "./components/layout/AuthLayout";
import { FeishuAuthCard } from "./components/shared/FeishuAuthCard";
import { AuthQrCornerSwitch } from "./components/shared/AuthQrCornerSwitch";
import { FeishuAuthTabs } from "./components/shared/FeishuAuthTabs";
import { FeishuMobileInput } from "./components/shared/FeishuMobileInput";
import { FeishuAuthInput } from "./components/shared/FeishuAuthInput";
import { FeishuAuthButton } from "./components/shared/FeishuAuthButton";
import { FeishuAuthDivider } from "./components/shared/FeishuAuthDivider";
import { FeishuThirdPartyLogin } from "./components/shared/FeishuThirdPartyLogin";
import { FeishuAgreementCheckbox } from "./components/shared/FeishuAgreementCheckbox";
import { VerificationCodeForm } from "./components/forms/VerificationCodeForm";
import { AccountPasswordForm } from "./components/forms/AccountPasswordForm";
import { RegisterForm } from "./components/forms/RegisterForm";
import { ResetPasswordForm } from "./components/forms/ResetPasswordForm";
import { QrScanLoginBox } from "./components/forms/QrScanLoginBox";
import { feishuToast } from "../../components/ui/FeishuToast";
import { authApi } from "@/lib/api.ts";
import { authStore, useAuthStore } from "./store/authStore";
import {
  AuthMainMode,
  LoginSubTab,
  BrandVariant,
  SupportedLang,
  AccountPasswordFormValues,
  RegisterFormValues,
  ResetPasswordFormValues,
  OAuthProvider,
} from "./types";

/**
 * 飞书 1:1 登录主页面容器 (已全面对接邮箱验证码登录、账号密码登录与邮箱密码登录)
 */
export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const auth = useAuthStore();

  const [mainMode, setMainMode] = useState<AuthMainMode>("form-login");
  const [subTab, setSubTab] = useState<LoginSubTab>("mobile");
  const [brandVariant, setBrandVariant] = useState<BrandVariant>("feishu");
  const [currentLang, setCurrentLang] = useState<SupportedLang>("zh-CN");
  const [loading, setLoading] = useState(false);

  // 输入框引用与滑道容器引用
  const phoneInputRef = useRef<HTMLInputElement | null>(null);
  const emailInputRef = useRef<HTMLInputElement | null>(null);
  const sliderContainerRef = useRef<HTMLDivElement | null>(null);

  // 表单状态
  const [countryCode, setCountryCode] = useState("+86");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [agreePolicy, setAgreePolicy] = useState(false); // 默认不勾选，提交时校验
  const [errors, setErrors] = useState<{ phone?: string; email?: string; agree?: boolean }>({});

  // 认证流上下文凭证（传递给验证码校验步骤）
  const [currentCredential, setCurrentCredential] = useState<{
    type: "mobile" | "email";
    value: string;
    countryCode: string;
  }>({
    type: "mobile",
    value: "",
    countryCode: "+86",
  });

  // 0. 前端初始化时预先获取 CSRF Token，自动注入后续所有状态变更请求头
  useEffect(() => {
    authApi.getCsrfToken().catch((err) => {
      console.warn("预获取 CSRF Token 失败:", err);
    });
  }, []);

  const unauthorizedToastShownRef = useRef(false);

  // 0.1 当未登录用户尝试访问受保护页面被重定向至 /login 时，弹出错误 Toast
  useEffect(() => {
    const state = location.state as { from?: { pathname: string }; unauthorized?: boolean } | null;
    if (state?.unauthorized || (state?.from && state.from.pathname !== "/login")) {
      if (!unauthorizedToastShownRef.current) {
        unauthorizedToastShownRef.current = true;
        feishuToast.show({
          id: "unauthorized-redirect-toast",
          type: "error",
          content: "请先登录",
        });
        // 清除 history 中的 state 避免刷新页面重复提示
        window.history.replaceState({}, document.title);
      }
    }
  }, [location.state]);

  // 切换折角扫码/表单模式
  const handleToggleCornerSwitch = () => {
    setMainMode((prev) => (prev === "scan-qr" ? "form-login" : "scan-qr"));
  };

  // 切换品牌风格 (飞书 <-> Lark)
  const handleSwitchBrand = () => {
    setBrandVariant((prev) => {
      const next = prev === "feishu" ? "lark" : "feishu";
      if (next === "lark") {
        setSubTab("email-password");
      } else {
        setSubTab("mobile");
      }
      return next;
    });
  };

  // 自动聚焦管理：使用 preventScroll: true 防止浏览器原生跳转打断 200ms 滑动动画
  useEffect(() => {
    if (mainMode !== "form-login") return;

    if (sliderContainerRef.current) {
      sliderContainerRef.current.scrollLeft = 0;
    }

    const timer = setTimeout(() => {
      if (subTab === "mobile") {
        phoneInputRef.current?.focus({ preventScroll: true });
        emailInputRef.current?.blur();
      } else if (subTab === "email-password") {
        emailInputRef.current?.focus({ preventScroll: true });
        phoneInputRef.current?.blur();
      }
    }, 200); // 待 200ms 滑动到位后平滑接管焦点

    return () => clearTimeout(timer);
  }, [subTab, mainMode]);

  // 第一步凭证提交：校验手机号/邮箱并发送验证码，进入验证码步骤
  const handleCredentialSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const newErrors: typeof errors = {};

    const isMobile = subTab === "mobile";
    const currentVal = isMobile ? phone.trim() : email.trim();

    if (isMobile) {
      if (!currentVal) {
        newErrors.phone = "请输入你的手机号";
      }
    } else {
      if (!currentVal) {
        newErrors.email = "请输入你的邮箱";
      } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(currentVal)) {
        newErrors.email = "邮箱格式不正确";
      }
    }

    if (newErrors.phone || newErrors.email) {
      setErrors(newErrors);
      return;
    }

    // 校验是否勾选协议与隐私政策，未勾选时弹出飞书错误 Toast 并标红复选框
    if (!agreePolicy) {
      setErrors((prev) => ({ ...prev, agree: true }));
      feishuToast.error("请阅读并同意《服务协议》和《隐私政策》");
      return;
    }

    setErrors({});

    setCurrentCredential({
      type: isMobile ? "mobile" : "email",
      value: currentVal,
      countryCode,
    });

    // 若当前输入的是邮箱，调用后端发送登录验证码接口 (POST /api/auth/email/send-code { purpose: "EMAIL_LOGIN" })
    if (!isMobile) {
      setLoading(true);
      try {
        const challenge = await authApi.sendEmailCode({
          email: currentVal,
          purpose: "EMAIL_LOGIN",
        });
        authStore.setChallenge({
          email: currentVal,
          challengeId: challenge.challengeId,
          purpose: "EMAIL_LOGIN",
          expiresTime: challenge.expiresTime,
        });
      } catch (err: any) {
        setErrors({ email: err?.message || "发送验证码失败，请检查邮箱后重试" });
        return;
      } finally {
        setLoading(false);
      }
    }

    setMainMode("verify-code");
  };

  // 验证码验证提交 (POST /api/auth/login/email-code)
  const handleVerifyCodeSubmit = async (code: string) => {
    setLoading(true);
    try {
      if (currentCredential.type === "email") {
        const challenge = authStore.getChallenge();
        const challengeId = challenge?.challengeId;

        if (!challengeId) {
          throw new Error("验证码挑战已失效，请重新获取验证码");
        }

        const session = await authApi.loginByEmailCode({
          email: currentCredential.value,
          challengeId,
          verificationCode: code,
        });

        // 登录成功，将返回的 userId 与 tenantId 存入全局 Store 与 localStorage
        authStore.setSession(session);
        console.log("邮箱验证码登录成功:", session);
        navigate("/");
      } else {
        console.log("手机号验证码登录:", currentCredential.value, code);
      }
    } finally {
      setLoading(false);
    }
  };

  // 重新获取登录验证码
  const handleResendCode = async (): Promise<boolean> => {
    if (currentCredential.type === "email") {
      try {
        const challenge = await authApi.sendEmailCode({
          email: currentCredential.value,
          purpose: "EMAIL_LOGIN",
        });
        authStore.setChallenge({
          email: currentCredential.value,
          challengeId: challenge.challengeId,
          purpose: "EMAIL_LOGIN",
          expiresTime: challenge.expiresTime,
        });
        return true;
      } catch (err: any) {
        console.error("重新发送验证码失败:", err);
        return false;
      }
    }
    return true;
  };

  // 账号名/邮箱 + 密码登录 (POST /api/auth/login/account 或 POST /api/auth/login/email-password)
  const handlePasswordLogin = async (values: AccountPasswordFormValues) => {
    setLoading(true);
    try {
      const isEmailInput = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.accountName);
      let session;

      if (isEmailInput) {
        session = await authApi.loginByEmailPassword({
          email: values.accountName,
          password: values.password,
        });
      } else {
        session = await authApi.loginByAccount({
          accountName: values.accountName,
          password: values.password,
        });
      }

      authStore.setSession(session);
      console.log("密码登录成功:", session);
      navigate("/");
    } finally {
      setLoading(false);
    }
  };

  // 注册表单提交 (POST /api/auth/register)
  const handleRegister = async (values: RegisterFormValues) => {
    setLoading(true);
    try {
      const challenge = authStore.getChallenge();
      const challengeId = challenge?.challengeId;

      if (!challengeId) {
        throw new Error("请先点击获取邮箱验证码");
      }

      const session = await authApi.register({
        accountName: values.accountName,
        email: values.email,
        challengeId,
        verificationCode: values.verificationCode,
      });

      authStore.setSession(session);
      console.log("注册并自动登录成功:", session);
      navigate("/");
    } finally {
      setLoading(false);
    }
  };

  // 找回/重置密码提交 (POST /api/auth/password/reset)
  const handleResetPassword = async (values: ResetPasswordFormValues) => {
    setLoading(true);
    try {
      const challenge = authStore.getChallenge();
      const challengeId = challenge?.challengeId || 0;

      await authApi.resetPassword({
        email: values.email,
        challengeId,
        verificationCode: values.verificationCode,
        newPassword: values.newPassword,
      });

      authStore.clearChallenge();
      setMainMode("form-login");
    } finally {
      setLoading(false);
    }
  };

  // 注册与找回密码页面的发送验证码回调
  const handleSendRegisterOrResetCode = async (
    targetEmail: string,
    purpose: "REGISTER" | "PASSWORD_RESET" = "REGISTER"
  ): Promise<boolean> => {
    try {
      const challenge = await authApi.sendEmailCode({
        email: targetEmail,
        purpose,
      });
      authStore.setChallenge({
        email: targetEmail,
        challengeId: challenge.challengeId,
        purpose,
        expiresTime: challenge.expiresTime,
      });
      return true;
    } catch (err: any) {
      console.error("发送验证码失败:", err);
      return false;
    }
  };

  // 第三方 OAuth 登录
  const handleSelectThirdParty = async (provider: OAuthProvider) => {
    try {
      const res = await authApi.startOAuth(provider);
      if (res?.authorizationUrl) {
        window.location.href = res.authorizationUrl;
      }
    } catch (err: any) {
      console.error("发起第三方登录失败:", err);
    }
  };

  // Tab 选项定义 (国内版为 [手机号, 邮箱]，Lark 国际版为 [邮箱, 手机号])
  const loginTabs =
    brandVariant === "lark"
      ? [
        { key: "email-password" as const, label: "邮箱" },
        { key: "mobile" as const, label: "手机号" },
      ]
      : [
        { key: "mobile" as const, label: "手机号" },
        { key: "email-password" as const, label: "邮箱" },
      ];

  // 是否处于第 1 个 Tab
  const isFirstTabActive =
    brandVariant === "lark" ? subTab === "email-password" : subTab === "mobile";

  // 卡片正下方的引导文案 (.login-content-bottom)
  const renderBottomPrompt = () => {
    if (mainMode === "form-login" || mainMode === "scan-qr" || mainMode === "account-password") {
      return (
        <div className="flex items-center justify-center gap-1 text-[14px] leading-5.5 text-[#646a73]">
          <span>还没有账号？</span>
          <button
            type="button"
            onClick={() => setMainMode("register")}
            className="text-feishu-blue hover:text-[#245bdb] hover:underline cursor-pointer font-normal"
          >
            立即注册
          </button>
        </div>
      );
    }
    if (mainMode === "register") {
      return (
        <div className="flex items-center justify-center gap-1 text-[14px] leading-5.5 text-[#646a73]">
          <span>已有账号？</span>
          <button
            type="button"
            onClick={() => setMainMode("form-login")}
            className="text-feishu-blue hover:text-[#245bdb] hover:underline cursor-pointer font-normal"
          >
            返回登录
          </button>
        </div>
      );
    }
    if (mainMode === "reset-password") {
      return (
        <div className="flex items-center justify-center gap-1 text-[14px] leading-5.5 text-[#646a73]">
          <span>想起密码？</span>
          <button
            type="button"
            onClick={() => setMainMode("form-login")}
            className="text-feishu-blue hover:text-[#245bdb] hover:underline cursor-pointer font-normal"
          >
            返回登录
          </button>
        </div>
      );
    }
    return null;
  };

  return (
    <AuthLayout
      variant={brandVariant}
      currentLang={currentLang}
      onSelectLang={setCurrentLang}
      bottomPrompt={renderBottomPrompt()}
    >
      <FeishuAuthCard
        cornerSwitch={
          (mainMode === "form-login" || mainMode === "scan-qr" || mainMode === "account-password") && (
            <AuthQrCornerSwitch
              mode={mainMode === "scan-qr" ? "qr" : "form"}
              onToggle={handleToggleCornerSwitch}
            />
          )
        }
      >
        {/* 1. 扫码登录面板 */}
        {mainMode === "scan-qr" && (
          <QrScanLoginBox
            variant={brandVariant}
            onSwitchBrand={handleSwitchBrand}
          />
        )}

        {/* 2. 表单登录面板（第一步：输入凭证） */}
        {mainMode === "form-login" && (
          <div className="w-full flex flex-col">
            {/* 卡片主标题 (.enter-credential__title-text: 24px, 600字重, 36px行高, 距离 Tab 间距 24px) */}
            <div className="mb-6 pr-10">
              <h2 className="text-[24px] font-semibold text-feishu-text-primary leading-9">
                {brandVariant === "lark" ? "欢迎使用 Lark" : "使用豆包或飞书账号登录"}
              </h2>
            </div>

            {/* Tab 切换栏 */}
            <FeishuAuthTabs
              tabs={loginTabs}
              activeKey={subTab}
              onChange={(key) => {
                setSubTab(key);
                setErrors({});
              }}
            />

            {/* 表单主体 */}
            <form onSubmit={handleCredentialSubmit} className="w-full flex flex-col">
              {/* 仅输入框区域进行 1:1 飞书原版 margin-left 滑道滑动 (固定 40px 高度严密裁剪, 12px 下边距) */}
              <div
                ref={sliderContainerRef}
                className="base-tabs-container w-full h-10 overflow-hidden mb-3 relative"
              >
                <div
                  className="base-tabs flex w-full h-10 transition-[margin-left] duration-200 ease-[cubic-bezier(0.645,0.045,0.355,1)] will-change-[margin-left]"
                  style={{
                    marginLeft: isFirstTabActive ? "0%" : "-100%",
                  }}
                >
                  {/* 面板 1 (国内版为手机号输入框，国际版为邮箱输入框) */}
                  <div
                    className={`base-tab-pane w-full h-10 shrink-0 transition-opacity duration-200 ${
                      isFirstTabActive ? "opacity-100" : "opacity-0 pointer-events-none"
                    }`}
                  >
                    {brandVariant === "lark" ? (
                      <FeishuAuthInput
                        inputRef={emailInputRef}
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
                    ) : (
                      <FeishuMobileInput
                        inputRef={phoneInputRef}
                        countryCode={countryCode}
                        phone={phone}
                        onPhoneChange={(val) => {
                          setPhone(val);
                          if (errors.phone) setErrors((prev) => ({ ...prev, phone: undefined }));
                        }}
                        error={errors.phone}
                        disabled={loading}
                      />
                    )}
                  </div>

                  {/* 面板 2 (国内版为邮箱输入框，国际版为手机号输入框) */}
                  <div
                    className={`base-tab-pane w-full h-10 shrink-0 transition-opacity duration-200 ${
                      !isFirstTabActive ? "opacity-100" : "opacity-0 pointer-events-none"
                    }`}
                  >
                    {brandVariant === "lark" ? (
                      <FeishuMobileInput
                        inputRef={phoneInputRef}
                        countryCode={countryCode}
                        phone={phone}
                        onPhoneChange={(val) => {
                          setPhone(val);
                          if (errors.phone) setErrors((prev) => ({ ...prev, phone: undefined }));
                        }}
                        error={errors.phone}
                        disabled={loading}
                      />
                    ) : (
                      <FeishuAuthInput
                        inputRef={emailInputRef}
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
                    )}
                  </div>
                </div>
              </div>

              {/* 错误提示文字 (展示在输入框下方) */}
              {(errors.phone || errors.email) && (
                <div className="-mt-1 mb-2 text-[12px] text-[#f54a45] leading-tight">
                  {errors.phone || errors.email}
                </div>
              )}

              {/* 下方所有元素保持绝对静止（下一步按钮、或分割线、第三方登录、协议、切换至Lark） */}
              <FeishuAuthButton type="submit" size="lg" loading={loading}>
                下一步
              </FeishuAuthButton>

              {/* 分割线 (上下边距 24px) */}
              <FeishuAuthDivider text={brandVariant === "lark" ? "更多登录方式" : "或"} />

              {/* 快捷登录按钮组 (谷歌 / GitHub / QQ) */}
              <FeishuThirdPartyLogin variant={brandVariant} onSelectProvider={handleSelectThirdParty} />

              {/* 协议与隐私政策 (上边距 16px) */}
              <div className="mt-4">
                <FeishuAgreementCheckbox
                  checked={agreePolicy}
                  variant={brandVariant}
                  onChange={(checked) => {
                    setAgreePolicy(checked);
                    if (errors.agree) setErrors((prev) => ({ ...prev, agree: false }));
                  }}
                  error={errors.agree}
                />
              </div>

              {/* 底部切换至 Lark/飞书 登录 (上边距 12px) */}
              <div className="mt-3 text-left">
                <button
                  type="button"
                  onClick={handleSwitchBrand}
                  className="text-[14px] leading-5.5 text-feishu-text-primary hover:text-[#646a73] hover:underline cursor-pointer select-none font-normal"
                >
                  {brandVariant === "lark" ? "切换至飞书登录" : "切换至Lark登录"}
                </button>
              </div>
            </form>
          </div>
        )}

        {/* 3. 验证码输入步骤面板 */}
        {mainMode === "verify-code" && (
          <VerificationCodeForm
            variant={brandVariant}
            credentialType={currentCredential.type}
            credentialValue={currentCredential.value}
            countryCode={currentCredential.countryCode}
            onBack={() => setMainMode("form-login")}
            onSubmit={handleVerifyCodeSubmit}
            onSendCode={handleResendCode}
            onSwitchOtherMethod={() => setMainMode("account-password")}
            onFindAccount={() => setMainMode("reset-password")}
            loading={loading}
          />
        )}

        {/* 4. 账号名 / 邮箱 + 密码登录面板 */}
        {mainMode === "account-password" && (
          <AccountPasswordForm
            variant={brandVariant}
            onSubmit={handlePasswordLogin}
            onSwitchVerifyCode={() => setMainMode("form-login")}
            onForgotPassword={() => setMainMode("reset-password")}
            onSelectThirdParty={handleSelectThirdParty}
            loading={loading}
          />
        )}

        {/* 5. 注册新用户面板 */}
        {mainMode === "register" && (
          <RegisterForm
            onSubmit={handleRegister}
            onSendCode={(targetEmail) => handleSendRegisterOrResetCode(targetEmail, "REGISTER")}
            onBackToLogin={() => setMainMode("form-login")}
            loading={loading}
          />
        )}

        {/* 6. 重置密码面板 */}
        {mainMode === "reset-password" && (
          <ResetPasswordForm
            onSubmit={handleResetPassword}
            onSendCode={(targetEmail) => handleSendRegisterOrResetCode(targetEmail, "PASSWORD_RESET")}
            onBackToLogin={() => setMainMode("form-login")}
            loading={loading}
          />
        )}
      </FeishuAuthCard>
    </AuthLayout>
  );
};
