package com.nexarag.common.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 敏感日志脱敏工具测试。
 */
class SensitiveLogSanitizerTest {

    @Test
    void sanitizeShouldMaskCommonSecrets() {
        String rawText = "apiKey=sk-123 password=secret token: bearer-token Authorization=Bearer abc";

        String sanitizedText = SensitiveLogSanitizer.sanitize(rawText);

        assertThat(sanitizedText).doesNotContain("sk-123", "secret", "bearer-token", "Bearer abc");
        assertThat(sanitizedText).contains("apiKey=******", "password=******", "token: ******", "Authorization=******");
    }
}
