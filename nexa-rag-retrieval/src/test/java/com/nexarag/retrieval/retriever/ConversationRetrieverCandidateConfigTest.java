package com.nexarag.retrieval.retriever;

import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.dto.req.ConversationRetrievalRequest;
import com.nexarag.retrieval.dto.req.KeywordIndexSearchRequest;
import com.nexarag.retrieval.enums.RetrievalScope;
import com.nexarag.retrieval.index.keyword.KeywordIndexClient;
import com.nexarag.retrieval.index.vector.DocumentVectorStore;
import com.nexarag.retrieval.model.KeywordIndexSearchResult;
import com.nexarag.retrieval.model.VectorIndexSearchResult;
import com.nexarag.retrieval.retriever.keyword.Bm25ConversationRetriever;
import com.nexarag.retrieval.retriever.vector.MilvusConversationRetriever;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对话检索器候选配置测试。
 */
class ConversationRetrieverCandidateConfigTest {

    @Test
    void vectorRetrieverShouldUseConfiguredCandidateLimitAndKeepScoreAboveConfiguredFloor() {
        DocumentVectorStore documentVectorStore = mock(DocumentVectorStore.class);
        RetrievalProperties properties = candidateProperties(7, 4, 0D);
        when(documentVectorStore.search(any(), any(Integer.class))).thenReturn(List.of(
                new VectorIndexSearchResult("candidate", 1L, null, 1, "内容", "{}", 0.48699233D)));

        List<?> result = new MilvusConversationRetriever(documentVectorStore, properties)
                .retrieve(request());

        assertThat(result).hasSize(1);
        verify(documentVectorStore).search("退款规则", 7);
    }

    @Test
    void keywordRetrieverShouldUseConfiguredCandidateLimitAndCoarseScoreFloor() {
        KeywordIndexClient keywordIndexClient = mock(KeywordIndexClient.class);
        RetrievalProperties properties = candidateProperties(7, 4, 0.2D);
        when(keywordIndexClient.search(any())).thenReturn(List.of(
                new KeywordIndexSearchResult("discarded", 1L, null, 1, "内容", "{}", 0.19D),
                new KeywordIndexSearchResult("kept", 1L, null, 2, "内容", "{}", 0.2D)));

        List<?> result = new Bm25ConversationRetriever(keywordIndexClient, properties).retrieve(request());

        assertThat(result).hasSize(1);
        ArgumentCaptor<KeywordIndexSearchRequest> captor = ArgumentCaptor.forClass(KeywordIndexSearchRequest.class);
        verify(keywordIndexClient).search(captor.capture());
        assertThat(captor.getValue().topK()).isEqualTo(4);
    }

    private ConversationRetrievalRequest request() {
        return new ConversationRetrievalRequest("退款规则", null, RetrievalScope.INTENT, 2, 0.9D, 1);
    }

    private RetrievalProperties candidateProperties(int vectorCandidateLimit, int keywordCandidateLimit,
                                                    double coarseScoreFloor) {
        RetrievalProperties properties = new RetrievalProperties();
        properties.getCandidate().setVectorCandidateLimit(vectorCandidateLimit);
        properties.getCandidate().setKeywordCandidateLimit(keywordCandidateLimit);
        properties.getCandidate().setCoarseScoreFloor(coarseScoreFloor);
        return properties;
    }
}
