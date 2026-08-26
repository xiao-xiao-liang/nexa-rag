CREATE TABLE tenant_invitation (
    invitation_id BIGINT NOT NULL COMMENT '租户邀请ID',
    tenant_id VARCHAR(64) NOT NULL COMMENT '目标租户ID',
    invited_user_id BIGINT NOT NULL COMMENT '受邀用户ID',
    inviter_user_id BIGINT NOT NULL COMMENT '邀请人用户ID',
    invitation_status TINYINT NOT NULL COMMENT '状态：0待接受、1已接受、2已拒绝、3已撤销、4已过期',
    expires_time DATETIME NOT NULL COMMENT '邀请过期时间',
    responded_time DATETIME NULL COMMENT '受邀用户响应时间',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (invitation_id),
    KEY idx_tenant_invitation_tenant_status (tenant_id, invitation_status, expires_time),
    KEY idx_tenant_invitation_user_status (invited_user_id, invitation_status, expires_time)
) COMMENT='企业租户成员邀请表';

CREATE TABLE tenant_ownership_transfer (
    transfer_id BIGINT NOT NULL COMMENT '所有者转交ID',
    tenant_id VARCHAR(64) NOT NULL COMMENT '目标租户ID',
    current_owner_user_id BIGINT NOT NULL COMMENT '发起转交的当前所有者ID',
    target_user_id BIGINT NOT NULL COMMENT '待接收所有者的成员ID',
    transfer_status TINYINT NOT NULL COMMENT '状态：0待接受、1已接受、2已取消、3已过期',
    expires_time DATETIME NOT NULL COMMENT '转交确认过期时间',
    accepted_time DATETIME NULL COMMENT '接受时间',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (transfer_id),
    KEY idx_tenant_transfer_tenant_status (tenant_id, transfer_status, expires_time),
    KEY idx_tenant_transfer_target_status (target_user_id, transfer_status, expires_time)
) COMMENT='企业租户所有者双确认转交表';
