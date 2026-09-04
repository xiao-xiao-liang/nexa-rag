-- Redis-only 验证码状态机已启用，已验证邮箱已迁移至 auth_user。
DROP TABLE IF EXISTS auth_email_verification_challenge;
DROP TABLE IF EXISTS auth_email_credential;
