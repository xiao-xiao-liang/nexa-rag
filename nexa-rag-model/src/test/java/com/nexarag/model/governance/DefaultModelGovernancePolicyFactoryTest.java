package com.nexarag.model.governance;

import com.nexarag.model.entity.ModelGovernanceConfig;
import com.nexarag.model.enums.ModelGovernanceBindingMode;
import com.nexarag.model.enums.ModelType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 默认模型治理策略工厂测试。
 */
class DefaultModelGovernancePolicyFactoryTest {

    @Test
    void chatDefaultShouldUseConservativeConcurrencyAndStreamTimeout() {
        DefaultModelGovernancePolicyFactory factory = new DefaultModelGovernancePolicyFactory();

        ModelGovernanceConfig config = factory.createForConfig(1001L, ModelType.CHAT);

        assertThat(config.getBindingMode()).isEqualTo(ModelGovernanceBindingMode.CONFIG);
        assertThat(config.getConfigId()).isEqualTo(1001L);
        assertThat(config.getCircuitEnabled()).isTrue();
        assertThat(config.getRateLimitEnabled()).isTrue();
        assertThat(config.getBulkheadEnabled()).isTrue();
        assertThat(config.getTimeLimiterEnabled()).isTrue();
        assertThat(config.getStreamFirstChunkTimeoutMs()).isGreaterThan(0);
        assertThat(config.getStreamMaxDurationMs()).isGreaterThan(config.getStreamFirstChunkTimeoutMs());
    }

    @Test
    void embeddingDefaultShouldAllowMoreConcurrencyThanChat() {
        DefaultModelGovernancePolicyFactory factory = new DefaultModelGovernancePolicyFactory();

        ModelGovernanceConfig chat = factory.createForConfig(1001L, ModelType.CHAT);
        ModelGovernanceConfig embedding = factory.createForConfig(1002L, ModelType.EMBEDDING);

        assertThat(embedding.getMaxConcurrentCalls()).isGreaterThan(chat.getMaxConcurrentCalls());
        assertThat(embedding.getRetryEnabled()).isTrue();
    }

    @Test
    void routeDefaultShouldBindRouteKey() {
        DefaultModelGovernancePolicyFactory factory = new DefaultModelGovernancePolicyFactory();

        ModelGovernanceConfig route = factory.createForRoute("chat", ModelType.CHAT);

        assertThat(route.getBindingMode()).isEqualTo(ModelGovernanceBindingMode.ROUTE);
        assertThat(route.getRouteKey()).isEqualTo("chat");
    }
}
