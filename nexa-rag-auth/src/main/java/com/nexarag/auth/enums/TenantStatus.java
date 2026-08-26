package com.nexarag.auth.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 租户状态，与 tenant.status 字段保持一致。
 */
@Getter
@RequiredArgsConstructor
public enum TenantStatus {

    /** 租户可被成员正常使用。 */
    ACTIVE(0),

    /** 租户已停用，不可作为当前工作空间。 */
    DISABLED(1);

    /** 数据库存储值。 */
    private final int code;
}
