package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.nexarag.chat.service.ConversationContextService;
import com.nexarag.chat.service.ConversationMessageService;
import com.nexarag.chat.service.ConversationSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.ASSISTANT_CONTENT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.ASSISTANT_MESSAGE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.COMPLETION_TOKENS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CONVERSATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.ERROR_CODE;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.ERROR_MESSAGE;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.MODEL_STREAM_RESULT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.PROMPT_TOKENS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.STREAM_STATUS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TOTAL_TOKENS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_ID;

/**
 * 助手消息最终化节点，负责持久化生成结果并刷新成功上下文。
 */
@Component
@ConditionalOnBean(SqlSessionFactory.class)
@RequiredArgsConstructor
public class AssistantMessagePersistenceNode implements NodeAction {
    private final ConversationMessageService messageService;
    private final ConversationContextService contextService;
    private final ConversationSummaryService summaryService;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        Map<String, Object> result = finalState(state.value(MODEL_STREAM_RESULT).orElse(null));
        String messageId = state.value(ASSISTANT_MESSAGE_ID, "");
        String content = (String) result.getOrDefault(ASSISTANT_CONTENT, "");
        String status = (String) result.getOrDefault(STREAM_STATUS, "FAILED");
        if ("COMPLETED".equals(status)) {
            // 1. 完成消息并刷新活跃上下文
            messageService.completeAssistantMessage(messageId, content, null,
                    (Integer) result.get(PROMPT_TOKENS), (Integer) result.get(COMPLETION_TOKENS),
                    (Integer) result.get(TOTAL_TOKENS), null);
            String conversationId = state.value(CONVERSATION_ID, "");
            String userId = state.value(USER_ID, "");
            contextService.rebuild(conversationId, userId);
            summaryService.scheduleIfNecessary(conversationId, userId);
        } else if ("CANCELLED".equals(status)) {
            messageService.cancelAssistantMessage(messageId, content);
        } else {
            messageService.failAssistantMessage(messageId, content,
                    (String) result.get(ERROR_CODE), (String) result.get(ERROR_MESSAGE));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> finalState(Object streamResult) {
        if (streamResult instanceof GraphResponse<?> response
                && response.resultValue().orElse(null) instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of(STREAM_STATUS, "FAILED", ERROR_CODE, "STREAM_STATE_INVALID",
                ERROR_MESSAGE, "模型流最终状态无效", ASSISTANT_CONTENT, "");
    }
}
