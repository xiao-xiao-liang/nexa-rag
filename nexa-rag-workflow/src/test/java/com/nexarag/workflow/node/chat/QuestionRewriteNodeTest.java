package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.workflow.prompt.ChatWorkflowPromptBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CONVERSATION_CONTEXT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.REWRITTEN_QUESTION;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_QUESTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 问题改写节点测试，验证模型路由和失败回退行为。
 */
@ExtendWith(MockitoExtension.class)
class QuestionRewriteNodeTest {

    @Mock
    private ModelGateway modelGateway;

    @Test
    void applyShouldFallbackToOriginalQuestionWhenModelUnavailable() {
        ChatWorkflowPromptBuilder promptBuilder = new ChatWorkflowPromptBuilder();
        QuestionRewriteNode node = new QuestionRewriteNode(modelGateway, promptBuilder);
        when(modelGateway.chat(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("模型不可用"));

        Map<String, Object> result = node.apply(new OverAllState(Map.of(
                USER_QUESTION, "原问题",
                CONVERSATION_CONTEXT, "上一轮上下文")));

        assertThat(result).containsEntry(REWRITTEN_QUESTION, "原问题");
        ArgumentCaptor<com.nexarag.model.gateway.chat.ChatModelRequest> captor =
                ArgumentCaptor.forClass(com.nexarag.model.gateway.chat.ChatModelRequest.class);
        verify(modelGateway).chat(captor.capture());
        assertThat(captor.getValue().routeKey()).isEqualTo("chat-rewrite");
    }
}
