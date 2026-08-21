package com.nexarag.retrieval.chat.impl;

import com.nexarag.retrieval.dto.req.ConversationRetrievalRequest;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.retrieval.enums.RetrievalScope;
import com.nexarag.retrieval.retriever.keyword.Bm25ConversationRetriever;
import com.nexarag.retrieval.retriever.vector.MilvusConversationRetriever;
import com.nexarag.retrieval.service.impl.ConversationRetrievalServiceImpl;
import com.nexarag.document.service.KnowledgeBaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 对话检索服务测试，验证多通道编排的降级行为。
 */
@ExtendWith(MockitoExtension.class)
class ConversationRetrievalServiceImplTest {

    @Mock
    private MilvusConversationRetriever milvusRetriever;
    @Mock
    private Bm25ConversationRetriever bm25Retriever;
    @Mock
    private KnowledgeBaseService knowledgeBaseService;
    @Test
    void retrieveShouldKeepKeywordResultsWhenMilvusFails() {
        ConversationRetrievalRequest request = new ConversationRetrievalRequest(
                "退款规则", null, RetrievalScope.INTENT, 10, 0.5D, 1);
        RetrievalChunk keywordChunk = new RetrievalChunk("chunk-k1", 1L, 0, null,
                "退款规则", "知识库", "退款应在七日内申请", 12.0D, "BM25", 1);
        when(milvusRetriever.retrieve(request)).thenThrow(new IllegalStateException("Milvus不可用"));
        when(bm25Retriever.retrieve(request)).thenReturn(List.of(keywordChunk));
        when(knowledgeBaseService.validateRequestedKnowledgeBases(List.of())).thenReturn(Set.of());
        when(knowledgeBaseService.filterDocumentIdsInCurrentTenantScope(List.of(1L), Set.of()))
                .thenReturn(Set.of(1L));

        ConversationRetrievalServiceImpl retrievalService = new ConversationRetrievalServiceImpl(
                List.of(milvusRetriever, bm25Retriever), knowledgeBaseService);
        List<RetrievalChunk> result = retrievalService.retrieve(request);

        assertThat(result).containsExactly(keywordChunk);
    }
}
