package com.nexarag.chat.constants;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chat 模型路由键常量测试，确保工作流与数据库默认路由保持一致。
 */
class ChatModelRouteConstantsTest {

    @Test
    void shouldMapChatWorkflowCapabilitiesToDefaultDatabaseRoutes() {
        assertThat(ChatModelRouteConstants.CHAT_ANSWER_ROUTE_KEY).isEqualTo("answer");
        assertThat(ChatModelRouteConstants.CHAT_REWRITE_ROUTE_KEY).isEqualTo("chat");
        assertThat(ChatModelRouteConstants.CHAT_INTENT_ROUTE_KEY).isEqualTo("chat");
        assertThat(ChatModelRouteConstants.CHAT_SUMMARY_ROUTE_KEY).isEqualTo("chat");
        assertThat(ChatModelRouteConstants.CHAT_TITLE_ROUTE_KEY).isEqualTo("chat");
    }
}
