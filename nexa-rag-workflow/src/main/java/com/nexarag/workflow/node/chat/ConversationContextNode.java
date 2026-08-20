package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.nexarag.chat.domain.ChatGenerationTurnBO;
import com.nexarag.chat.domain.ConversationContext;
import com.nexarag.chat.service.ConversationContextService;
import com.nexarag.chat.service.ConversationMessageService;
import com.nexarag.workflow.stream.ChatGenerationAccumulator;
import com.nexarag.workflow.stream.ChatGenerationEventPublisher;
import com.nexarag.workflow.stream.ChatGenerationTaskManager;
import com.nexarag.workflow.stream.ChatStreamEvent;
import com.nexarag.workflow.stream.ChatStreamEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CONVERSATION_CONTEXT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CONVERSATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.GENERATION_ACCUMULATOR;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.GENERATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.ASSISTANT_MESSAGE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_MESSAGE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_QUESTION;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TRACE_ID;

/**
 * 会话上下文节点，负责加载上下文并保存本轮用户消息。
 */
@Component
@ConditionalOnProperty(prefix = "nexa.chat", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ConversationContextNode implements NodeAction {

    private final ConversationContextService contextService;
    private final ConversationMessageService messageService;
    private final ChatGenerationTaskManager taskManager;
    private final ChatGenerationEventPublisher eventPublisher;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String conversationId = state.value(CONVERSATION_ID, "");
        String userId = state.value(USER_ID, "");
        // 1. 加载用户消息写入前的活跃上下文
        ConversationContext context = contextService.loadForTurn(conversationId, userId);
        // 2. 原子保存本轮用户消息和助手占位消息，并在工具阶段前注册取消任务
        String generationId = state.value(GENERATION_ID, "");
        ChatGenerationTurnBO turn = messageService.beginGenerationTurn(
                conversationId, userId, state.value(USER_QUESTION, ""), generationId);
        ChatGenerationAccumulator accumulator = new ChatGenerationAccumulator();
        taskManager.register(generationId, userId, conversationId, accumulator, () -> {
            String toolOperationsJson = eventPublisher.serializeOperations(accumulator.operationsSnapshot());
            messageService.cancelAssistantMessage(turn.assistantMessage().messageId(), accumulator.snapshot().content(),
                    toolOperationsJson);
            eventPublisher.publish(new ChatStreamEvent(ChatStreamEventType.CANCELLED, null, conversationId,
                    state.value(TRACE_ID, ""), generationId, turn.assistantMessage().messageId(), null, null,
                    0L, accumulator.operationsSnapshot()));
            eventPublisher.complete(generationId);
        });

        // 3. 发布客户端恢复所需的初始元数据
        eventPublisher.publish(new ChatStreamEvent(ChatStreamEventType.META, null, conversationId,
                state.value(TRACE_ID, ""), generationId, turn.assistantMessage().messageId(), null, null));
        log.debug("会话上下文加载完成，conversationId={}，context={}", conversationId, context);
        return Map.of(CONVERSATION_CONTEXT, context, USER_MESSAGE_ID, turn.userMessage().messageId(),
                ASSISTANT_MESSAGE_ID, turn.assistantMessage().messageId(), GENERATION_ACCUMULATOR, accumulator);
    }
}
