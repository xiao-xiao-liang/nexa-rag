package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.nexarag.chat.domain.ChatConversationVO;
import com.nexarag.chat.service.ConversationService;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.toolkits.prompt.PromptBuilder;
import com.nexarag.model.prompt.domain.PromptExecutionSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CONVERSATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.IS_NEW_CONVERSATION;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_QUESTION;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TRACE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.PROMPT_EXECUTION_SNAPSHOT;
import static com.nexarag.chat.constants.ChatModelRouteConstants.CHAT_TITLE_ROUTE_KEY;

/**
 * 会话有效性节点，负责校验已有会话或创建新会话。
 */
@Component
@ConditionalOnProperty(prefix = "nexa.chat", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ConversationValidationNode implements NodeAction {

    private final ConversationService conversationService;
    private final ModelGateway modelGateway;
    private final PromptBuilder promptBuilder;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String userId = state.value(USER_ID, "");
        String conversationId = state.value(CONVERSATION_ID, "");
        if (!conversationId.isBlank()) {
            // 1. 校验已有会话归属
            conversationService.getOwned(conversationId, userId);
            return Map.of(IS_NEW_CONVERSATION, false);
        }

        // 2. 使用当前问题生成临时标题并创建会话
        String question = state.value(USER_QUESTION, "新会话");
        String title = question.length() > 20 ? question.substring(0, 20) : question;
        ChatConversationVO conversation = conversationService.create(userId, title);
        String traceId = state.value(TRACE_ID, "");
        PromptExecutionSnapshot snapshot = state.value(PROMPT_EXECUTION_SNAPSHOT, (PromptExecutionSnapshot) null);
        Thread.startVirtualThread(() -> generateTitle(conversation.getConversationId(), userId, question, traceId, snapshot));
        return Map.of(CONVERSATION_ID, conversation.getConversationId(), IS_NEW_CONVERSATION, true);
    }

    private void generateTitle(String conversationId, String userId, String question, String traceId,
                               PromptExecutionSnapshot snapshot) {
        try {
            // 1. 调用轻量模型生成正式标题
            var response = modelGateway.chat(ChatModelRequest.builder()
                    .traceId(traceId)
                    .bizType(ModelBizType.CHAT)
                    .bizId(conversationId)
                    .routeKey(CHAT_TITLE_ROUTE_KEY)
                    .messages(promptBuilder.buildTitleMessages(snapshot, Map.of("question", question == null ? "" : question)))
                    .build());
            if (response != null && response.content() != null && !response.content().isBlank()) {
                // 2. 标题生成成功后更新会话
                conversationService.rename(conversationId, userId, response.content().trim());
            }
        } catch (RuntimeException exception) {
            log.warn("异步生成会话标题失败，保留临时标题，conversationId={}", conversationId, exception);
        }
    }
}
