package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.retrieval.chat.model.IntentRecognitionResult;
import com.nexarag.workflow.prompt.ChatWorkflowPromptBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.INTENT_RESULT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.REWRITTEN_QUESTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 意图识别节点测试，验证解析失败时使用低置信度结果降级。
 */
class IntentRecognitionNodeTest {

    @Test
    void applyShouldUseEmptyIntentWhenResponseCannotBeParsed() {
        ModelGateway modelGateway = mock(ModelGateway.class);
        when(modelGateway.chat(any())).thenThrow(new IllegalStateException("模型不可用"));
        IntentRecognitionNode node = new IntentRecognitionNode(
                modelGateway, new ObjectMapper(), new ChatWorkflowPromptBuilder());

        Map<String, Object> result = node.apply(new OverAllState(Map.of(REWRITTEN_QUESTION, "退款规则")));

        assertThat(result.get(INTENT_RESULT)).isEqualTo(new IntentRecognitionResult(java.util.List.of(), 0.0D));
    }
}
