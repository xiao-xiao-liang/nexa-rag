package com.nexarag.model.client;

import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.route.ModelRouteDecision;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.model.ApiKey;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型客户端工厂，负责管理动态模型客户端缓存。
 */
@Component
public class ModelClientFactory {

    private final Map<String, OpenAiEmbeddingModel> openAiEmbeddingClientCache = new ConcurrentHashMap<>();

    /**
     * 获取 OpenAI 兼容 Embedding 客户端。
     *
     * @param decision 路由决策
     * @return OpenAI Embedding 客户端
     */
    public OpenAiEmbeddingModel getOpenAiEmbeddingClient(ModelRouteDecision decision) {
        // 1. 按路由结果生成缓存Key，避免同一模型端点重复创建客户端
        String cacheKey = openAiEmbeddingCacheKey(decision);
        return openAiEmbeddingClientCache.computeIfAbsent(cacheKey, key -> createOpenAiEmbeddingClient(decision));
    }

    /**
     * 清理模型客户端缓存。
     */
    public void clear() {
        // 1. 清空所有动态客户端，配置刷新后由下一次调用重新创建
        openAiEmbeddingClientCache.clear();
    }

    private OpenAiEmbeddingModel createOpenAiEmbeddingClient(ModelRouteDecision decision) {
        ModelProfileProperties profile = decision.profile();
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(profile.getBaseUrl())
                .apiKey((ApiKey) () -> StringUtils.hasText(profile.getApiKey()) ? profile.getApiKey() : "")
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(profile.getModelName())
                .build();
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
    }

    private String openAiEmbeddingCacheKey(ModelRouteDecision decision) {
        ModelProfileProperties profile = decision.profile();
        return String.join(":",
                decision.profileName(),
                nullToEmpty(profile.getBaseUrl()),
                nullToEmpty(profile.getModelName()));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
