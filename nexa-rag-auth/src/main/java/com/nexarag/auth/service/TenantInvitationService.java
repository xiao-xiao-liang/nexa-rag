package com.nexarag.auth.service;

import com.nexarag.auth.model.dto.TenantInvitationCreateDTO;

/** 管理企业租户成员邀请的创建、接受、拒绝与撤销。 */
public interface TenantInvitationService {
    /** 创建邀请并返回邀请ID。 */
    Long createInvitation(TenantInvitationCreateDTO createDTO);
    /** 当前受邀用户接受邀请。 */
    void acceptInvitation(Long invitationId);
    /** 当前受邀用户拒绝邀请。 */
    void rejectInvitation(Long invitationId);
    /** 当前租户所有者撤销待接受邀请。 */
    void revokeInvitation(Long invitationId);
}
