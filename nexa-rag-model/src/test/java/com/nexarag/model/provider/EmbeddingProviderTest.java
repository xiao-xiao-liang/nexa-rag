package com.nexarag.model.provider;

import com.nexarag.model.client.EmbeddingClientFactory;
import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.gateway.embedding.EmbeddingModelRequest;
import com.nexarag.model.gateway.embedding.EmbeddingModelResponse;
import com.nexarag.model.route.ModelRouteDecision;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Embedding Provider 测试。
 */
class EmbeddingProviderTest {

    @Test
    void embeddingShouldMapSpringAiResponse() {
        EmbeddingClientFactory embeddingClientFactory = mock(EmbeddingClientFactory.class);
        OpenAiEmbeddingModel embeddingModel = mock(OpenAiEmbeddingModel.class);
        EmbeddingProvider provider = new EmbeddingProvider(embeddingClientFactory);
        ModelRouteDecision decision = routeDecision();
        EmbeddingModelRequest request = EmbeddingModelRequest.builder()
                .traceId("trace-1")
                .bizType(ModelBizType.RETRIEVAL)
                .bizId("document-1")
                .routeKey("embedding")
                .texts(List.of("片段"))
                .build();

        when(embeddingClientFactory.getEmbeddingClient(decision)).thenReturn(embeddingModel);
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(new EmbeddingResponse(List.of(
                new Embedding(new float[]{0.1f, 0.2f}, 0)
        )));

        EmbeddingModelResponse response = provider.embedding(decision, request);

        assertThat(response.modelProfile()).isEqualTo("embedding-primary");
        assertThat(response.totalTokens()).isZero();
        assertThat(response.embeddings()).hasSize(1);
        assertThat(response.embeddings().getFirst()).containsExactly(0.1f, 0.2f);
    }

    @Test
    void shouldSupportOpenAiCompatibleEmbeddingProviders() {
        EmbeddingProvider provider = new EmbeddingProvider(mock(EmbeddingClientFactory.class));

        assertThat(provider.supports(ModelProvider.OPENAI, ModelType.EMBEDDING)).isTrue();
        assertThat(provider.supports(ModelProvider.OLLAMA, ModelType.EMBEDDING)).isTrue();
        assertThat(provider.supports(ModelProvider.DASHSCOPE, ModelType.EMBEDDING)).isTrue();
        assertThat(provider.supports(ModelProvider.OPENAI, ModelType.RERANK)).isFalse();
    }

    private ModelRouteDecision routeDecision() {
        ModelProfileProperties profile = new ModelProfileProperties();
        profile.setProvider("OPENAI");
        profile.setBaseUrl("https://api.openai.com/v1");
        profile.setModelName("text-embedding-3-small");
        return new ModelRouteDecision("embedding-primary", profile, false);
    }
}
