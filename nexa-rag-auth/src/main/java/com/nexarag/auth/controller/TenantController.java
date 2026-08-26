package com.nexarag.auth.controller;

import com.nexarag.auth.model.dto.CreateTenantDTO;
import com.nexarag.auth.model.dto.TenantInvitationCreateDTO;
import com.nexarag.auth.model.dto.TenantOwnershipTransferCreateDTO;
import com.nexarag.auth.model.vo.TenantVO;
import com.nexarag.auth.service.TenantInvitationService;
import com.nexarag.auth.service.TenantMembershipService;
import com.nexarag.auth.service.TenantService;
import com.nexarag.auth.tenant.CurrentTenantService;
import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 企业工作空间与成员管理控制器，仅负责参数接收与统一结果包装。 */
@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {
    private final TenantService tenantService;
    private final TenantInvitationService invitationService;
    private final TenantMembershipService membershipService;
    private final CurrentTenantService currentTenantService;

    /** 创建企业租户。 */
    @PostMapping
    public Result<TenantVO> create(@RequestBody @Valid CreateTenantDTO createDTO) {
        return Results.success(tenantService.createEnterpriseTenant(createDTO));
    }

    /** 切换当前设备工作空间。 */
    @PostMapping("/{tenantId}/switch")
    public Result<Void> switchTenant(@PathVariable String tenantId) {
        currentTenantService.switchCurrentTenant(tenantId);
        return Results.success();
    }

    /** 创建成员邀请。 */
    @PostMapping("/invitations")
    public Result<Long> invite(@RequestBody @Valid TenantInvitationCreateDTO createDTO) {
        return Results.success(invitationService.createInvitation(createDTO));
    }

    /** 接受成员邀请。 */
    @PostMapping("/invitations/{invitationId}/accept")
    public Result<Void> acceptInvitation(@PathVariable Long invitationId) {
        invitationService.acceptInvitation(invitationId);
        return Results.success();
    }

    /** 拒绝成员邀请。 */
    @PostMapping("/invitations/{invitationId}/reject")
    public Result<Void> rejectInvitation(@PathVariable Long invitationId) {
        invitationService.rejectInvitation(invitationId);
        return Results.success();
    }

    /** 撤销成员邀请。 */
    @PostMapping("/invitations/{invitationId}/revoke")
    public Result<Void> revokeInvitation(@PathVariable Long invitationId) {
        invitationService.revokeInvitation(invitationId);
        return Results.success();
    }

    /** 移除普通成员。 */
    @PostMapping("/{tenantId}/members/{userId}/remove")
    public Result<Void> removeMember(@PathVariable String tenantId, @PathVariable Long userId) {
        membershipService.removeMember(tenantId, userId);
        return Results.success();
    }

    /** 当前普通成员退出企业租户。 */
    @PostMapping("/{tenantId}/leave")
    public Result<Void> leave(@PathVariable String tenantId) {
        membershipService.leaveTenant(tenantId);
        return Results.success();
    }

    /** 发起所有者转交。 */
    @PostMapping("/ownership-transfers")
    public Result<Long> createOwnershipTransfer(@RequestBody @Valid TenantOwnershipTransferCreateDTO createDTO) {
        return Results.success(membershipService.createOwnershipTransfer(createDTO));
    }

    /** 接受所有者转交。 */
    @PostMapping("/ownership-transfers/{transferId}/accept")
    public Result<Void> acceptOwnershipTransfer(@PathVariable Long transferId) {
        membershipService.acceptOwnershipTransfer(transferId);
        return Results.success();
    }
}
