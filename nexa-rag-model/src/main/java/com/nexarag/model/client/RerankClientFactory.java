package com.nexarag.model.client;

import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.route.ModelRouteDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rerank 客户端工厂，负责按路由配置创建并缓存重排序模型 HTTP 客户端。
 */
@Component
@RequiredArgsConstructor
public class RerankClientFactory {

    private static final String API_VERSION_PATH = "/api/v1";
    private static final String COMPATIBLE_API_VERSION_PATH = "/compatible-api/v1";
    private static final String QWEN3_RERANK_MODEL = "qwen3-rerank";

    private final RestClient.Builder restClientBuilder;
    private final Map<String, RestClient> rerankClientCache = new ConcurrentHashMap<>();

    /**
     * 获取 Rerank HTTP 客户端。
     *
     * @param decision 路由决策
     * @return Rerank HTTP 客户端
     */
    public RestClient getRerankClient(ModelRouteDecision decision) {
        // 1. 按路由结果生成缓存 Key，避免同一模型端点重复创建客户端
        String cacheKey = cacheKey(decision);
        return rerankClientCache.computeIfAbsent(cacheKey, key -> createRerankClient(decision));
    }

    /**
     * 清理客户端缓存。
     */
    public void clear() {
        // 1. 配置刷新后由下一次调用重新创建客户端
        rerankClientCache.clear();
    }

    private RestClient createRerankClient(ModelRouteDecision decision) {
        ModelProfileProperties profile = decision.profile();
        return restClientBuilder.clone()
                .baseUrl(normalizeBaseUrl(profile.getBaseUrl(), profile.getEndpointPath(), profile.getModelName()))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + nullToEmpty(profile.getApiKey()))
                .build();
    }

    private String cacheKey(ModelRouteDecision decision) {
        ModelProfileProperties profile = decision.profile();
        return String.join(":",
                decision.profileName(),
                nullToEmpty(profile.getBaseUrl()),
                nullToEmpty(profile.getEndpointPath()),
                nullToEmpty(profile.getModelName()));
    }

    private String normalizeBaseUrl(String baseUrl, String endpointPath, String modelName) {
        String normalizedBaseUrl = StringUtils.hasText(baseUrl) ? StringUtils.trimTrailingCharacter(baseUrl, '/') : "";
        boolean compatibleEndpoint = StringUtils.hasText(endpointPath)
                ? endpointPath.startsWith(COMPATIBLE_API_VERSION_PATH)
                : QWEN3_RERANK_MODEL.equals(modelName);
        if (!compatibleEndpoint) {
            return normalizedBaseUrl;
        }
        if (normalizedBaseUrl.endsWith(API_VERSION_PATH)) {
            return normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - API_VERSION_PATH.length());
        }
        if (normalizedBaseUrl.endsWith(COMPATIBLE_API_VERSION_PATH)) {
            return normalizedBaseUrl.substring(0,
                    normalizedBaseUrl.length() - COMPATIBLE_API_VERSION_PATH.length());
        }
        return normalizedBaseUrl;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
