package com.nexarag.auth.service.impl;

import com.nexarag.auth.enums.EmailVerificationPurpose;
import com.nexarag.auth.enums.UserStatus;
import com.nexarag.auth.mapper.AuthUserMapper;
import com.nexarag.auth.mapper.EmailCredentialMapper;
import com.nexarag.auth.mapper.PasswordCredentialMapper;
import com.nexarag.auth.model.dataobject.AuthUserDO;
import com.nexarag.auth.model.dataobject.EmailCredentialDO;
import com.nexarag.auth.model.dataobject.PasswordCredentialDO;
import com.nexarag.auth.model.dto.PasswordResetDTO;
import com.nexarag.auth.service.EmailChallengeService;
import com.nexarag.auth.service.SessionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 密码凭据短事务写入测试。
 */
class PasswordCredentialMutationServiceTest {

    /**
     * 验证事务服务直接使用事务外已生成的密码哈希，不在锁内再次计算 Argon2。
     */
    @Test
    void shouldPersistPrecomputedHashAfterConsumingResetChallenge() {
        EmailCredentialMapper emailCredentialMapper = mock(EmailCredentialMapper.class);
        AuthUserMapper authUserMapper = mock(AuthUserMapper.class);
        PasswordCredentialMapper passwordCredentialMapper = mock(PasswordCredentialMapper.class);
        EmailChallengeService emailChallengeService = mock(EmailChallengeService.class);
        SessionService sessionService = mock(SessionService.class);
        PasswordCredentialMutationService service = new PasswordCredentialMutationService(emailCredentialMapper,
                authUserMapper, passwordCredentialMapper, emailChallengeService, sessionService);
        EmailCredentialDO emailCredential = new EmailCredentialDO(1L, "user@example.com", "user@example.com",
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        AuthUserDO user = new AuthUserDO(1L, "user", "user", 2L, UserStatus.ACTIVE.getCode(), "tenant",
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        when(emailCredentialMapper.selectByEmailKeyForUpdate("user@example.com")).thenReturn(emailCredential);
        when(authUserMapper.selectByUserIdForUpdate(1L)).thenReturn(user);
        when(passwordCredentialMapper.selectByUserIdForUpdate(1L)).thenReturn(null);
        PasswordResetDTO request = new PasswordResetDTO();
        request.setEmail("user@example.com");
        request.setChallengeId(100L);
        request.setVerificationCode("012345");

        service.resetPassword(request, "argon2-precomputed");

        ArgumentCaptor<PasswordCredentialDO> credentialCaptor = ArgumentCaptor.forClass(PasswordCredentialDO.class);
        verify(emailChallengeService).verifyAndConsume(100L, "user@example.com",
                EmailVerificationPurpose.PASSWORD_RESET, 1L, "012345");
        verify(passwordCredentialMapper).insert(credentialCaptor.capture());
        verify(sessionService).revokeAllSessionsAfterCommit(1L);
        assertThat(credentialCaptor.getValue().getPasswordHash()).isEqualTo("argon2-precomputed");
    }
}
