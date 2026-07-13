package com.nexarag.retrieval.service.impl;

import com.nexarag.retrieval.service.ConversationRetrievalService;
import com.nexarag.retrieval.dto.req.ConversationRetrievalRequest;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.retrieval.retriever.ConversationRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 对话检索服务实现，负责并行编排已启用的召回通道。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationRetrievalServiceImpl implements ConversationRetrievalService {

    private final List<ConversationRetriever> retrievers;

    @Override
    public List<RetrievalChunk> retrieve(ConversationRetrievalRequest request) {
        // 1. 并行执行所有已装配的检索通道
        List<CompletableFuture<List<RetrievalChunk>>> futures = retrievers.stream()
                .map(retriever -> CompletableFuture.supplyAsync(() -> retrieveSafely(retriever, request)))
                .toList();

        // 2. 合并各通道结果，单路失败已在通道边界降级为空列表
        List<RetrievalChunk> result = new ArrayList<>();
        for (CompletableFuture<List<RetrievalChunk>> future : futures) {
            result.addAll(future.join());
        }
        return result;
    }

    private List<RetrievalChunk> retrieveSafely(ConversationRetriever retriever,
                                                ConversationRetrievalRequest request) {
        try {
            return retriever.retrieve(request);
        } catch (RuntimeException exception) {
            log.warn("对话检索通道执行失败，retriever={}", retriever.getClass().getSimpleName(), exception);
            return List.of();
        }
    }
}
