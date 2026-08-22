package com.nexarag.workflow.chat;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.nexarag.chat.service.ConversationContextService;
import com.nexarag.chat.service.ConversationMessageService;
import com.nexarag.chat.service.ConversationSummaryService;
import com.nexarag.chat.domain.ChatCitationSetCodec;
import com.nexarag.chat.domain.ChatCitationSetDTO;
import com.nexarag.workflow.node.chat.AssistantMessagePersistenceNode;
import com.nexarag.workflow.stream.ChatGenerationEventPublisher;
import com.nexarag.workflow.stream.ChatGenerationTaskManager;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.ASSISTANT_CONTENT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.ASSISTANT_MESSAGE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CONVERSATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CITATION_SET;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.MODEL_STREAM_RESULT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.STREAM_STATUS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_ID;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Chat Workflow 关键链路集成测试，验证模型流完成后的消息和上下文最终化。
 */
class ChatWorkflowIntegrationTest {

    @Test
    void completedStreamShouldPersistMessageRefreshContextAndScheduleSummary() throws Exception {
        ConversationMessageService messageService = mock(ConversationMessageService.class);
        ConversationContextService contextService = mock(ConversationContextService.class);
        ConversationSummaryService summaryService = mock(ConversationSummaryService.class);
        ChatGenerationEventPublisher eventPublisher = mock(ChatGenerationEventPublisher.class);
        ChatGenerationTaskManager taskManager = mock(ChatGenerationTaskManager.class);
        ChatCitationSetCodec citationCodec = mock(ChatCitationSetCodec.class);
        when(citationCodec.encode(org.mockito.ArgumentMatchers.any())).thenReturn("{\"version\":1,\"citations\":[]}");
        AssistantMessagePersistenceNode node = new AssistantMessagePersistenceNode(
                messageService, contextService, summaryService, eventPublisher, taskManager, citationCodec);
        GraphResponse<?> streamResult = GraphResponse.done(Map.of(
                STREAM_STATUS, "COMPLETED", ASSISTANT_CONTENT, "完整回答"));
        OverAllState state = new OverAllState(Map.of(
                MODEL_STREAM_RESULT, streamResult,
                ASSISTANT_MESSAGE_ID, "m1",
                CONVERSATION_ID, "c1",
                USER_ID, "u1",
                CITATION_SET, new ChatCitationSetDTO(1, java.util.List.of())));

        node.apply(state);

        verify(messageService).completeAssistantMessage("m1", "完整回答", null,
                null, null, null, "{\"version\":1,\"citations\":[]}", null);
        verify(contextService).rebuild("c1", "u1");
        verify(summaryService).scheduleIfNecessary("c1", "u1");
    }
}
