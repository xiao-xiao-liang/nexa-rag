package com.nexarag.model.governance;

import com.nexarag.model.config.ModelGovernanceProperties;
import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.entity.ModelGovernanceConfig;
import com.nexarag.model.enums.ModelGovernanceBindingMode;
import com.nexarag.model.registry.ModelRegistry;
import com.nexarag.model.registry.ModelRegistrySnapshot;
import com.nexarag.model.route.ModelRouteDecision;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型治理配置解析器测试。
 */
class ModelGovernanceResolverTest {

    @Test
    void configModeShouldResolveByConfigId() {
        ModelRegistry registry = new ModelRegistry();
        ModelGovernanceConfig governance = ModelGovernanceConfig.builder()
                .bindingMode(ModelGovernanceBindingMode.CONFIG)
                .configId(1001L)
                .enabled(Boolean.TRUE)
                .rateLimitEnabled(Boolean.TRUE)
                .limitForPeriod(10)
                .build();
        registry.replace(new ModelRegistrySnapshot(7L, Map.of(), Map.of(), Map.of(),
                Map.of("CONFIG:1001", governance)));

        ModelGovernanceProperties properties = new ModelGovernanceProperties();
        properties.getGovernance().setBindingMode(ModelGovernanceBindingMode.CONFIG);
        ModelGovernanceResolver resolver = new ModelGovernanceResolver(registry, properties);

        ModelGovernanceSettings settings = resolver.resolve("chat", decision(1001L));

        assertThat(settings.getRateLimitEnabled()).isTrue();
        assertThat(settings.getLimitForPeriod()).isEqualTo(10);
    }

    @Test
    void routeModeShouldResolveByRouteKey() {
        ModelRegistry registry = new ModelRegistry();
        ModelGovernanceConfig governance = ModelGovernanceConfig.builder()
                .bindingMode(ModelGovernanceBindingMode.ROUTE)
                .routeKey("chat")
                .enabled(Boolean.TRUE)
                .bulkheadEnabled(Boolean.TRUE)
                .maxConcurrentCalls(3)
                .build();
        registry.replace(new ModelRegistrySnapshot(8L, Map.of(), Map.of(), Map.of(),
                Map.of("ROUTE:chat", governance)));

        ModelGovernanceProperties properties = new ModelGovernanceProperties();
        properties.getGovernance().setBindingMode(ModelGovernanceBindingMode.ROUTE);
        ModelGovernanceResolver resolver = new ModelGovernanceResolver(registry, properties);

        ModelGovernanceSettings settings = resolver.resolve("chat", decision(1001L));

        assertThat(settings.getBulkheadEnabled()).isTrue();
        assertThat(settings.getMaxConcurrentCalls()).isEqualTo(3);
    }

    @Test
    void missingGovernanceConfigShouldReturnDisabledSettings() {
        ModelRegistry registry = new ModelRegistry();
        ModelGovernanceProperties properties = new ModelGovernanceProperties();
        ModelGovernanceResolver resolver = new ModelGovernanceResolver(registry, properties);

        ModelGovernanceSettings settings = resolver.resolve("chat", decision(1001L));

        assertThat(settings.getRetryEnabled()).isFalse();
        assertThat(settings.getCircuitEnabled()).isFalse();
        assertThat(settings.getRateLimitEnabled()).isFalse();
        assertThat(settings.getBulkheadEnabled()).isFalse();
    }

    private ModelRouteDecision decision(Long configId) {
        return new ModelRouteDecision("chat-primary", ModelProfileProperties.builder().build(),
                false, null, null, null, configId);
    }
}
