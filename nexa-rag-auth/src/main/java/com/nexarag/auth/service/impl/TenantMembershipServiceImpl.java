package com.nexarag.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexarag.auth.context.UserContext;
import com.nexarag.auth.enums.TenantMemberRole;
import com.nexarag.auth.enums.TenantMemberStatus;
import com.nexarag.auth.enums.TenantOwnershipTransferStatus;
import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.auth.mapper.TenantMemberMapper;
import com.nexarag.auth.mapper.TenantOwnershipTransferMapper;
import com.nexarag.auth.model.dataobject.TenantMemberDO;
import com.nexarag.auth.model.dataobject.TenantOwnershipTransferDO;
import com.nexarag.auth.model.dto.TenantOwnershipTransferCreateDTO;
import com.nexarag.auth.service.RecentVerificationService;
import com.nexarag.auth.service.SecurityAuditService;
import com.nexarag.auth.service.TenantMembershipService;
import com.nexarag.common.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 企业租户成员和所有者转交实现，所有角色变更均在单一事务中完成。 */
@Service
@RequiredArgsConstructor
public class TenantMembershipServiceImpl implements TenantMembershipService {
    private final TenantMemberMapper tenantMemberMapper;
    private final TenantOwnershipTransferMapper transferMapper;
    private final RecentVerificationService recentVerificationService;
    private final SecurityAuditService securityAuditService;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void removeMember(String tenantId, Long targetUserId) {
        Long actorUserId = Long.valueOf(UserContext.getUserId());
        requireOwner(tenantId, actorUserId);
        TenantMemberDO target = tenantMemberMapper.selectActiveByTenantIdAndUserIdForUpdate(tenantId, targetUserId);
        if (target == null || target.getMemberRole() != TenantMemberRole.MEMBER.getCode()) {
            throw new ClientException(AuthErrorCode.TENANT_OPERATION_INVALID);
        }
        tenantMemberMapper.updateRoleAndStatus(tenantId, targetUserId, TenantMemberRole.MEMBER.getCode(),
                TenantMemberStatus.REMOVED.getCode(), LocalDateTime.now());
        securityAuditService.recordSuccess(actorUserId, "TENANT_MEMBER_REMOVED", "已移除企业租户成员");
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void leaveTenant(String tenantId) {
        Long userId = Long.valueOf(UserContext.getUserId());
        TenantMemberDO member = tenantMemberMapper.selectActiveByTenantIdAndUserIdForUpdate(tenantId, userId);
        if (member == null || member.getMemberRole() != TenantMemberRole.MEMBER.getCode()) {
            throw new ClientException(AuthErrorCode.TENANT_OPERATION_INVALID);
        }
        tenantMemberMapper.updateRoleAndStatus(tenantId, userId, TenantMemberRole.MEMBER.getCode(),
                TenantMemberStatus.LEFT.getCode(), LocalDateTime.now());
        securityAuditService.recordSuccess(userId, "TENANT_MEMBER_LEFT", "已退出企业租户");
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public Long createOwnershipTransfer(TenantOwnershipTransferCreateDTO createDTO) {
        // 1. 发起人必须为已完成最近验证的当前所有者，目标必须是当前普通成员
        if (createDTO == null || createDTO.tenantId() == null || createDTO.targetUserId() == null) {
            throw new ClientException(AuthErrorCode.TENANT_OPERATION_INVALID);
        }
        recentVerificationService.requireCurrentSessionGrant();
        Long ownerUserId = Long.valueOf(UserContext.getUserId());
        requireOwner(createDTO.tenantId(), ownerUserId);
        TenantMemberDO target = tenantMemberMapper.selectActiveByTenantIdAndUserIdForUpdate(createDTO.tenantId(), createDTO.targetUserId());
        if (target == null || target.getMemberRole() != TenantMemberRole.MEMBER.getCode()) {
            throw new ClientException(AuthErrorCode.TENANT_OPERATION_INVALID);
        }

        // 2. 记录一次性待接受转交，未接受前不变更任一成员角色
        LocalDateTime now = LocalDateTime.now();
        Long transferId = IdWorker.getId();
        transferMapper.insert(new TenantOwnershipTransferDO(transferId, createDTO.tenantId(), ownerUserId,
                createDTO.targetUserId(), TenantOwnershipTransferStatus.PENDING.getCode(), now.plusDays(1),
                null, now, now));
        securityAuditService.recordSuccess(ownerUserId, "TENANT_OWNERSHIP_TRANSFER_CREATED", "已发起所有者转交");
        return transferId;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void acceptOwnershipTransfer(Long transferId) {
        // 1. 锁定转交记录和双方成员，确认只有指定目标成员可接受未过期记录
        if (transferId == null) throw new ClientException(AuthErrorCode.TENANT_OPERATION_INVALID);
        TenantOwnershipTransferDO transfer = transferMapper.selectByIdForUpdate(transferId);
        Long targetUserId = Long.valueOf(UserContext.getUserId());
        if (transfer == null || !targetUserId.equals(transfer.getTargetUserId())
                || transfer.getTransferStatus() != TenantOwnershipTransferStatus.PENDING.getCode()
                || !transfer.getExpiresTime().isAfter(LocalDateTime.now())) {
            throw new ClientException(AuthErrorCode.TENANT_OPERATION_INVALID);
        }
        TenantMemberDO owner = requireOwner(transfer.getTenantId(), transfer.getCurrentOwnerUserId());
        TenantMemberDO target = tenantMemberMapper.selectActiveByTenantIdAndUserIdForUpdate(transfer.getTenantId(), targetUserId);
        if (target == null || target.getMemberRole() != TenantMemberRole.MEMBER.getCode()) {
            throw new ClientException(AuthErrorCode.TENANT_OPERATION_INVALID);
        }

        // 2. 单一事务内先授予接收者 OWNER，再降级原 OWNER，始终保留至少一位所有者
        LocalDateTime now = LocalDateTime.now();
        tenantMemberMapper.updateRoleAndStatus(transfer.getTenantId(), targetUserId, TenantMemberRole.OWNER.getCode(),
                TenantMemberStatus.ACTIVE.getCode(), now);
        tenantMemberMapper.updateRoleAndStatus(transfer.getTenantId(), owner.getUserId(), TenantMemberRole.MEMBER.getCode(),
                TenantMemberStatus.ACTIVE.getCode(), now);
        transfer.setTransferStatus(TenantOwnershipTransferStatus.ACCEPTED.getCode());
        transfer.setAcceptedTime(now);
        transfer.setUpdateTime(now);
        transferMapper.updateById(transfer);
        securityAuditService.recordSuccess(targetUserId, "TENANT_OWNERSHIP_TRANSFER_ACCEPTED", "已接受所有者转交");
    }

    private TenantMemberDO requireOwner(String tenantId, Long userId) {
        TenantMemberDO member = tenantMemberMapper.selectActiveByTenantIdAndUserIdForUpdate(tenantId, userId);
        if (member == null || member.getMemberRole() != TenantMemberRole.OWNER.getCode()) {
            throw ClientException.forbidden(AuthErrorCode.TENANT_MEMBERSHIP_UNAVAILABLE);
        }
        return member;
    }
}
