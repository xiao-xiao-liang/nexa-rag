package com.nexarag.model.provider;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.gateway.embedding.EmbeddingModelRequest;
import com.nexarag.model.gateway.embedding.EmbeddingModelResponse;
import com.nexarag.model.gateway.rerank.RerankModelRequest;
import com.nexarag.model.gateway.rerank.RerankModelResponse;
import com.nexarag.model.route.ModelRouteDecision;

/**
 * 模型厂商适配器，屏蔽不同模型厂商客户端的调用差异。
 */
public interface ModelProviderAdapter {

    /**
     * 判断当前适配器是否支持指定厂商和模型类型。
     *
     * @param provider  模型厂商
     * @param modelType 模型类型
     * @return 支持返回 true，否则返回 false
     */
    boolean supports(ModelProvider provider, ModelType modelType);

    /**
     * 调用向量化模型。
     *
     * @param decision 路由决策
     * @param request  向量化请求
     * @return 向量化响应
     */
    default EmbeddingModelResponse embedding(ModelRouteDecision decision, EmbeddingModelRequest request) {
        // 1. 默认实现用于防止适配器声明错误时静默失败
        throw new ServiceException("当前模型厂商暂未支持 Embedding 调用", BaseErrorCode.SERVICE_ERROR);
    }

    /**
     * 调用重排序模型。
     *
     * @param decision 路由决策
     * @param request  重排序请求
     * @return 重排序响应
     */
    default RerankModelResponse rerank(ModelRouteDecision decision, RerankModelRequest request) {
        // 1. 默认实现用于防止适配器声明错误时静默失败
        throw new ServiceException("当前模型厂商暂未支持 Rerank 调用", BaseErrorCode.SERVICE_ERROR);
    }
}
