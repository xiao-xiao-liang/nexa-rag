package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.nexarag.chat.domain.ChatCitationSetCodec;
import com.nexarag.chat.domain.ChatCitationSetDTO;
import com.nexarag.chat.service.ConversationMessageService;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.chat.ChatModelMessage;
import com.nexarag.model.toolkits.prompt.PromptBuilder;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.workflow.citation.CitationSetFactory;
import com.nexarag.workflow.stream.ChatGenerationAccumulator;
import com.nexarag.workflow.stream.ChatGenerationEventPublisher;
import com.nexarag.workflow.stream.ChatGenerationTaskManager;
import com.nexarag.workflow.stream.ChatStreamEvent;
import com.nexarag.workflow.stream.ChatStreamEventType;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.ACCEPTED_EVIDENCE_RESULTS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CITATION_SET;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CONVERSATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.ASSISTANT_MESSAGE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.GENERATION_ACCUMULATOR;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.GENERATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.REWRITTEN_QUESTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回答生成节点的引用事件测试。
 */
class AnswerGenerationNodeCitationTest {

    @Test
    void shouldPublishCitationsBeforeStartingModelStream() {
        ModelGateway modelGateway = mock(ModelGateway.class);
        PromptBuilder promptBuilder = mock(PromptBuilder.class);
        ConversationMessageService messageService = mock(ConversationMessageService.class);
        ChatGenerationEventPublisher eventPublisher = mock(ChatGenerationEventPublisher.class);
        when(promptBuilder.buildAnswerMessages(any(), any(), any(), any(), any())).thenReturn(List.of(
                new ChatModelMessage("SYSTEM", "规则")));
        when(eventPublisher.publish(any(ChatStreamEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(modelGateway.streamChat(any())).thenReturn(Flux.never());
        AnswerGenerationNode node = new AnswerGenerationNode(modelGateway, promptBuilder,
                mock(ChatGenerationTaskManager.class), eventPublisher, new CitationSetFactory(), messageService,
                new ChatCitationSetCodec());

        Map<String, Object> result = node.apply(new OverAllState(Map.of(
                CONVERSATION_ID, "c1",
                GENERATION_ID, "g1",
                ASSISTANT_MESSAGE_ID, "m1",
                REWRITTEN_QUESTION, "报销规则",
                GENERATION_ACCUMULATOR, new ChatGenerationAccumulator(),
                ACCEPTED_EVIDENCE_RESULTS, List.of(new RetrievalChunk("chunk-1", 10L, 2, null,
                        "费用制度", "file", "正文", 0.9D, "hybrid", 1)))));

        ChatCitationSetDTO citations = (ChatCitationSetDTO) result.get(CITATION_SET);
        assertThat(citations.citations()).extracting(citation -> citation.citationId()).containsExactly(1);
        assertThat(citations.citations()).extracting(citation -> citation.chunkId()).containsExactly("chunk-1");
        org.mockito.ArgumentCaptor<String> referencesCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(messageService).updateGeneratingAssistantReferences(eq("m1"), referencesCaptor.capture());
        assertThat(referencesCaptor.getValue()).contains("chunk-1");
        org.mockito.InOrder ordered = inOrder(messageService, eventPublisher, modelGateway);
        ordered.verify(messageService).updateGeneratingAssistantReferences(eq("m1"), any());
        ordered.verify(eventPublisher).publish(any(ChatStreamEvent.class));
        ordered.verify(modelGateway).streamChat(any());
    }
}
