CREATE TABLE auth_password_credential (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    password_hash VARCHAR(512) NOT NULL COMMENT 'Argon2id PHC密码哈希',
    failed_attempts INT NOT NULL DEFAULT 0 COMMENT '连续失败次数',
    password_locked_until DATETIME NULL COMMENT '密码验证冻结截止时间',
    password_changed_time DATETIME NOT NULL COMMENT '密码最近变更时间',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (user_id),
    KEY idx_auth_password_credential_locked_until (password_locked_until)
) COMMENT='本地密码凭据表';

CREATE TABLE auth_email_credential (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    email VARCHAR(320) NOT NULL COMMENT '展示邮箱地址',
    email_key VARCHAR(320) NOT NULL COMMENT '规范化邮箱键',
    verified_time DATETIME NOT NULL COMMENT '验证成功时间',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_auth_email_credential_email_key (email_key)
) COMMENT='当前已验证邮箱凭据表';

CREATE TABLE auth_external_identity (
    external_identity_id BIGINT NOT NULL COMMENT '第三方身份ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    provider_code VARCHAR(16) NOT NULL COMMENT '提供方：QQ、FEISHU、GOOGLE、GITHUB',
    provider_subject VARCHAR(255) NOT NULL COMMENT '提供方稳定主体标识',
    bind_time DATETIME NOT NULL COMMENT '绑定时间',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (external_identity_id),
    UNIQUE KEY uk_auth_external_identity_provider_subject (provider_code, provider_subject),
    KEY idx_auth_external_identity_user (user_id)
) COMMENT='第三方身份绑定表';

CREATE TABLE auth_email_verification_challenge (
    challenge_id BIGINT NOT NULL COMMENT '邮箱验证码挑战ID',
    user_id BIGINT NULL COMMENT '关联用户ID',
    email_key VARCHAR(320) NOT NULL COMMENT '规范化邮箱键',
    purpose_code VARCHAR(32) NOT NULL COMMENT '用途编码',
    context_hash CHAR(64) NOT NULL COMMENT '用途上下文哈希',
    expires_time DATETIME NOT NULL COMMENT '过期时间',
    verify_attempts INT NOT NULL DEFAULT 0 COMMENT '验证尝试次数',
    consumed_time DATETIME NULL COMMENT '消费时间',
    invalidated_time DATETIME NULL COMMENT '重发或撤销失效时间',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (challenge_id),
    KEY idx_auth_email_challenge_lookup (email_key, purpose_code, create_time),
    KEY idx_auth_email_challenge_user (user_id, purpose_code, create_time),
    KEY idx_auth_email_challenge_expire (expires_time)
) COMMENT='邮箱验证码挑战表';

CREATE TABLE auth_device_session (
    device_session_id BIGINT NOT NULL COMMENT '设备会话记录ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    sa_token_session_key_hash CHAR(64) NOT NULL COMMENT 'Sa-Token会话标识哈希',
    device_id_hash CHAR(64) NOT NULL COMMENT '浏览器设备标识哈希',
    device_name VARCHAR(256) NOT NULL COMMENT '设备名称',
    device_label VARCHAR(64) NULL COMMENT '用户设备备注',
    masked_ip VARCHAR(64) NULL COMMENT '脱敏IP',
    city VARCHAR(128) NULL COMMENT '市级约略地区',
    login_time DATETIME NOT NULL COMMENT '登录时间',
    last_active_time DATETIME NOT NULL COMMENT '最近活动时间',
    revoked_time DATETIME NULL COMMENT '撤销时间',
    PRIMARY KEY (device_session_id),
    UNIQUE KEY uk_auth_device_session_key_hash (sa_token_session_key_hash),
    KEY idx_auth_device_session_user_active (user_id, revoked_time, last_active_time)
) COMMENT='设备会话摘要表';

CREATE TABLE auth_security_audit_event (
    event_id BIGINT NOT NULL COMMENT '安全事件ID',
    user_id BIGINT NULL COMMENT '目标业务用户ID',
    actor_user_id BIGINT NULL COMMENT '操作者用户ID',
    event_type VARCHAR(64) NOT NULL COMMENT '安全事件类型',
    event_result TINYINT NOT NULL COMMENT '事件结果：0成功、1拒绝、2失败',
    device_session_id BIGINT NULL COMMENT '设备会话记录ID',
    masked_ip VARCHAR(64) NULL COMMENT '脱敏IP',
    city VARCHAR(128) NULL COMMENT '市级约略地区',
    detail_summary VARCHAR(512) NULL COMMENT '脱敏事件摘要',
    create_time DATETIME NOT NULL COMMENT '发生时间',
    PRIMARY KEY (event_id),
    KEY idx_auth_security_audit_user_time (user_id, create_time),
    KEY idx_auth_security_audit_expire (create_time)
) COMMENT='安全审计事件表';
