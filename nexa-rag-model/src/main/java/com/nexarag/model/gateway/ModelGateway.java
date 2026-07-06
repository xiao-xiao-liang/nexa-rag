package com.nexarag.model.gateway;

import com.nexarag.model.execution.ModelExecutionCommand;
import com.nexarag.model.execution.ModelExecutionTemplate;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.gateway.chat.ChatModelResponse;
import com.nexarag.model.gateway.embedding.EmbeddingModelRequest;
import com.nexarag.model.gateway.embedding.EmbeddingModelResponse;
import com.nexarag.model.gateway.rerank.RerankModelRequest;
import com.nexarag.model.gateway.rerank.RerankModelResponse;
import com.nexarag.model.provider.ModelProviderDispatcher;
import com.nexarag.model.route.ModelRouteDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 统一模型网关，向业务模块提供 Chat、Embedding、Rerank 三类模型调用入口。
 */
@Service
@RequiredArgsConstructor
public class ModelGateway {

    private final ModelExecutionTemplate executionTemplate;
    private final ModelProviderDispatcher providerDispatcher;

    /**
     * 调用聊天模型。
     *
     * @param request 聊天模型请求
     * @return 聊天模型响应
     */
    public ChatModelResponse chat(ChatModelRequest request) {
        // 1. 交给执行模板统一处理路由、日志和后续治理能力
        return executionTemplate.execute(ModelExecutionCommand.ofChat(request,
                decision -> providerDispatcher.chat(decision, request)));
    }

    /**
     * 按指定路由决策调用聊天模型。
     *
     * @param decision 指定路由决策
     * @param request  聊天模型请求
     * @return 聊天模型响应
     */
    public ChatModelResponse chat(ModelRouteDecision decision, ChatModelRequest request) {
        // 1. 用指定路由决策执行，主要用于模型配置连接测试
        return executionTemplate.execute(ModelExecutionCommand.ofChat(request,
                ignored -> providerDispatcher.chat(decision, request)), decision);
    }

    /**
     * 调用向量化模型。
     *
     * @param request 向量化模型请求
     * @return 向量化模型响应
     */
    public EmbeddingModelResponse embedding(EmbeddingModelRequest request) {
        // 1. 交给执行模板统一处理路由、日志和后续治理能力
        return executionTemplate.execute(ModelExecutionCommand.ofEmbedding(request,
                decision -> providerDispatcher.embedding(decision, request)));
    }

    /**
     * 按指定路由决策调用向量化模型。
     *
     * @param decision 指定路由决策
     * @param request  向量化模型请求
     * @return 向量化模型响应
     */
    public EmbeddingModelResponse embedding(ModelRouteDecision decision, EmbeddingModelRequest request) {
        // 1. 用指定路由决策执行，主要用于模型配置连接测试
        return executionTemplate.execute(ModelExecutionCommand.ofEmbedding(request,
                ignored -> providerDispatcher.embedding(decision, request)), decision);
    }

    /**
     * 调用重排序模型。
     *
     * @param request 重排序模型请求
     * @return 重排序模型响应
     */
    public RerankModelResponse rerank(RerankModelRequest request) {
        // 1. 交给执行模板统一处理路由、日志和后续治理能力
        return executionTemplate.execute(ModelExecutionCommand.ofRerank(request,
                decision -> providerDispatcher.rerank(decision, request)));
    }

    /**
     * 按指定路由决策调用重排序模型。
     *
     * @param decision 指定路由决策
     * @param request  重排序模型请求
     * @return 重排序模型响应
     */
    public RerankModelResponse rerank(ModelRouteDecision decision, RerankModelRequest request) {
        // 1. 用指定路由决策执行，主要用于模型配置连接测试
        return executionTemplate.execute(ModelExecutionCommand.ofRerank(request,
                ignored -> providerDispatcher.rerank(decision, request)), decision);
    }
}
