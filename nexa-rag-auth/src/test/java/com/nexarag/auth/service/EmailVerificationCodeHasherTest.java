package com.nexarag.auth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 邮箱验证码服务端哈希测试。
 */
class EmailVerificationCodeHasherTest {

    /**
     * 验证相同验证码在不同服务端 Pepper 下产生不同哈希。
     */
    @Test
    void shouldBindCodeHashToServerSidePepper() {
        EmailVerificationCodeHasher firstHasher = new EmailVerificationCodeHasher("pepper-a");
        EmailVerificationCodeHasher secondHasher = new EmailVerificationCodeHasher("pepper-b");

        String firstHash = firstHasher.hash("context", "012345");

        assertThat(firstHash).isNotEqualTo(secondHasher.hash("context", "012345"));
        assertThat(firstHasher.matches("context", "012345", firstHash)).isTrue();
        assertThat(firstHasher.matches("context", "123456", firstHash)).isFalse();
    }
}
