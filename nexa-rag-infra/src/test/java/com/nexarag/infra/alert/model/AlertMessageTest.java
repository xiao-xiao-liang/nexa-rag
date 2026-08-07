package com.nexarag.infra.alert.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 告警消息测试，确保跨模块消息不会携带原始敏感失败详情。
 */
class AlertMessageTest {

    @Test
    void shouldSanitizeFailureReason() {
        AlertMessage message = new AlertMessage(11L, 7L, 3L, "operation-1", "CLEAN_DOCUMENT_INDEX",
                AlertSeverity.ERROR, AlertChannel.FEISHU,
                "Bearer secret-token\\r\\n/home/nexa/sk-secret", 5, LocalDateTime.of(2026, 8, 7, 18, 0));

        assertThat(message.failureReason())
                .doesNotContain("Bearer", "/home", "sk-", "\\r", "\\n");
    }
}
