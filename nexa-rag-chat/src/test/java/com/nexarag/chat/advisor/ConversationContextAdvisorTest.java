package com.nexarag.chat.advisor;

import com.nexarag.auth.context.CurrentUser;
import com.nexarag.auth.context.CurrentUserContext;
import com.nexarag.chat.constants.ChatContextConstants;
import com.nexarag.chat.domain.ConversationContext;
import com.nexarag.chat.domain.ChatMessageVO;
import com.nexarag.chat.enums.ChatMessageRole;
import com.nexarag.chat.enums.ChatMessageStatus;
import com.nexarag.chat.service.ConversationContextService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话上下文 Advisor 的行为测试。
 */
@ExtendWith(MockitoExtension.class)
class ConversationContextAdvisorTest {

    @Mock
    private ConversationContextService contextService;

    @AfterEach
    void clearUserContext() {
        CurrentUserContext.clear();
    }

    @Test
    void shouldPrependSummaryAndRecentMessages() {
        CurrentUserContext.set(new CurrentUser("864019719617777664"));
        ChatMessageVO message = new ChatMessageVO("m1", "c1", "864019719617777664", 1,
                ChatMessageRole.USER, ChatMessageStatus.COMPLETED, "历史问题", null,
                null, null, null, null, null, null, null, null, null, null);
        when(contextService.loadForTurn("c1", "864019719617777664"))
                .thenReturn(new ConversationContext("c1", "864019719617777664", "摘要", null, null,
                        List.of(message), "m1", 1L));

        ConversationContextAdvisor advisor = new ConversationContextAdvisor(contextService);
        ChatClientRequest request = new ChatClientRequest(new Prompt("本轮问题"),
                Map.of(ChatContextConstants.CONVERSATION_ID_CONTEXT_KEY, "c1"));
        ChatClientRequest result = advisor.before(request, mock(AdvisorChain.class));

        List<Message> instructions = result.prompt().getInstructions();
        assertThat(instructions).hasSize(3);
        assertThat(instructions.get(0).getText()).contains("摘要");
        assertThat(instructions.get(1).getText()).isEqualTo("历史问题");
        assertThat(instructions.get(2).getText()).isEqualTo("本轮问题");
        verify(contextService).loadForTurn("c1", "864019719617777664");
    }
}
