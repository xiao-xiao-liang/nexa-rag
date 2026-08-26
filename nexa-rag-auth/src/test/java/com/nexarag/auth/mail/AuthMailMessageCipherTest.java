package com.nexarag.auth.mail;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 认证邮件事务消息载荷加密测试。
 */
class AuthMailMessageCipherTest {

    /** 32 字节 Base64 编码的 AES-256 测试密钥。 */
    private static final String TEST_MASTER_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    /**
     * 验证事务消息中的验证码可加密传输且每次密文不同。
     */
    @Test
    void shouldEncryptAndDecryptVerificationCodeWithRandomIv() {
        AuthMailMessageCipher cipher = new AuthMailMessageCipher(TEST_MASTER_KEY);

        String firstCiphertext = cipher.encrypt("012345");
        String secondCiphertext = cipher.encrypt("012345");

        assertThat(firstCiphertext).isNotEqualTo("012345");
        assertThat(secondCiphertext).isNotEqualTo(firstCiphertext);
        assertThat(cipher.decrypt(firstCiphertext)).isEqualTo("012345");
    }

    /**
     * 验证非法密钥会在启动配置阶段被拒绝。
     */
    @Test
    void shouldRejectInvalidMasterKey() {
        assertThatThrownBy(() -> new AuthMailMessageCipher("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
