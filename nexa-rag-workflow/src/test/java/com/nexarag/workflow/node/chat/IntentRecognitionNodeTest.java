package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.toolkits.prompt.PromptBuilder;
import com.nexarag.model.prompt.domain.PromptExecutionSnapshot;
import com.nexarag.model.toolkits.prompt.PromptRender;
import com.nexarag.model.prompt.domain.PromptVariableSchema;
import com.nexarag.retrieval.dto.res.IntentRecognitionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.INTENT_RESULT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.PROMPT_EXECUTION_SNAPSHOT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.REWRITTEN_QUESTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 意图识别节点测试，验证调用失败时使用低置信度空意图。
 */
class IntentRecognitionNodeTest {

    @Test
    void applyShouldUseEmptyIntentWhenResponseCannotBeParsed() {
        ModelGateway modelGateway = mock(ModelGateway.class);
        when(modelGateway.chat(any())).thenThrow(new IllegalStateException("模型不可用"));
        IntentRecognitionNode node = new IntentRecognitionNode(
                modelGateway, new ObjectMapper(), new PromptBuilder(new PromptRender()));

        Map<String, Object> result = node.apply(new OverAllState(Map.of(
                REWRITTEN_QUESTION, "退款规则", PROMPT_EXECUTION_SNAPSHOT, snapshot())));

        assertThat(result.get(INTENT_RESULT)).isEqualTo(new IntentRecognitionResult(List.of(), 0.0D));
    }

    private PromptExecutionSnapshot snapshot() {
        return PromptExecutionSnapshot.of(Map.of(PromptBuilder.INTENT_INSTRUCTION,
                new PromptExecutionSnapshot.PromptSnapshot(PromptBuilder.INTENT_INSTRUCTION, 1L, 2L, 3L, "{{question}}",
                        new PromptVariableSchema(List.of(), List.of()))));
    }
}
