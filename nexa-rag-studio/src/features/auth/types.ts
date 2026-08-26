/**
 * 认证页面主模式
 */
export type AuthMainMode =
  | "form-login"      // 基础表单登录（输入手机号/邮箱）
  | "verify-code"     // 安全验证码输入步骤
  | "account-password"// 账号/邮箱密码登录
  | "scan-qr"         // 飞书移动端/豆包扫码登录
  | "register"        // 新用户注册
  | "reset-password"; // 找回/重置密码

/**
 * 登录表单内的子 Tab 选项
 */
export type LoginSubTab =
  | "account"         // 账号名 + 密码登录
  | "email-password"  // 邮箱 + 密码登录
  | "email-code"      // 邮箱 + 验证码登录
  | "mobile";         // 手机号登录 (UI 展示)

/**
 * 品牌主题风格
 */
export type BrandVariant = "feishu" | "lark";

/**
 * 第三方登录平台编码
 */
export type OAuthProvider = "google" | "github" | "qq" | "doubao" | "sso" | "apple" | "feishu";

/**
 * 语言选项
 */
export type SupportedLang = "zh-CN" | "en-US" | "ja-JP";

/**
 * 账号密码登录表单数据
 */
export interface AccountPasswordFormValues {
  accountName: string;
  password: string;
  agreePolicy: boolean;
}

/**
 * 邮箱密码登录表单数据
 */
export interface EmailPasswordFormValues {
  email: string;
  password: string;
  agreePolicy: boolean;
}

/**
 * 邮箱验证码登录表单数据
 */
export interface EmailCodeFormValues {
  email: string;
  verificationCode: string;
  challengeId?: number;
  agreePolicy: boolean;
}

/**
 * 注册表单数据
 */
export interface RegisterFormValues {
  accountName: string;
  email: string;
  verificationCode: string;
  challengeId?: number;
  agreePolicy: boolean;
}

/**
 * 重置密码表单数据
 */
export interface ResetPasswordFormValues {
  email: string;
  verificationCode: string;
  challengeId?: number;
  newPassword: string;
  confirmPassword: string;
}
