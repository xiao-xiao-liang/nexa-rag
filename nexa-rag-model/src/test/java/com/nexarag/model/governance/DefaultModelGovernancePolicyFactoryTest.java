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
    void chatDefaultShouldOnlyProvideBindingIdentity() {
        DefaultModelGovernancePolicyFactory factory = new DefaultModelGovernancePolicyFactory();

        ModelGovernanceConfig config = factory.createForConfig(1001L, ModelType.CHAT);

        assertThat(config.getBindingMode()).isEqualTo(ModelGovernanceBindingMode.CONFIG);
        assertThat(config.getConfigId()).isEqualTo(1001L);
        assertThat(config.getGovernanceId()).isNotNull();
        assertThat(config.getEnabled()).isNull();
        assertThat(config.getMaxAttempts()).isNull();
        assertThat(config.getMaxConcurrentCalls()).isNull();
    }

    @Test
    void embeddingDefaultShouldOnlyOverrideChatBaselineDifferences() {
        DefaultModelGovernancePolicyFactory factory = new DefaultModelGovernancePolicyFactory();

        ModelGovernanceConfig embedding = factory.createForConfig(1002L, ModelType.EMBEDDING);

        assertThat(embedding.getEnabled()).isNull();
        assertThat(embedding.getRetryEnabled()).isTrue();
        assertThat(embedding.getMaxAttempts()).isEqualTo(2);
        assertThat(embedding.getLimitForPeriod()).isEqualTo(200);
        assertThat(embedding.getMaxConcurrentCalls()).isEqualTo(30);
    }

    @Test
    void routeDefaultShouldBindRouteKey() {
        DefaultModelGovernancePolicyFactory factory = new DefaultModelGovernancePolicyFactory();

        ModelGovernanceConfig route = factory.createForRoute("chat", ModelType.CHAT);

        assertThat(route.getBindingMode()).isEqualTo(ModelGovernanceBindingMode.ROUTE);
        assertThat(route.getRouteKey()).isEqualTo("chat");
    }
}
