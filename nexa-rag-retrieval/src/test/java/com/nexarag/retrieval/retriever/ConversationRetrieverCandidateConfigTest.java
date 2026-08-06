package com.nexarag.retrieval.retriever;

import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.embedding.EmbeddingModelResponse;
import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.dto.req.ConversationRetrievalRequest;
import com.nexarag.retrieval.dto.req.KeywordIndexSearchRequest;
import com.nexarag.retrieval.dto.req.VectorIndexSearchRequest;
import com.nexarag.retrieval.enums.RetrievalScope;
import com.nexarag.retrieval.index.keyword.KeywordIndexClient;
import com.nexarag.retrieval.index.vector.VectorIndexClient;
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
        ModelGateway modelGateway = mock(ModelGateway.class);
        VectorIndexClient vectorIndexClient = mock(VectorIndexClient.class);
        RetrievalProperties properties = candidateProperties(7, 4, 0D);
        when(modelGateway.embedding(any())).thenReturn(new EmbeddingModelResponse(List.of(new float[]{0.1F}), "embedding", 1));
        when(vectorIndexClient.search(any())).thenReturn(List.of(
                new VectorIndexSearchResult("candidate", 1L, null, 1, "内容", "{}", 0.48699233D)));

        List<?> result = new MilvusConversationRetriever(modelGateway, vectorIndexClient, properties)
                .retrieve(request());

        assertThat(result).hasSize(1);
        ArgumentCaptor<VectorIndexSearchRequest> captor = ArgumentCaptor.forClass(VectorIndexSearchRequest.class);
        verify(vectorIndexClient).search(captor.capture());
        assertThat(captor.getValue().topK()).isEqualTo(7);
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
