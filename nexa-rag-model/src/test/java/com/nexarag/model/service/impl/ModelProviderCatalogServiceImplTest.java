package com.nexarag.model.service.impl;

import com.nexarag.model.dto.ModelProviderCatalogResponse;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型厂商推荐值服务实现类测试。
 */
class ModelProviderCatalogServiceImplTest {

    @Test
    void dashScopeShouldExposeChatEmbeddingAndRerankTypes() {
        ModelProviderCatalogServiceImpl service = new ModelProviderCatalogServiceImpl();

        ModelProviderCatalogResponse dashScope = service.listProviders().stream()
                .filter(provider -> ModelProvider.DASHSCOPE == provider.provider())
                .findFirst()
                .orElseThrow();

        assertThat(dashScope.supportedTypes())
                .containsExactly(ModelType.CHAT, ModelType.EMBEDDING, ModelType.RERANK);
        assertThat(dashScope.recommendedModels())
                .containsKeys(ModelType.CHAT, ModelType.EMBEDDING, ModelType.RERANK);
    }

    @Test
    void catalogShouldExposeDefaultGovernanceDescription() {
        ModelProviderCatalogServiceImpl service = new ModelProviderCatalogServiceImpl();

        assertThat(service.listProviders())
                .anySatisfy(provider -> assertThat(provider.defaultGovernanceDescription()).contains("默认治理"));
    }
}
