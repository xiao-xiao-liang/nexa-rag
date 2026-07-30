package com.nexarag.model.gateway;

import com.nexarag.model.execution.ModelExecutionCommand;
import com.nexarag.model.execution.ModelExecutionTemplate;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.gateway.chat.ChatModelResponse;
import com.nexarag.model.gateway.chat.ChatModelStreamResponse;
import com.nexarag.model.gateway.embedding.EmbeddingModelRequest;
import com.nexarag.model.gateway.embedding.EmbeddingModelResponse;
import com.nexarag.model.gateway.rerank.RerankModelRequest;
import com.nexarag.model.gateway.rerank.RerankModelResponse;
import com.nexarag.model.provider.ModelProviderDispatcher;
import com.nexarag.model.route.ModelRouteDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 统一模型网关，向业务模块提供 Chat、Embedding、Rerank 三类模型调用入口。
 */
@Service
@RequiredArgsConstructor
@Slf4j
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
        logChatPrompt(request);
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
        logChatPrompt(request);
        return executionTemplate.execute(ModelExecutionCommand.ofChat(request,
                ignored -> providerDispatcher.chat(decision, request)), decision);
    }

    /**
     * 流式调用聊天模型。
     *
     * @param request 聊天模型请求
     * @return Chat 模型流式响应分片
     */
    public Flux<ChatModelStreamResponse> streamChat(ChatModelRequest request) {
        // 1. 交给执行模板统一处理路由、日志和后续治理能力
        logChatPrompt(request);
        return executionTemplate.executeStream(ModelExecutionCommand.ofChatStream(request,
                decision -> providerDispatcher.streamChat(decision, request)));
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

    /**
     * 输出渲染完成后的 Chat 模型消息，便于排查提示词加载与变量替换结果。
     *
     * @param request Chat 模型请求
     */
    private void logChatPrompt(ChatModelRequest request) {
        if (log.isDebugEnabled()) {
            log.debug("模型提示词已加载，traceId={}，bizType={}，bizId={}，routeKey={}，messages={}",
                    request.traceId(), request.bizType(), request.bizId(), request.routeKey(), request.messages());
        }
    }
}
