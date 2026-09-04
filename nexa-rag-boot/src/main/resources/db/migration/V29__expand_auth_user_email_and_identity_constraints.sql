ALTER TABLE auth_user
    ADD COLUMN email VARCHAR(320) NULL COMMENT '已验证且规范化的邮箱地址' AFTER default_tenant_id,
    ADD COLUMN email_verified_time DATETIME NULL COMMENT '当前邮箱验证完成时间' AFTER email;

UPDATE auth_user user INNER JOIN auth_email_credential credential ON credential.user_id = user.user_id
SET user.email = credential.email_key,
    user.email_verified_time = credential.verified_time
WHERE user.email IS NULL;

-- 同一用户同一平台存在多条绑定时拒绝继续迁移，由人工确认保留记录，禁止自动删除。
SELECT user_id, provider_code, COUNT(*) AS binding_count
FROM auth_external_identity
GROUP BY user_id, provider_code
HAVING COUNT(*) > 1;

ALTER TABLE auth_user
    ADD UNIQUE KEY uk_auth_user_email (email);

ALTER TABLE auth_external_identity
    ADD UNIQUE KEY uk_auth_external_identity_user_provider (user_id, provider_code);
