package com.nexarag.model.governance;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

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
}
