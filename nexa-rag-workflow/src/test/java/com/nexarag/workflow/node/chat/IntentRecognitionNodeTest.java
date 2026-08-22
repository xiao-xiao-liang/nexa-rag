package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.toolkits.prompt.PromptBuilder;
import com.nexarag.model.prompt.domain.PromptExecutionSnapshot;
import com.nexarag.model.toolkits.prompt.PromptRender;
import com.nexarag.model.prompt.domain.PromptVariableSchema;
import com.nexarag.retrieval.dto.res.IntentRecognitionResult;
import com.nexarag.workflow.stream.ChatGenerationAccumulator;
import com.nexarag.workflow.stream.ChatGenerationEventPublisher;
import com.nexarag.workflow.stream.ChatStreamEvent;
import com.nexarag.workflow.stream.ChatToolOperationStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.INTENT_RESULT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.ASSISTANT_MESSAGE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CONVERSATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.GENERATION_ACCUMULATOR;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.GENERATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.PROMPT_EXECUTION_SNAPSHOT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.REWRITTEN_QUESTION;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TRACE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

/**
 * 意图识别节点测试，验证调用失败时使用低置信度空意图。
 */
class IntentRecognitionNodeTest {

    @Test
    void applyShouldUseEmptyIntentWhenResponseCannotBeParsed() {
        ModelGateway modelGateway = mock(ModelGateway.class);
        ChatGenerationEventPublisher eventPublisher = mock(ChatGenerationEventPublisher.class);
        when(modelGateway.chat(any())).thenThrow(new IllegalStateException("模型不可用"));
        IntentRecognitionNode node = new IntentRecognitionNode(
                modelGateway, new ObjectMapper(), new PromptBuilder(new PromptRender()), eventPublisher);

        Map<String, Object> result = node.apply(new OverAllState(Map.of(
                REWRITTEN_QUESTION, "退款规则", TRACE_ID, "trace-001",
                CONVERSATION_ID, "c1", GENERATION_ID, "g1", ASSISTANT_MESSAGE_ID, "m1",
                GENERATION_ACCUMULATOR, new ChatGenerationAccumulator(),
                PROMPT_EXECUTION_SNAPSHOT, snapshot())));

        assertThat(result.get(INTENT_RESULT)).isEqualTo(new IntentRecognitionResult(List.of(), 0.0D));
        ArgumentCaptor<com.nexarag.model.gateway.chat.ChatModelRequest> captor =
                ArgumentCaptor.forClass(com.nexarag.model.gateway.chat.ChatModelRequest.class);
        verify(modelGateway).chat(captor.capture());
        assertThat(captor.getValue().traceId()).isEqualTo("trace-001");

        ArgumentCaptor<ChatStreamEvent> eventCaptor = ArgumentCaptor.forClass(ChatStreamEvent.class);
        verify(eventPublisher, times(2)).publish(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .allSatisfy(event -> assertThat(event.operations()).hasSize(1));
        assertThat(eventCaptor.getAllValues())
                .extracting(event -> event.operations().getFirst().name())
                .containsOnly("system:intent_recognition");
        assertThat(eventCaptor.getAllValues())
                .extracting(event -> event.operations().getFirst().status())
                .containsExactly(ChatToolOperationStatus.RUNNING, ChatToolOperationStatus.SUCCESS);
    }

    private PromptExecutionSnapshot snapshot() {
        return PromptExecutionSnapshot.of(Map.of(PromptBuilder.INTENT_INSTRUCTION,
                new PromptExecutionSnapshot.PromptSnapshot(PromptBuilder.INTENT_INSTRUCTION, 1L, 2L, 3L, "{{question}}",
                        new PromptVariableSchema(List.of(), List.of()))));
    }
}
