package com.nexarag.auth.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 租户成员关系状态，与 tenant_member.member_status 字段保持一致。
 */
@Getter
@RequiredArgsConstructor
public enum TenantMemberStatus {

    /** 成员关系有效。 */
    ACTIVE(0),

    /** 用户主动退出租户。 */
    LEFT(1),

    /** 用户已被租户移除。 */
    REMOVED(2);

    /** 数据库存储值。 */
    private final int code;
}
