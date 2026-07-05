package com.nexarag.model.gateway;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.model.execution.ModelExecutionCommand;
import com.nexarag.model.execution.ModelExecutionTemplate;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.gateway.chat.ChatModelResponse;
import com.nexarag.model.gateway.embedding.EmbeddingModelRequest;
import com.nexarag.model.gateway.embedding.EmbeddingModelResponse;
import com.nexarag.model.gateway.rerank.RerankModelRequest;
import com.nexarag.model.gateway.rerank.RerankModelResponse;
import com.nexarag.model.provider.ModelProviderDispatcher;
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
        // 1. Chat 调用链路后续接入，初版先给出清晰异常
        throw new ServiceException("Chat 模型调用暂未支持", BaseErrorCode.SERVICE_ERROR);
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
}
