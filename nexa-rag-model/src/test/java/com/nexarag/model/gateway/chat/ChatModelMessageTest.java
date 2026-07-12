package com.nexarag.model.gateway.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证模型网关聊天消息传输对象的基本行为。
 */
class ChatModelMessageTest {

    @Test
    void shouldExposeRoleAndContent() {
        ChatModelMessage message = new ChatModelMessage("USER", "你好");

        assertThat(message.role()).isEqualTo("USER");
        assertThat(message.content()).isEqualTo("你好");
    }
}
