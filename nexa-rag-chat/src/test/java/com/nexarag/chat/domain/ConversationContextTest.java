package com.nexarag.chat.domain;

import com.nexarag.chat.enums.ChatMessageRole;
import com.nexarag.chat.enums.ChatMessageStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证会话上下文的消息顺序和集合隔离行为。
 */
class ConversationContextTest {

    @Test
    void shouldKeepRecentMessagesInSequenceOrderAndProtectInputList() {
        ChatMessageVO message = new ChatMessageVO(
                "m1", "c1", "u1", 1L, ChatMessageRole.USER, ChatMessageStatus.COMPLETED,
                "你好", null, null, null, null, null, null, null, null, null
        );
        List<ChatMessageVO> source = new java.util.ArrayList<>(List.of(message));

        ConversationContext context = new ConversationContext(
                "c1", "u1", "摘要", "m0", source, "m1", 1L
        );
        source.clear();

        assertThat(context.recentMessages()).containsExactly(message);
        assertThat(context.recentMessages().getFirst().role()).isEqualTo(ChatMessageRole.USER);
    }
}
