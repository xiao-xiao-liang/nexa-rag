package com.nexarag.model.registry;

import com.nexarag.model.entity.ModelConfig;
import com.nexarag.model.entity.ModelRoute;
import com.nexarag.model.entity.ModelRouteConfig;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelRouteRole;
import com.nexarag.model.enums.ModelRouteStrategy;
import com.nexarag.model.enums.ModelType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型注册表测试。
 */
class ModelRegistryTest {

    @Test
    void replaceShouldExposeLatestSnapshot() {
        ModelRegistry registry = new ModelRegistry();
        ModelConfig config = ModelConfig.builder()
                .configId(1L)
                .configKey("embedding.ollama")
                .modelType(ModelType.EMBEDDING)
                .provider(ModelProvider.OLLAMA)
                .baseUrl("http://localhost:11434/v1")
                .modelName("nomic-embed-text")
                .enabled(Boolean.TRUE)
                .version(1L)
                .build();
        ModelRoute route = ModelRoute.builder()
                .routeId(10L)
                .routeKey("embedding.document")
                .modelType(ModelType.EMBEDDING)
                .strategy(ModelRouteStrategy.PRIMARY_BACKUP)
                .enabled(Boolean.TRUE)
                .build();
        ModelRouteConfig routeConfig = ModelRouteConfig.builder()
                .routeConfigId(100L)
                .routeId(10L)
                .configId(1L)
                .role(ModelRouteRole.PRIMARY)
                .priority(0)
                .weight(100)
                .enabled(Boolean.TRUE)
                .build();

        registry.replace(new ModelRegistrySnapshot(
                2L,
                Map.of(1L, config),
                Map.of(10L, route),
                Map.of(10L, List.of(routeConfig))
        ));

        assertThat(registry.current().versionNo()).isEqualTo(2L);
        assertThat(registry.getConfig(1L)).isSameAs(config);
        assertThat(registry.getRoute(10L)).isSameAs(route);
        assertThat(registry.getRoute("embedding.document")).isSameAs(route);
        assertThat(registry.getRouteConfigs(10L)).containsExactly(routeConfig);
    }
}
