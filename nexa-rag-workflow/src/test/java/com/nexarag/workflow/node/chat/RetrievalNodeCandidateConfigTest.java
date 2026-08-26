package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import cn.dev33.satoken.exception.SaTokenContextException;
import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.dto.req.ConversationRetrievalRequest;
import com.nexarag.retrieval.service.ConversationRetrievalService;
import com.nexarag.workflow.stream.ChatGenerationEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.REWRITTEN_QUESTION;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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

        new RetrievalNode(retrievalService, properties, mock(ChatGenerationEventPublisher.class)).apply(new OverAllState(Map.of(
                REWRITTEN_QUESTION, "退款规则", TENANT_ID, "tenant-001")));

        ArgumentCaptor<ConversationRetrievalRequest> captor = ArgumentCaptor.forClass(ConversationRetrievalRequest.class);
        verify(retrievalService).retrieve(captor.capture());
        assertThat(captor.getValue().topK()).isEqualTo(7);
        assertThat(captor.getValue().vectorThreshold()).isZero();
        assertThat(captor.getValue().tenantId()).isEqualTo("tenant-001");
    }

    /**
     * 异步线程缺失 Sa-Token 上下文表示程序错误，不能重试后降级为空检索。
     */
    @Test
    void applyShouldPropagateSaTokenContextFailureWithoutRetry() {
        ConversationRetrievalService retrievalService = mock(ConversationRetrievalService.class);
        when(retrievalService.retrieve(any())).thenThrow(new SaTokenContextException("上下文未初始化"));

        RetrievalNode node = new RetrievalNode(retrievalService, new RetrievalProperties(),
                mock(ChatGenerationEventPublisher.class));

        assertThatThrownBy(() -> node.apply(new OverAllState(Map.of(REWRITTEN_QUESTION, "退款规则",
                TENANT_ID, "tenant-001"))))
                .isInstanceOf(SaTokenContextException.class);
        verify(retrievalService, times(1)).retrieve(any());
    }
}
