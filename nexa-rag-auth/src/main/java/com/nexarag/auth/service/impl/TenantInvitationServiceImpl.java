package com.nexarag.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexarag.auth.context.UserContext;
import com.nexarag.auth.enums.TenantInvitationStatus;
import com.nexarag.auth.enums.TenantMemberRole;
import com.nexarag.auth.enums.TenantMemberStatus;
import com.nexarag.auth.enums.UserStatus;
import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.auth.mapper.AuthUserMapper;
import com.nexarag.auth.mapper.EmailCredentialMapper;
import com.nexarag.auth.mapper.TenantInvitationMapper;
import com.nexarag.auth.mapper.TenantMemberMapper;
import com.nexarag.auth.model.dataobject.AuthUserDO;
import com.nexarag.auth.model.dataobject.EmailCredentialDO;
import com.nexarag.auth.model.dataobject.TenantInvitationDO;
import com.nexarag.auth.model.dataobject.TenantMemberDO;
import com.nexarag.auth.model.dto.TenantInvitationCreateDTO;
import com.nexarag.auth.service.AccountNamePolicy;
import com.nexarag.auth.service.SecurityAuditService;
import com.nexarag.auth.service.TenantInvitationService;
import com.nexarag.common.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

/** 企业租户邀请实现，所有授权依据均来自当前用户的成员关系。 */
@Service
@RequiredArgsConstructor
public class TenantInvitationServiceImpl implements TenantInvitationService {
    private final TenantInvitationMapper invitationMapper;
    private final TenantMemberMapper tenantMemberMapper;
    private final AuthUserMapper authUserMapper;
    private final EmailCredentialMapper emailCredentialMapper;
    private final AccountNamePolicy accountNamePolicy;
    private final SecurityAuditService securityAuditService;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public Long createInvitation(TenantInvitationCreateDTO createDTO) {
        // 1. 校验当前所有者与受邀目标
        if (createDTO == null || createDTO.tenantId() == null || createDTO.tenantId().isBlank()) {
            throw new ClientException(AuthErrorCode.TENANT_OPERATION_INVALID);
        }
        Long inviterUserId = Long.valueOf(UserContext.getUserId());
        requireOwner(createDTO.tenantId(), inviterUserId);
        AuthUserDO invitedUser = resolveActiveUser(createDTO.target());
        if (invitedUser.getUserId().equals(inviterUserId)
                || tenantMemberMapper.selectActiveByTenantIdAndUserIdForUpdate(createDTO.tenantId(), invitedUser.getUserId()) != null) {
            throw new ClientException(AuthErrorCode.TENANT_OPERATION_INVALID);
        }

        // 2. 写入独立待接受邀请，邀请本身不授予成员资格
        LocalDateTime now = LocalDateTime.now();
        Long invitationId = IdWorker.getId();
        invitationMapper.insert(new TenantInvitationDO(invitationId, createDTO.tenantId(), invitedUser.getUserId(), inviterUserId,
                TenantInvitationStatus.PENDING.getCode(), now.plusDays(7), null, now, now));
        securityAuditService.recordSuccess(inviterUserId, "TENANT_INVITATION_CREATED", "已创建企业租户成员邀请");
        return invitationId;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void acceptInvitation(Long invitationId) {
        TenantInvitationDO invitation = requirePendingInvitationForCurrentUser(invitationId);
        Long userId = Long.valueOf(UserContext.getUserId());
        LocalDateTime now = LocalDateTime.now();
        TenantMemberDO member = tenantMemberMapper.selectByTenantIdAndUserIdForUpdate(invitation.getTenantId(), userId);
        if (member == null) {
            tenantMemberMapper.insert(new TenantMemberDO(invitation.getTenantId(), userId, TenantMemberRole.MEMBER.getCode(),
                    TenantMemberStatus.ACTIVE.getCode(), now, now));
        } else if (member.getMemberStatus() != TenantMemberStatus.ACTIVE.getCode()) {
            tenantMemberMapper.updateRoleAndStatus(invitation.getTenantId(), userId, TenantMemberRole.MEMBER.getCode(),
                    TenantMemberStatus.ACTIVE.getCode(), now);
        }
        finishInvitation(invitation, TenantInvitationStatus.ACCEPTED, now);
        securityAuditService.recordSuccess(userId, "TENANT_INVITATION_ACCEPTED", "已接受企业租户邀请");
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void rejectInvitation(Long invitationId) {
        TenantInvitationDO invitation = requirePendingInvitationForCurrentUser(invitationId);
        LocalDateTime now = LocalDateTime.now();
        finishInvitation(invitation, TenantInvitationStatus.REJECTED, now);
        securityAuditService.recordSuccess(invitation.getInvitedUserId(), "TENANT_INVITATION_REJECTED", "已拒绝企业租户邀请");
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void revokeInvitation(Long invitationId) {
        TenantInvitationDO invitation = invitationMapper.selectByIdForUpdate(invitationId);
        if (invitation == null) {
            throw new ClientException(AuthErrorCode.TENANT_OPERATION_INVALID);
        }
        Long userId = Long.valueOf(UserContext.getUserId());
        requireOwner(invitation.getTenantId(), userId);
        if (invitation.getInvitationStatus() != TenantInvitationStatus.PENDING.getCode()) {
            throw new ClientException(AuthErrorCode.TENANT_OPERATION_INVALID);
        }
        finishInvitation(invitation, TenantInvitationStatus.REVOKED, LocalDateTime.now());
        securityAuditService.recordSuccess(userId, "TENANT_INVITATION_REVOKED", "已撤销企业租户邀请");
    }

    private TenantInvitationDO requirePendingInvitationForCurrentUser(Long invitationId) {
        if (invitationId == null) throw new ClientException(AuthErrorCode.TENANT_OPERATION_INVALID);
        TenantInvitationDO invitation = invitationMapper.selectByIdForUpdate(invitationId);
        if (invitation == null || !Long.valueOf(UserContext.getUserId()).equals(invitation.getInvitedUserId())
                || invitation.getInvitationStatus() != TenantInvitationStatus.PENDING.getCode()) {
            throw new ClientException(AuthErrorCode.TENANT_OPERATION_INVALID);
        }
        if (!invitation.getExpiresTime().isAfter(LocalDateTime.now())) {
            finishInvitation(invitation, TenantInvitationStatus.EXPIRED, LocalDateTime.now());
            throw new ClientException(AuthErrorCode.TENANT_OPERATION_INVALID);
        }
        return invitation;
    }

    private void finishInvitation(TenantInvitationDO invitation, TenantInvitationStatus status, LocalDateTime now) {
        invitation.setInvitationStatus(status.getCode());
        invitation.setRespondedTime(now);
        invitation.setUpdateTime(now);
        invitationMapper.updateById(invitation);
    }

    private void requireOwner(String tenantId, Long userId) {
        TenantMemberDO member = tenantMemberMapper.selectActiveByTenantIdAndUserIdForUpdate(tenantId, userId);
        if (member == null || member.getMemberRole() != TenantMemberRole.OWNER.getCode()) {
            throw ClientException.forbidden(AuthErrorCode.TENANT_MEMBERSHIP_UNAVAILABLE);
        }
    }

    private AuthUserDO resolveActiveUser(String target) {
        if (target == null || target.isBlank()) throw new ClientException(AuthErrorCode.TENANT_OPERATION_INVALID);
        AuthUserDO user;
        if (target.contains("@")) {
            EmailCredentialDO credential = emailCredentialMapper.selectByEmailKey(target.trim().toLowerCase(Locale.ROOT));
            user = credential == null ? null : authUserMapper.selectById(credential.getUserId());
        } else {
            user = authUserMapper.selectByAccountNameKey(accountNamePolicy.normalizeAndValidate(target));
        }
        if (user == null || user.getStatus() != UserStatus.ACTIVE.getCode()) {
            throw new ClientException(AuthErrorCode.TENANT_OPERATION_INVALID);
        }
        return user;
    }
}
