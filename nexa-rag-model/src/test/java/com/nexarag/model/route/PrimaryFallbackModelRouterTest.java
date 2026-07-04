package com.nexarag.model.route;

import com.nexarag.model.config.ModelGovernanceProperties;
import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.config.ModelRouteProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 主备模型路由器测试。
 */
class PrimaryFallbackModelRouterTest {

    @Test
    void shouldSelectPrimaryProfileByRouteKey() {
        ModelGovernanceProperties properties = new ModelGovernanceProperties();
        ModelProfileProperties profile = new ModelProfileProperties();
        profile.setModelName("qwen2.5:7b");
        properties.getProfiles().put("chat-primary", profile);

        ModelRouteProperties route = new ModelRouteProperties();
        route.setPrimary("chat-primary");
        route.setFallback("chat-backup");
        properties.getRoutes().put("chat", route);

        PrimaryFallbackModelRouter router = new PrimaryFallbackModelRouter(properties);

        ModelRouteDecision decision = router.route(new ModelRouteContext("chat", false));

        assertThat(decision.profileName()).isEqualTo("chat-primary");
        assertThat(decision.profile().getModelName()).isEqualTo("qwen2.5:7b");
        assertThat(decision.fallback()).isFalse();
    }
}
