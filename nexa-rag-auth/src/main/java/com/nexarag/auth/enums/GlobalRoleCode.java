package com.nexarag.auth.enums;

/**
 * 系统内置全局角色编码，与 auth_role.role_code 字段保持一致。
 */
public enum GlobalRoleCode {

    /** 可管理模型网关与提示词等平台能力的管理员。 */
    ADMIN,

    /** 默认业务用户。 */
    USER
}
