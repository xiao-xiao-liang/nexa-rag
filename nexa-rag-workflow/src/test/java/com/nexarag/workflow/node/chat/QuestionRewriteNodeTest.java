package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.toolkits.prompt.PromptBuilder;
import com.nexarag.model.prompt.domain.PromptExecutionSnapshot;
import com.nexarag.model.toolkits.prompt.PromptRender;
import com.nexarag.model.prompt.domain.PromptVariableSchema;
import com.nexarag.workflow.stream.ChatGenerationAccumulator;
import com.nexarag.workflow.stream.ChatGenerationEventPublisher;
import com.nexarag.workflow.stream.ChatStreamEvent;
import com.nexarag.workflow.stream.ChatToolOperationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CONVERSATION_CONTEXT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CONVERSATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.GENERATION_ACCUMULATOR;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.GENERATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.ASSISTANT_MESSAGE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.PROMPT_EXECUTION_SNAPSHOT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.REWRITTEN_QUESTION;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TRACE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_QUESTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

/**
 * 问题改写节点测试，验证模型不可用时回退到原问题。
 */
@ExtendWith(MockitoExtension.class)
class QuestionRewriteNodeTest {

    @Mock
    private ModelGateway modelGateway;

    @Mock
    private ChatGenerationEventPublisher eventPublisher;

    @Test
    void applyShouldFallbackToOriginalQuestionWhenModelUnavailable() {
        QuestionRewriteNode node = new QuestionRewriteNode(modelGateway, new PromptBuilder(new PromptRender()), eventPublisher);
        when(modelGateway.chat(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("模型不可用"));

        Map<String, Object> result = node.apply(new OverAllState(Map.of(
                USER_QUESTION, "原问题",
                TRACE_ID, "trace-001",
                CONVERSATION_ID, "c1",
                GENERATION_ID, "g1",
                ASSISTANT_MESSAGE_ID, "m1",
                GENERATION_ACCUMULATOR, new ChatGenerationAccumulator(),
                CONVERSATION_CONTEXT, new com.nexarag.chat.domain.ConversationContext("c1", "u1", "", "", null,
                        List.of(), "", 1L),
                PROMPT_EXECUTION_SNAPSHOT, snapshot())));

        assertThat(result).containsEntry(REWRITTEN_QUESTION, "原问题");
        ArgumentCaptor<com.nexarag.model.gateway.chat.ChatModelRequest> captor =
                ArgumentCaptor.forClass(com.nexarag.model.gateway.chat.ChatModelRequest.class);
        verify(modelGateway).chat(captor.capture());
        assertThat(captor.getValue().routeKey()).isEqualTo("chat");
        assertThat(captor.getValue().traceId()).isEqualTo("trace-001");

        ArgumentCaptor<ChatStreamEvent> eventCaptor = ArgumentCaptor.forClass(ChatStreamEvent.class);
        verify(eventPublisher, times(2)).publish(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .allSatisfy(event -> assertThat(event.operations()).hasSize(1));
        assertThat(eventCaptor.getAllValues())
                .extracting(event -> event.operations().getFirst().name())
                .containsOnly("system:question_rewrite");
        assertThat(eventCaptor.getAllValues())
                .extracting(event -> event.operations().getFirst().status())
                .containsExactly(ChatToolOperationStatus.RUNNING, ChatToolOperationStatus.SUCCESS);
    }

    private PromptExecutionSnapshot snapshot() {
        return PromptExecutionSnapshot.of(Map.of(PromptBuilder.REWRITE_INSTRUCTION,
                new PromptExecutionSnapshot.PromptSnapshot(PromptBuilder.REWRITE_INSTRUCTION, 1L, 2L, 3L, "{{question}}",
                        new PromptVariableSchema(List.of(), List.of()))));
    }
}
