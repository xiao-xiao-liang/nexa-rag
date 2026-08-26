package com.nexarag.auth.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 企业租户所有者转交状态，与 tenant_ownership_transfer.transfer_status 保持一致。
 */
@Getter
@RequiredArgsConstructor
public enum TenantOwnershipTransferStatus {
    /** 等待目标成员确认。 */
    PENDING(0),
    /** 目标成员已确认并完成转交。 */
    ACCEPTED(1),
    /** 当前所有者已取消。 */
    CANCELLED(2),
    /** 转交确认已超时。 */
    EXPIRED(3);

    /** 数据库存储值。 */
    private final int code;
}
