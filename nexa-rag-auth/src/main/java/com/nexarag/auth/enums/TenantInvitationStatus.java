package com.nexarag.auth.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 企业租户邀请状态，与 tenant_invitation.invitation_status 保持一致。
 */
@Getter
@RequiredArgsConstructor
public enum TenantInvitationStatus {
    /** 等待受邀用户接受或拒绝。 */
    PENDING(0),
    /** 受邀用户已接受。 */
    ACCEPTED(1),
    /** 受邀用户已拒绝。 */
    REJECTED(2),
    /** 邀请人或所有者已撤销。 */
    REVOKED(3),
    /** 到期后不再可接受。 */
    EXPIRED(4);

    /** 数据库存储值。 */
    private final int code;
}
