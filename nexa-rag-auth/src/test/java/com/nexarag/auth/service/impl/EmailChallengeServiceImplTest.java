package com.nexarag.auth.service.impl;

import com.nexarag.auth.enums.EmailVerificationPurpose;
import com.nexarag.auth.mail.AuthEmailMessagePublisher;
import com.nexarag.auth.redis.EmailVerificationRedisStore;
import com.nexarag.auth.service.EmailVerificationCodeHasher;
import com.nexarag.common.exception.ClientException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 邮箱验证码服务 Redis 挑战创建测试。 */
class EmailChallengeServiceImplTest {

    @Test
    void shouldNotPublishMailWhenRedisChallengeCreationFails() {
        EmailVerificationRedisStore redisStore = mock(EmailVerificationRedisStore.class);
        when(redisStore.create(any(), any(), any(), any())).thenReturn(false);
        AuthEmailMessagePublisher publisher = mock(AuthEmailMessagePublisher.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(1L);
        EmailChallengeServiceImpl service = new EmailChallengeServiceImpl(redisStore, redisTemplate,
                publisher, new EmailVerificationCodeHasher("test-pepper"));

        assertThrows(ClientException.class,
                () -> service.sendCode("user@example.com", EmailVerificationPurpose.REGISTER, null));

        verify(publisher, never()).publish(any(), any(), any(), any());
    }
}
