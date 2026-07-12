package com.nexarag.chat.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.VirtualThreadTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话摘要虚拟线程执行器配置测试。
 */
class ExecutorConfigurationTest {

    @Test
    void shouldCreateVirtualThreadExecutor() {
        ExecutorConfiguration configuration = new ExecutorConfiguration();

        assertThat(configuration.chatSummaryExecutor()).isInstanceOf(VirtualThreadTaskExecutor.class);
    }
}
