package com.nexarag.auth.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 租户成员角色，与 tenant_member.member_role 字段保持一致。
 */
@Getter
@RequiredArgsConstructor
public enum TenantMemberRole {

    /** 具备租户所有权的成员。 */
    OWNER(0),

    /** 普通租户成员。 */
    MEMBER(1);

    /** 数据库存储值。 */
    private final int code;
}
