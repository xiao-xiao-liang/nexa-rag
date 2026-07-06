package com.nexarag.model.provider;

import com.nexarag.model.client.EmbeddingClientFactory;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.gateway.embedding.EmbeddingModelRequest;
import com.nexarag.model.gateway.embedding.EmbeddingModelResponse;
import com.nexarag.model.route.ModelRouteDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Embedding Provider，负责基于 Spring AI OpenAI 兼容协议调用向量化模型。
 */
@Component
@RequiredArgsConstructor
public class EmbeddingProvider implements ModelProviderAdapter {

    private final EmbeddingClientFactory embeddingClientFactory;

    @Override
    public boolean supports(ModelProvider provider, ModelType modelType) {
        return ModelType.EMBEDDING == modelType && provider.isOpenAiCompatible();
    }

    @Override
    public EmbeddingModelResponse embedding(ModelRouteDecision decision, EmbeddingModelRequest request) {
        // 1. 通过客户端工厂获取当前路由对应的 Spring AI 客户端
        EmbeddingRequest embeddingRequest = new EmbeddingRequest(request.texts(), null);
        EmbeddingResponse response = embeddingClientFactory.getEmbeddingClient(decision).call(embeddingRequest);

        // 2. 将 Spring AI 响应转换为模型网关统一响应
        List<float[]> embeddings = response.getResults().stream()
                .map(Embedding::getOutput)
                .toList();
        return new EmbeddingModelResponse(embeddings, decision.profileName(), totalTokens(response.getMetadata()));
    }

    private Integer totalTokens(EmbeddingResponseMetadata metadata) {
        if (metadata == null || metadata.getUsage() == null || metadata.getUsage().getTotalTokens() == null) {
            return 0;
        }
        return metadata.getUsage().getTotalTokens();
    }
}
