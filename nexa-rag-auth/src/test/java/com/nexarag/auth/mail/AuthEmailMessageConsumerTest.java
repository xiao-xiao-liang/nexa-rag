package com.nexarag.auth.mail;

import com.nexarag.auth.enums.EmailVerificationPurpose;
import com.nexarag.auth.mail.message.AuthEmailVerificationMessage;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 普通认证邮件消息消费测试。 */
class AuthEmailMessageConsumerTest {

    @Test
    void shouldDecryptAndDeliverVerificationCode() {
        AuthMailMessageCipher cipher = mock(AuthMailMessageCipher.class);
        when(cipher.decrypt("encrypted-email")).thenReturn("user@example.com");
        when(cipher.decrypt("encrypted-code")).thenReturn("012345");
        AuthMailService mailService = mock(AuthMailService.class);
        AuthEmailMessageConsumer consumer = new AuthEmailMessageConsumer(cipher, mailService);

        consumer.onMessage(new AuthEmailVerificationMessage(100L, "encrypted-email",
                EmailVerificationPurpose.EMAIL_LOGIN, "encrypted-code"));

        verify(mailService).sendVerificationCode("user@example.com", EmailVerificationPurpose.EMAIL_LOGIN, "012345");
    }
}
