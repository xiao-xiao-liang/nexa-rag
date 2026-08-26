package com.nexarag.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.nexarag.auth.constants.AuthSessionConstants;
import com.nexarag.auth.service.RecentVerificationService;
import com.nexarag.auth.service.SessionService;
import com.nexarag.auth.service.DeviceSessionService;
import com.nexarag.auth.web.CsrfTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sa-Token 会话实现，将登录态建立动作延迟到数据事务成功提交之后。
 */
@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final CsrfTokenService csrfTokenService;
    private final RecentVerificationService recentVerificationService;
    private final DeviceSessionService deviceSessionService;

    /**
     * {@inheritDoc}
     */
    @Override
    public void establishLoginAfterCommit(Long userId, String tenantId) {
        establishLoginAfterCommit(userId, tenantId, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void establishLoginAfterCommit(Long userId, String tenantId, boolean grantRecentVerification) {
        Runnable establishLogin = () -> {
            StpUtil.login(userId);
            StpUtil.getTokenSession().set(AuthSessionConstants.CURRENT_TENANT_ID, tenantId);
            csrfTokenService.rotateForCurrentLogin();
            if (grantRecentVerification) {
                recentVerificationService.grantForCurrentSession();
            }
            deviceSessionService.recordCurrentLogin(userId);
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            establishLogin.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                establishLogin.run();
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void revokeAllSessionsAfterCommit(Long userId) {
        Runnable revokeSessions = () -> StpUtil.logout(userId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            revokeSessions.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                revokeSessions.run();
            }
        });
    }
}
