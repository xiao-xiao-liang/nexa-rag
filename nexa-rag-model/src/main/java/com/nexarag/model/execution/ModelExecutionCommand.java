package com.nexarag.model.execution;

import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.enums.ModelRequestType;
import com.nexarag.model.gateway.embedding.EmbeddingModelRequest;
import com.nexarag.model.gateway.embedding.EmbeddingModelResponse;
import com.nexarag.model.gateway.rerank.RerankModelRequest;
import com.nexarag.model.gateway.rerank.RerankModelResponse;
import com.nexarag.model.route.ModelRouteDecision;

import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * 模型执行命令。
 *
 * @param traceId                  链路追踪ID
 * @param bizType                  业务类型
 * @param bizId                    业务ID
 * @param routeKey                 路由Key
 * @param requestType              请求类型
 * @param executor                 实际模型调用逻辑
 * @param promptTokenExtractor     输入Token提取器
 * @param completionTokenExtractor 输出Token提取器
 * @param totalTokenExtractor      总Token提取器
 * @param <T>                      模型响应类型
 */
public record ModelExecutionCommand<T>(
        String traceId,
        ModelBizType bizType,
        String bizId,
        String routeKey,
        ModelRequestType requestType,
        Function<ModelRouteDecision, T> executor,
        ToIntFunction<T> promptTokenExtractor,
        ToIntFunction<T> completionTokenExtractor,
        ToIntFunction<T> totalTokenExtractor) {

    /**
     * 构造向量化模型执行命令。
     *
     * @param request  向量化请求
     * @param executor 实际调用逻辑
     * @return 向量化模型执行命令
     */
    public static ModelExecutionCommand<EmbeddingModelResponse> ofEmbedding(
            EmbeddingModelRequest request,
            Function<ModelRouteDecision, EmbeddingModelResponse> executor) {
        // 1. Embedding 只统计总 Token，输入和输出 Token 暂记为 0
        return new ModelExecutionCommand<>(
                request.traceId(),
                request.bizType(),
                request.bizId(),
                request.routeKey(),
                ModelRequestType.EMBEDDING,
                executor,
                response -> 0,
                response -> 0,
                response -> safeToken(response.totalTokens())
        );
    }

    /**
     * 构造重排序模型执行命令。
     *
     * @param request  重排序请求
     * @param executor 实际调用逻辑
     * @return 重排序模型执行命令
     */
    public static ModelExecutionCommand<RerankModelResponse> ofRerank(
            RerankModelRequest request,
            Function<ModelRouteDecision, RerankModelResponse> executor) {
        // 1. Rerank 只统计总 Token，输入和输出 Token 暂记为 0
        return new ModelExecutionCommand<>(
                request.traceId(),
                request.bizType(),
                request.bizId(),
                request.routeKey(),
                ModelRequestType.RERANK,
                executor,
                response -> 0,
                response -> 0,
                response -> safeToken(response.totalTokens())
        );
    }

    private static int safeToken(Integer token) {
        return token == null ? 0 : token;
    }
}
