package com.nexarag.model.route;

import com.nexarag.model.config.ModelGovernanceProperties;
import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.config.ModelRouteProperties;
import com.nexarag.model.entity.ModelConfig;
import com.nexarag.model.entity.ModelRoute;
import com.nexarag.model.entity.ModelRouteConfig;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelRouteRole;
import com.nexarag.model.enums.ModelRouteStrategy;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.registry.ModelRegistry;
import com.nexarag.model.registry.ModelRegistrySnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据库优先模型路由器测试。
 */
class RegistryFirstModelRouterTest {

    @Test
    void shouldUseRegistryRouteBeforeLocalProperties() {
        ModelRegistry registry = new ModelRegistry();
        registry.replace(new ModelRegistrySnapshot(1L,
                Map.of(1L, registryConfig()),
                Map.of(10L, registryRoute()),
                Map.of(10L, List.of(registryRouteConfig()))));

        ModelGovernanceProperties properties = new ModelGovernanceProperties();
        ModelProfileProperties localProfile = new ModelProfileProperties();
        localProfile.setProvider("OPENAI_COMPATIBLE");
        localProfile.setModelName("local-old-model");
        properties.getProfiles().put("chat-primary", localProfile);
        ModelRouteProperties localRoute = new ModelRouteProperties();
        localRoute.setPrimary("chat-primary");
        properties.getRoutes().put("chat", localRoute);

        RegistryFirstModelRouter router = new RegistryFirstModelRouter(registry, new PrimaryFallbackModelRouter(properties));

        ModelRoutePlan plan = router.plan(new ModelRouteContext("chat", false));

        assertThat(plan.strategy()).isEqualTo(ModelRouteStrategy.PRIMARY_BACKUP);
        assertThat(plan.candidates()).hasSize(1);
        ModelRouteDecision decision = plan.candidates().getFirst();
        assertThat(decision.profileName()).isEqualTo("chat.dashscope.primary");
        assertThat(decision.profile().getProvider()).isEqualTo("DASHSCOPE");
        assertThat(decision.profile().getModelName()).isEqualTo("deepseek-v4-pro");
        assertThat(decision.configId()).isEqualTo(1L);
    }

    private ModelConfig registryConfig() {
        return ModelConfig.builder()
                .configId(1L)
                .configKey("chat.dashscope.primary")
                .modelType(ModelType.CHAT)
                .provider(ModelProvider.DASHSCOPE)
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .endpointPath("/chat/completions")
                .modelName("deepseek-v4-pro")
                .timeoutMs(60000)
                .maxRetries(0)
                .enabled(true)
                .build();
    }

    private ModelRoute registryRoute() {
        return ModelRoute.builder()
                .routeId(10L)
                .routeKey("chat")
                .modelType(ModelType.CHAT)
                .strategy(ModelRouteStrategy.PRIMARY_BACKUP)
                .enabled(true)
                .build();
    }

    private ModelRouteConfig registryRouteConfig() {
        return ModelRouteConfig.builder()
                .routeConfigId(100L)
                .routeId(10L)
                .configId(1L)
                .role(ModelRouteRole.PRIMARY)
                .priority(100)
                .weight(100)
                .enabled(true)
                .build();
    }
}
