package com.nexarag.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.session.SaSession;
import com.nexarag.auth.constants.AuthSessionConstants;
import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.auth.service.RecentVerificationService;
import com.nexarag.common.exception.ClientException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * 将最近验证授权保存在当前 Sa-Token Token-Session 中的实现。
 */
@Service
public class RecentVerificationServiceImpl implements RecentVerificationService {

    /** 最近验证授权有效期。 */
    private static final Duration GRANT_TTL = Duration.ofMinutes(15);

    /**
     * {@inheritDoc}
     */
    @Override
    public void grantForCurrentSession() {
        StpUtil.checkLogin();
        StpUtil.getTokenSession().set(AuthSessionConstants.RECENT_VERIFICATION_EXPIRES_AT,
                Instant.now().plus(GRANT_TTL).toEpochMilli());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasCurrentSessionGrant() {
        if (!StpUtil.isLogin()) {
            return false;
        }
        return hasGrant(StpUtil.getTokenSession());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasGrantForToken(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return false;
        }
        return hasGrant(StpUtil.getTokenSessionByToken(tokenValue));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requireGrantForToken(String tokenValue) {
        if (!hasGrantForToken(tokenValue)) {
            throw ClientException.forbidden(AuthErrorCode.RECENT_VERIFICATION_REQUIRED);
        }
    }

    /**
     * 判断并按需清理指定 Token-Session 的短期授权。
     */
    private boolean hasGrant(SaSession tokenSession) {
        if (tokenSession == null) {
            return false;
        }
        Object expiresAt = tokenSession.get(AuthSessionConstants.RECENT_VERIFICATION_EXPIRES_AT);
        long expiresAtMillis = toEpochMillis(expiresAt);
        if (expiresAtMillis > Instant.now().toEpochMilli()) {
            return true;
        }
        tokenSession.delete(AuthSessionConstants.RECENT_VERIFICATION_EXPIRES_AT);
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requireCurrentSessionGrant() {
        if (!hasCurrentSessionGrant()) {
            throw ClientException.forbidden(AuthErrorCode.RECENT_VERIFICATION_REQUIRED);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void revokeCurrentSessionGrant() {
        if (StpUtil.isLogin()) {
            StpUtil.getTokenSession().delete(AuthSessionConstants.RECENT_VERIFICATION_EXPIRES_AT);
        }
    }

    /**
     * 将会话属性转换为到期时间戳，异常值一律视为过期。
     */
    private long toEpochMillis(Object expiresAt) {
        if (expiresAt instanceof Number number) {
            return number.longValue();
        }
        if (expiresAt instanceof String value) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException exception) {
                return 0L;
            }
        }
        return 0L;
    }
}
