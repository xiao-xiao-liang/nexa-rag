package com.nexarag.retrieval.service.impl;

import com.nexarag.retrieval.service.ConversationRetrievalService;
import com.nexarag.retrieval.dto.req.ConversationRetrievalRequest;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.retrieval.retriever.ConversationRetriever;
import com.nexarag.document.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 对话检索服务实现，负责并行编排已启用的召回通道。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationRetrievalServiceImpl implements ConversationRetrievalService {

    private final List<ConversationRetriever> retrievers;
    private final KnowledgeBaseService knowledgeBaseService;

    @Override
    public List<RetrievalChunk> retrieve(ConversationRetrievalRequest request) {
        // 1. 异步工作流必须携带入口处捕获的可信租户，不能在工作线程读取认证上下文。
        String tenantId = requireTenantId(request.tenantId());
        Set<Long> knowledgeBaseIds = knowledgeBaseService.validateRequestedKnowledgeBases(tenantId,
                request.knowledgeBaseIds());
        ConversationRetrievalRequest scopedRequest = new ConversationRetrievalRequest(request.question(), request.intentResult(),
                request.scope(), request.topK(), request.vectorThreshold(), request.round(), tenantId,
                List.copyOf(knowledgeBaseIds));

        // 2. 并行执行所有已装配的检索通道
        List<CompletableFuture<List<RetrievalChunk>>> futures = retrievers.stream()
                .map(retriever -> CompletableFuture.supplyAsync(() -> retrieveSafely(retriever, scopedRequest)))
                .toList();

        // 3. 合并并按文档归属过滤三类召回结果，防止跨租户或跨选定知识库泄露
        List<RetrievalChunk> result = new ArrayList<>();
        for (CompletableFuture<List<RetrievalChunk>> future : futures) {
            result.addAll(future.join());
        }
        Set<Long> accessibleDocumentIds = knowledgeBaseService.filterDocumentIdsInTenantScope(tenantId,
                result.stream().map(RetrievalChunk::documentId).toList(), knowledgeBaseIds);
        return result.stream()
                .filter(chunk -> accessibleDocumentIds.contains(chunk.documentId()))
                .toList();
    }

    private String requireTenantId(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("对话检索缺少可信租户ID");
        }
        return tenantId;
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
