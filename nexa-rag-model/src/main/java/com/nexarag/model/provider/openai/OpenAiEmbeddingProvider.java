package com.nexarag.model.provider.openai;

import com.nexarag.model.client.ModelClientFactory;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.gateway.embedding.EmbeddingModelRequest;
import com.nexarag.model.gateway.embedding.EmbeddingModelResponse;
import com.nexarag.model.provider.ModelProviderAdapter;
import com.nexarag.model.route.ModelRouteDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * OpenAI 兼容 Embedding Provider，负责调用 OpenAI 规范的向量化模型。
 */
@Component
@RequiredArgsConstructor
public class OpenAiEmbeddingProvider implements ModelProviderAdapter {

    private static final Set<ModelProvider> SUPPORTED_PROVIDERS = EnumSet.of(
            ModelProvider.OPENAI,
            ModelProvider.OLLAMA,
            ModelProvider.DEEPSEEK,
            ModelProvider.SILICONFLOW,
            ModelProvider.ZHIPU,
            ModelProvider.MOONSHOT,
            ModelProvider.CUSTOM_OPENAI
    );

    private final ModelClientFactory modelClientFactory;

    @Override
    public boolean supports(ModelProvider provider, ModelType modelType) {
        return ModelType.EMBEDDING == modelType && SUPPORTED_PROVIDERS.contains(provider);
    }

    @Override
    public EmbeddingModelResponse embedding(ModelRouteDecision decision, EmbeddingModelRequest request) {
        // 1. 通过客户端工厂获取当前路由对应的 Spring AI 客户端
        EmbeddingRequest embeddingRequest = new EmbeddingRequest(request.texts(), null);
        EmbeddingResponse response = modelClientFactory.getOpenAiEmbeddingClient(decision).call(embeddingRequest);

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
