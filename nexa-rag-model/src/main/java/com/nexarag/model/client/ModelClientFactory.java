package com.nexarag.model.client;

import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.route.ModelRouteDecision;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankModel;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankOptions;
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
    private final Map<String, DashScopeRerankModel> dashScopeRerankClientCache = new ConcurrentHashMap<>();

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
     * 获取 DashScope Rerank 客户端。
     *
     * @param decision 路由决策
     * @return DashScope Rerank 客户端
     */
    public DashScopeRerankModel getDashScopeRerankClient(ModelRouteDecision decision) {
        // 1. 按路由结果生成缓存Key，避免同一模型端点重复创建客户端
        String cacheKey = dashScopeRerankCacheKey(decision);
        return dashScopeRerankClientCache.computeIfAbsent(cacheKey, key -> createDashScopeRerankClient(decision));
    }

    /**
     * 清理模型客户端缓存。
     */
    public void clear() {
        // 1. 清空所有动态客户端，配置刷新后由下一次调用重新创建
        openAiEmbeddingClientCache.clear();
        dashScopeRerankClientCache.clear();
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

    private DashScopeRerankModel createDashScopeRerankClient(ModelRouteDecision decision) {
        ModelProfileProperties profile = decision.profile();
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .baseUrl(profile.getBaseUrl())
                .apiKey(nullToEmpty(profile.getApiKey()))
                .build();
        DashScopeRerankOptions options = DashScopeRerankOptions.builder()
                .model(profile.getModelName())
                .returnDocuments(true)
                .build();
        return new DashScopeRerankModel(dashScopeApi, options);
    }

    private String dashScopeRerankCacheKey(ModelRouteDecision decision) {
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
