package com.nexarag.model.client;

import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.route.ModelRouteDecision;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat 客户端工厂，负责按路由配置创建并缓存 OpenAI 兼容聊天模型客户端。
 */
@Component
public class ChatClientFactory {

    private static final String DEFAULT_ENDPOINT_PATH = "/chat/completions";

    private final Map<String, OpenAiChatModel> chatClientCache = new ConcurrentHashMap<>();

    /**
     * 获取 Chat 模型客户端。
     *
     * @param decision 路由决策
     * @return Chat 模型客户端
     */
    public OpenAiChatModel getChatClient(ModelRouteDecision decision) {
        // 1. 按路由结果生成缓存 Key，避免同一模型端点重复创建客户端
        String cacheKey = cacheKey(decision);
        return chatClientCache.computeIfAbsent(cacheKey, key -> createChatClient(decision));
    }

    /**
     * 清理客户端缓存。
     */
    public void clear() {
        // 1. 配置刷新后由下一次调用重新创建客户端
        chatClientCache.clear();
    }

    private OpenAiChatModel createChatClient(ModelRouteDecision decision) {
        ModelProfileProperties profile = decision.profile();
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(profile.getBaseUrl())
                .apiKey(() -> StringUtils.hasText(profile.getApiKey()) ? profile.getApiKey() : "")
                .completionsPath(normalizeEndpointPath(profile.getEndpointPath()))
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(profile.getModelName())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
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
