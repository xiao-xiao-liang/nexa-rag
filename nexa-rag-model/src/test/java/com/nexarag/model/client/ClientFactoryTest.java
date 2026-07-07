package com.nexarag.model.client;

import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.route.ModelRouteDecision;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型客户端工厂测试。
 */
class ClientFactoryTest {

    @Test
    void chatClientCacheShouldSeparateEndpointPath() {
        ChatClientFactory factory = new ChatClientFactory();

        Object firstClient = factory.getChatClient(decision("/chat/completions", "gpt-4o-mini"));
        Object secondClient = factory.getChatClient(decision("/v1/chat/completions", "gpt-4o-mini"));

        assertThat(secondClient).isNotSameAs(firstClient);
    }

    @Test
    void clientCacheShouldSeparateSameConfigAcrossRegistryVersions() {
        ChatClientFactory factory = new ChatClientFactory();

        Object versionOne = factory.getChatClient(decision(1001L, 1L));
        Object versionTwo = factory.getChatClient(decision(1001L, 2L));

        assertThat(versionOne).isNotSameAs(versionTwo);
    }

    @Test
    void embeddingClientCacheShouldSeparateEndpointPath() {
        EmbeddingClientFactory factory = new EmbeddingClientFactory();

        Object firstClient = factory.getEmbeddingClient(decision("/embeddings", "text-embedding-3-small"));
        Object secondClient = factory.getEmbeddingClient(decision("/v1/embeddings", "text-embedding-3-small"));

        assertThat(secondClient).isNotSameAs(firstClient);
    }

    @Test
    void rerankClientCacheShouldSeparateEndpointPath() {
        RerankClientFactory factory = new RerankClientFactory(RestClient.builder());

        Object firstClient = factory.getRerankClient(decision("/compatible-api/v1/reranks", "qwen3-rerank"));
        Object secondClient = factory.getRerankClient(decision("/services/rerank/text-rerank/text-rerank",
                "gte-rerank-v2"));

        assertThat(secondClient).isNotSameAs(firstClient);
    }

    private ModelRouteDecision decision(String endpointPath, String modelName) {
        ModelProfileProperties profile = ModelProfileProperties.builder()
                .provider("OPENAI")
                .baseUrl("https://api.openai.com/v1")
                .endpointPath(endpointPath)
                .apiKey("sk-test")
                .modelName(modelName)
                .build();
        return new ModelRouteDecision("primary", profile, false);
    }

    private ModelRouteDecision decision(Long configId, Long registryVersion) {
        ModelProfileProperties profile = ModelProfileProperties.builder()
                .provider("OPENAI")
                .baseUrl("https://api.openai.com/v1")
                .endpointPath("/chat/completions")
                .apiKey("sk-test")
                .modelName("gpt-4o-mini")
                .build();
        return new ModelRouteDecision("primary", profile, false, null, null, null, configId, registryVersion);
    }
}
