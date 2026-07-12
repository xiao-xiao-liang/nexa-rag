package com.nexarag.retrieval.chat;

import com.nexarag.retrieval.chat.model.ConversationRetrievalRequest;
import com.nexarag.retrieval.chat.model.RetrievalChunk;

import java.util.List;

/**
 * 对话多通道检索服务。
 */
public interface ConversationRetrievalService {

    /**
     * 按请求并行执行已启用的检索通道。
     *
     * @param request 对话检索请求
     * @return 原始召回片段
     */
    List<RetrievalChunk> retrieve(ConversationRetrievalRequest request);
}
