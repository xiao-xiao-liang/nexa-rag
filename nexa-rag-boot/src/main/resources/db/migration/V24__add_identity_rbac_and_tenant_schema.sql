CREATE TABLE auth_role (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    role_code VARCHAR(64) NOT NULL COMMENT '角色编码',
    role_name VARCHAR(128) NOT NULL COMMENT '角色名称',
    system_predefined TINYINT NOT NULL DEFAULT 1 COMMENT '是否系统预置：0否、1是',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (role_id),
    UNIQUE KEY uk_auth_role_code (role_code)
) COMMENT='全局角色表';

CREATE TABLE auth_permission (
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    permission_code VARCHAR(128) NOT NULL COMMENT '权限编码',
    permission_name VARCHAR(128) NOT NULL COMMENT '权限名称',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (permission_id),
    UNIQUE KEY uk_auth_permission_code (permission_code)
) COMMENT='全局权限表';

CREATE TABLE auth_role_permission (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (role_id, permission_id),
    KEY idx_auth_role_permission_permission (permission_id)
) COMMENT='角色权限关联表';

CREATE TABLE tenant (
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    tenant_name VARCHAR(128) NOT NULL COMMENT '租户名称',
    tenant_name_key VARCHAR(128) NOT NULL COMMENT '租户名称规范化键',
    tenant_type TINYINT NOT NULL COMMENT '租户类型：0内建默认、1企业',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '租户状态：0启用、1禁用',
    creator_user_id BIGINT NULL COMMENT '创建用户ID',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (tenant_id),
    UNIQUE KEY uk_tenant_name_key (tenant_name_key),
    KEY idx_tenant_creator_status (creator_user_id, status)
) COMMENT='租户主数据表';

CREATE TABLE auth_user (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    account_name VARCHAR(39) NOT NULL COMMENT '账号名',
    account_name_key VARCHAR(39) NOT NULL COMMENT '账号名规范化键',
    role_id BIGINT NOT NULL COMMENT '全局角色ID',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '用户状态：0启用、1禁用',
    default_tenant_id VARCHAR(64) NOT NULL COMMENT '默认租户ID',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_auth_user_account_name_key (account_name_key),
    KEY idx_auth_user_role (role_id),
    KEY idx_auth_user_status (status),
    KEY idx_auth_user_default_tenant (default_tenant_id)
) COMMENT='认证用户表';

CREATE TABLE tenant_member (
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    member_role TINYINT NOT NULL COMMENT '租户成员角色：0所有者、1普通成员',
    member_status TINYINT NOT NULL DEFAULT 0 COMMENT '成员状态：0有效、1主动退出、2被移除',
    joined_time DATETIME NOT NULL COMMENT '首次加入时间',
    status_changed_time DATETIME NOT NULL COMMENT '状态变更时间',
    PRIMARY KEY (tenant_id, user_id),
    UNIQUE KEY uk_tenant_member_user_tenant (user_id, tenant_id),
    KEY idx_tenant_member_tenant_status (tenant_id, member_status),
    KEY idx_tenant_member_user_status (user_id, member_status)
) COMMENT='租户成员表';

INSERT INTO auth_role (role_id, role_code, role_name, system_predefined, create_time, update_time)
VALUES (1, 'ADMIN', '管理员', 1, NOW(), NOW()),
       (2, 'USER', '普通用户', 1, NOW(), NOW());

INSERT INTO auth_permission (permission_id, permission_code, permission_name, create_time, update_time)
VALUES (1, 'model:manage', '模型模块管理', NOW(), NOW());

INSERT INTO auth_role_permission (role_id, permission_id, create_time)
VALUES (1, 1, NOW());

INSERT INTO tenant (tenant_id, tenant_name, tenant_name_key, tenant_type, status, creator_user_id,
                    create_time, update_time)
VALUES ('default-tenant', '默认租户', 'default-tenant', 0, 0, NULL, NOW(), NOW());
