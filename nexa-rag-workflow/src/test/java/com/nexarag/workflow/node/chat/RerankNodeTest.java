package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.rerank.RerankModelRequest;
import com.nexarag.model.gateway.rerank.RerankModelResponse;
import com.nexarag.retrieval.chat.model.RetrievalChunk;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.FUSED_RETRIEVAL_RESULTS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RERANKED_RETRIEVAL_RESULTS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.REWRITTEN_QUESTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 重排序节点测试，验证空候选短路行为。
 */
class RerankNodeTest {

    @Test
    void applyShouldReturnEmptyResultWithoutCallingModelForEmptyCandidates() {
        ModelGateway modelGateway = mock(ModelGateway.class);
        RerankNode node = new RerankNode(modelGateway);

        Map<String, Object> result = node.apply(new OverAllState(Map.of(
                REWRITTEN_QUESTION, "退款规则",
                FUSED_RETRIEVAL_RESULTS, List.of())));

        assertThat(result.get(RERANKED_RETRIEVAL_RESULTS)).isEqualTo(List.of());
        verifyNoInteractions(modelGateway);
    }

    @Test
    void applyShouldUseRerankRouteAndKeepFinalTopFive() {
        ModelGateway modelGateway = mock(ModelGateway.class);
        List<RetrievalChunk> chunks = java.util.stream.IntStream.rangeClosed(1, 6)
                .mapToObj(index -> new RetrievalChunk("c" + index, 1L, index, null,
                        "标题", "知识库", "内容" + index, index, "BM25", index))
                .toList();
        List<RerankModelResponse.RerankScore> scores = java.util.stream.IntStream.rangeClosed(1, 6)
                .mapToObj(index -> new RerankModelResponse.RerankScore("c" + index, index))
                .toList();
        when(modelGateway.rerank(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new RerankModelResponse(scores, "rerank-profile", 10));
        RerankNode node = new RerankNode(modelGateway);

        Map<String, Object> result = node.apply(new OverAllState(Map.of(
                REWRITTEN_QUESTION, "退款规则",
                FUSED_RETRIEVAL_RESULTS, chunks)));

        assertThat((List<?>) result.get(RERANKED_RETRIEVAL_RESULTS)).hasSize(5);
        ArgumentCaptor<RerankModelRequest> captor = ArgumentCaptor.forClass(RerankModelRequest.class);
        verify(modelGateway).rerank(captor.capture());
        assertThat(captor.getValue().routeKey()).isEqualTo("rerank");
    }
}
