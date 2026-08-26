package com.nexarag.auth.service;

import com.nexarag.auth.model.dto.TenantOwnershipTransferCreateDTO;

/** 管理企业租户成员移除、退出与所有者双确认转交。 */
public interface TenantMembershipService {
    /** 当前所有者移除普通成员。 */
    void removeMember(String tenantId, Long targetUserId);
    /** 当前普通成员退出企业租户。 */
    void leaveTenant(String tenantId);
    /** 当前所有者发起面向现有成员的转交。 */
    Long createOwnershipTransfer(TenantOwnershipTransferCreateDTO createDTO);
    /** 当前目标成员接受所有者转交。 */
    void acceptOwnershipTransfer(Long transferId);
}
