package com.nexarag.retrieval.retriever;

import com.nexarag.retrieval.chat.model.ConversationRetrievalRequest;
import com.nexarag.retrieval.chat.model.RetrievalChunk;

import java.util.List;

/**
 * 对话检索通道。
 */
public interface ConversationRetriever {

    /**
     * 执行单个检索通道的召回。
     *
     * @param request 对话检索请求
     * @return 通道召回片段
     */
    List<RetrievalChunk> retrieve(ConversationRetrievalRequest request);
}
