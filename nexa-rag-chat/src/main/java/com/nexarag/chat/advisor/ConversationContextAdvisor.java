package com.nexarag.chat.advisor;

import com.nexarag.auth.context.UserContext;
import com.nexarag.chat.constants.ChatContextConstants;
import com.nexarag.chat.domain.ChatMessageVO;
import com.nexarag.chat.domain.ConversationContext;
import com.nexarag.chat.service.ConversationContextService;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * 在模型调用前注入会话上下文的只读 Advisor。
 */
@Component
@ConditionalOnBean(SqlSessionFactory.class)
@RequiredArgsConstructor
public class ConversationContextAdvisor implements BaseAdvisor {

    private final ConversationContextService contextService;

    @NotNull
    @Override
    public ChatClientRequest before(ChatClientRequest request, @NotNull AdvisorChain chain) {
        // 1. 从请求上下文读取会话 ID，并从当前用户上下文读取用户 ID
        String conversationId = conversationId(request.context());
        if (conversationId == null || conversationId.isBlank()) {
            return request;
        }
        String userId = UserContext.getCurrUser().userId();

        // 2. 从 Redis 优先加载会话上下文，缓存未命中时由服务负责重建
        ConversationContext context = contextService.loadForTurn(conversationId, userId);

        // 3. 将摘要和最近消息追加到原始 Prompt 前面，保留本轮用户消息在末尾
        List<Message> messages = new ArrayList<>();
        if (context.summary() != null && !context.summary().isBlank()) {
            messages.add(new SystemMessage("以下是该会话的历史摘要：\n" + context.summary()));
        }
        context.recentMessages().stream()
                .map(this::toSpringMessage)
                .forEach(messages::add);
        messages.addAll(request.prompt().getInstructions());
        return new ChatClientRequest(new Prompt(messages, request.prompt().getOptions()), request.context());
    }

    @NotNull
    @Override
    public ChatClientResponse after(@NotNull ChatClientResponse response, @NotNull AdvisorChain chain) {
        return response;
    }

    @NotNull
    @Override
    public String getName() {
        return "conversation-context-advisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private String conversationId(Map<String, Object> context) {
        Object value = context == null ? null : context.get(ChatContextConstants.CONVERSATION_ID_CONTEXT_KEY);
        return value == null ? null : value.toString();
    }

    private Message toSpringMessage(ChatMessageVO message) {
        return switch (message.role()) {
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
        };
    }
}
