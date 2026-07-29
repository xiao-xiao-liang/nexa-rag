package com.nexarag.model.provider;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.gateway.chat.ChatModelResponse;
import com.nexarag.model.gateway.chat.ChatModelStreamResponse;
import com.nexarag.model.gateway.embedding.EmbeddingModelRequest;
import com.nexarag.model.gateway.embedding.EmbeddingModelResponse;
import com.nexarag.model.gateway.rerank.RerankModelRequest;
import com.nexarag.model.gateway.rerank.RerankModelResponse;
import com.nexarag.model.route.ModelRouteDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 模型厂商分发器，按厂商和模型类型选择对应 Provider 适配器。
 */
@Component
@RequiredArgsConstructor
public class ModelProviderDispatcher {

    private final List<ModelProviderAdapter> providerAdapters;

    /**
     * 分发聊天模型调用。
     *
     * @param decision 路由决策
     * @param request  聊天请求
     * @return 聊天响应
     */
    public ChatModelResponse chat(ModelRouteDecision decision, ChatModelRequest request) {
        // 1. 按路由决策中的厂商选择 Chat 适配器
        return select(decision, ModelType.CHAT).chat(decision, request);
    }

    /**
     * 分发流式聊天模型调用。
     *
     * @param decision 路由决策
     * @param request  聊天请求
     * @return Chat 模型流式响应分片
     */
    public Flux<ChatModelStreamResponse> streamChat(ModelRouteDecision decision, ChatModelRequest request) {
        // 1. 按路由决策中的厂商选择 Chat 流式适配器
        return select(decision, ModelType.CHAT).streamChat(decision, request);
    }

    /**
     * 分发向量化模型调用。
     *
     * @param decision 路由决策
     * @param request  向量化请求
     * @return 向量化响应
     */
    public EmbeddingModelResponse embedding(ModelRouteDecision decision, EmbeddingModelRequest request) {
        // 1. 按路由决策中的厂商选择 Embedding 适配器
        return select(decision, ModelType.EMBEDDING).embedding(decision, request);
    }

    /**
     * 分发重排序模型调用。
     *
     * @param decision 路由决策
     * @param request  重排序请求
     * @return 重排序响应
     */
    public RerankModelResponse rerank(ModelRouteDecision decision, RerankModelRequest request) {
        // 1. 按路由决策中的厂商选择 Rerank 适配器
        return select(decision, ModelType.RERANK).rerank(decision, request);
    }

    private ModelProviderAdapter select(ModelRouteDecision decision, ModelType modelType) {
        ModelProvider provider = parseProvider(decision.profile().getProvider());
        return providerAdapters.stream()
                .filter(adapter -> adapter.supports(provider, modelType))
                .findFirst()
                .orElseThrow(() -> new ServiceException("未找到模型厂商适配器，provider="
                        + provider + "，modelType=" + modelType, BaseErrorCode.SERVICE_ERROR));
    }

    private ModelProvider parseProvider(String provider) {
        try {
            // 1. 将配置中的厂商字符串转换为受控枚举，并兼容历史配置名称
            ModelProvider modelProvider = ModelProvider.fromJson(provider);
            if (modelProvider == null) {
                throw new IllegalArgumentException("模型厂商不能为空");
            }
            return modelProvider;
        } catch (IllegalArgumentException exception) {
            throw new ServiceException("不支持的模型厂商，provider=" + provider,
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
