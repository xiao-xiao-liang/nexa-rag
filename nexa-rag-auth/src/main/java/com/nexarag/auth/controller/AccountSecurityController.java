package com.nexarag.auth.controller;

import com.nexarag.auth.model.dto.EmailChangeDTO;
import com.nexarag.auth.model.dto.EmailCodeSendDTO;
import com.nexarag.auth.model.dto.EmailVerificationDTO;
import com.nexarag.auth.model.dto.PasswordSetDTO;
import com.nexarag.auth.model.vo.EmailChallengeVO;
import com.nexarag.auth.service.EmailCredentialService;
import com.nexarag.auth.service.PasswordManagementService;
import com.nexarag.auth.service.DeviceSessionService;
import com.nexarag.auth.model.vo.DeviceSessionVO;
import com.nexarag.auth.model.vo.SecurityAuditEventVO;
import com.nexarag.auth.service.SecurityAuditService;
import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 已登录账号的高风险安全操作控制器，由默认 Sa-Token 路由策略保护。
 */
@RestController
@RequestMapping("/api/auth/security")
@RequiredArgsConstructor
public class AccountSecurityController {

    private final PasswordManagementService passwordManagementService;
    private final EmailCredentialService emailCredentialService;
    private final DeviceSessionService deviceSessionService;
    private final SecurityAuditService securityAuditService;

    @GetMapping("/devices")
    public Result<java.util.List<DeviceSessionVO>> listDevices() { return Results.success(deviceSessionService.listCurrentUserSessions()); }

    @GetMapping("/audit-events")
    public Result<java.util.List<SecurityAuditEventVO>> listAuditEvents() { return Results.success(securityAuditService.listCurrentUserEvents()); }

    @PostMapping("/devices/{deviceSessionId}/kickout")
    public Result<Void> kickoutDevice(@PathVariable Long deviceSessionId) { deviceSessionService.kickoutCurrentUserSession(deviceSessionId); return Results.success(); }

    @PostMapping("/devices/logout-all")
    public Result<Void> logoutAllDevices() { deviceSessionService.logoutAllCurrentUserSessions(); return Results.success(); }

    /**
     * 向当前旧邮箱或待绑定新邮箱发送换绑验证码。
     *
     * @param sendDTO 换绑验证码发送请求
     * @return 新建验证码挑战摘要
     */
    @PostMapping("/email/send-change-code")
    public Result<EmailChallengeVO> sendEmailChangeCode(@RequestBody(required = false) EmailCodeSendDTO sendDTO) {
        return Results.success(emailCredentialService.sendEmailChangeCode(sendDTO));
    }

    /**
     * 为无邮箱凭据的已登录账号绑定首个邮箱。
     *
     * @param verificationDTO 新邮箱验证码
     * @return 无数据成功响应
     */
    @PostMapping("/email/bind-first")
    public Result<Void> bindFirstEmail(@RequestBody(required = false) EmailVerificationDTO verificationDTO) {
        emailCredentialService.bindFirstEmail(verificationDTO);
        return Results.success();
    }

    /**
     * 使用旧、新邮箱的独立验证码原子更换当前邮箱。
     *
     * @param changeDTO 双邮箱验证码请求
     * @return 无数据成功响应
     */
    @PostMapping("/email/change")
    public Result<Void> changeEmail(@RequestBody(required = false) EmailChangeDTO changeDTO) {
        emailCredentialService.changeEmail(changeDTO);
        return Results.success();
    }

    /**
     * 向当前绑定邮箱发送设置或修改密码验证码。
     *
     * @param sendDTO 邮箱验证码发送请求
     * @return 新建验证码挑战摘要
     */
    @PostMapping("/password/send-code")
    public Result<EmailChallengeVO> sendPasswordSetCode(@RequestBody(required = false) @Valid EmailCodeSendDTO sendDTO) {
        return Results.success(passwordManagementService.sendPasswordSetCode(sendDTO));
    }

    /**
     * 使用当前绑定邮箱验证码设置或修改本地密码，不撤销既有登录态。
     *
     * @param setDTO 设置密码请求
     * @return 无数据成功响应
     */
    @PostMapping("/password/set")
    public Result<Void> setPassword(@RequestBody(required = false) @Valid PasswordSetDTO setDTO) {
        passwordManagementService.setPassword(setDTO);
        return Results.success();
    }
}
