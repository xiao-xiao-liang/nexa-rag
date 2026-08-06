package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.dto.req.ConversationRetrievalRequest;
import com.nexarag.retrieval.service.ConversationRetrievalService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.REWRITTEN_QUESTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对话检索节点候选配置测试。
 */
class RetrievalNodeCandidateConfigTest {

    @Test
    void applyShouldUseCandidateConfigWhenStateDoesNotContainRetrievalParameters() {
        ConversationRetrievalService retrievalService = mock(ConversationRetrievalService.class);
        RetrievalProperties properties = new RetrievalProperties();
        properties.getCandidate().setVectorCandidateLimit(7);
        properties.getCandidate().setCoarseScoreFloor(0D);
        when(retrievalService.retrieve(any())).thenReturn(java.util.List.of());

        new RetrievalNode(retrievalService, properties).apply(new OverAllState(Map.of(
                REWRITTEN_QUESTION, "退款规则")));

        ArgumentCaptor<ConversationRetrievalRequest> captor = ArgumentCaptor.forClass(ConversationRetrievalRequest.class);
        verify(retrievalService).retrieve(captor.capture());
        assertThat(captor.getValue().topK()).isEqualTo(7);
        assertThat(captor.getValue().vectorThreshold()).isZero();
    }
}
