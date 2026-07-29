package com.nexarag.model.provider;

import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.gateway.embedding.EmbeddingModelRequest;
import com.nexarag.model.gateway.embedding.EmbeddingModelResponse;
import com.nexarag.model.route.ModelRouteDecision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型厂商分发器测试，验证历史厂商配置的兼容行为。
 */
class ModelProviderDispatcherTest {

    @Test
    void embeddingShouldUseCustomOpenAiAdapterForLegacyOpenAiCompatibleProvider() {
        EmbeddingModelResponse expected = new EmbeddingModelResponse(List.of(new float[]{0.1F}), "embedding-primary", 1);
        ModelProviderAdapter adapter = new ModelProviderAdapter() {
            @Override
            public boolean supports(ModelProvider provider, ModelType modelType) {
                return provider == ModelProvider.CUSTOM_OPENAI && modelType == ModelType.EMBEDDING;
            }

            @Override
            public EmbeddingModelResponse embedding(ModelRouteDecision decision, EmbeddingModelRequest request) {
                return expected;
            }
        };
        ModelProviderDispatcher dispatcher = new ModelProviderDispatcher(List.of(adapter));
        ModelProfileProperties profile = new ModelProfileProperties();
        profile.setProvider("OPENAI_COMPATIBLE");
        ModelRouteDecision decision = new ModelRouteDecision("embedding-primary", profile, false);
        EmbeddingModelRequest request = EmbeddingModelRequest.builder()
                .routeKey("embedding")
                .texts(List.of("测试文本"))
                .build();

        EmbeddingModelResponse actual = dispatcher.embedding(decision, request);

        assertThat(actual).isSameAs(expected);
    }
}
