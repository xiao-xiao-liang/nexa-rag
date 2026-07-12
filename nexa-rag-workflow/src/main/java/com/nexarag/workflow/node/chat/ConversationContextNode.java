package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.nexarag.chat.domain.ChatMessageVO;
import com.nexarag.chat.domain.ConversationContext;
import com.nexarag.chat.service.ConversationContextService;
import com.nexarag.chat.service.ConversationMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CONVERSATION_CONTEXT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CONVERSATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_MESSAGE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_QUESTION;

/**
 * 会话上下文节点，负责加载上下文并保存本轮用户消息。
 */
@Component
@ConditionalOnBean(SqlSessionFactory.class)
@RequiredArgsConstructor
public class ConversationContextNode implements NodeAction {

    private final ConversationContextService contextService;
    private final ConversationMessageService messageService;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String conversationId = state.value(CONVERSATION_ID, "");
        String userId = state.value(USER_ID, "");
        // 1. 加载用户消息写入前的活跃上下文
        ConversationContext context = contextService.loadForTurn(conversationId, userId);
        // 2. 保存本轮用户消息
        ChatMessageVO message = messageService.appendUserMessage(
                conversationId, userId, state.value(USER_QUESTION, ""));
        return Map.of(CONVERSATION_CONTEXT, context, USER_MESSAGE_ID, message.messageId());
    }
}
