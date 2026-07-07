package com.nexarag.model.governance;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模型治理执行器测试。
 */
class ModelGovernanceExecutorTest {

    @Test
    void retryShouldCallProviderAgainWhenEnabled() {
        AtomicInteger calls = new AtomicInteger();
        ModelGovernanceExecutor executor = new ModelGovernanceExecutor();
        ModelGovernanceSettings settings = ModelGovernanceSettings.builder()
                .retryEnabled(true)
                .maxAttempts(2)
                .retryWaitMs(0)
                .build();

        String result = executor.execute("config-1", settings, () -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("第一次失败");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(2);
    }

    @Test
    void timeLimiterShouldTimeoutSlowSynchronousCall() {
        ModelGovernanceExecutor executor = new ModelGovernanceExecutor();
        ModelGovernanceSettings settings = ModelGovernanceSettings.builder()
                .timeLimiterEnabled(Boolean.TRUE)
                .timeLimiterTimeoutMs(50)
                .build();

        assertThatThrownBy(() -> executor.execute("slow-chat", settings, () -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return "ok";
        })).isInstanceOf(Exception.class);
    }
}
