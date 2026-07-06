package com.nexarag.model.client;

import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.route.ModelRouteDecision;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedding 客户端工厂，负责按路由配置创建并缓存 OpenAI 兼容向量模型客户端。
 */
@Component
public class EmbeddingClientFactory {

    private static final String DEFAULT_ENDPOINT_PATH = "/embeddings";

    private final Map<String, OpenAiEmbeddingModel> embeddingClientCache = new ConcurrentHashMap<>();

    /**
     * 获取 Embedding 模型客户端。
     *
     * @param decision 路由决策
     * @return Embedding 模型客户端
     */
    public OpenAiEmbeddingModel getEmbeddingClient(ModelRouteDecision decision) {
        // 1. 按路由结果生成缓存 Key，避免同一模型端点重复创建客户端
        String cacheKey = cacheKey(decision);
        return embeddingClientCache.computeIfAbsent(cacheKey, key -> createEmbeddingClient(decision));
    }

    /**
     * 清理客户端缓存。
     */
    public void clear() {
        // 1. 配置刷新后由下一次调用重新创建客户端
        embeddingClientCache.clear();
    }

    private OpenAiEmbeddingModel createEmbeddingClient(ModelRouteDecision decision) {
        ModelProfileProperties profile = decision.profile();
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(profile.getBaseUrl())
                .apiKey(() -> StringUtils.hasText(profile.getApiKey()) ? profile.getApiKey() : "")
                .embeddingsPath(normalizeEndpointPath(profile.getEndpointPath()))
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(profile.getModelName())
                .build();
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
    }

    private String cacheKey(ModelRouteDecision decision) {
        ModelProfileProperties profile = decision.profile();
        return String.join(":",
                decision.profileName(),
                nullToEmpty(profile.getBaseUrl()),
                nullToEmpty(profile.getModelName()),
                normalizeEndpointPath(profile.getEndpointPath()));
    }

    private String normalizeEndpointPath(String endpointPath) {
        return StringUtils.hasText(endpointPath) ? endpointPath : DEFAULT_ENDPOINT_PATH;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
