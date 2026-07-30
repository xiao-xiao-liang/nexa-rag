package com.nexarag.chat.domain;

import jakarta.validation.constraints.Size;

/**
 * 创建会话请求。
 *
 * @param title 会话标题（可选）
 */
public record CreateConversationRequest(
        @Size(max = 100, message = "会话标题长度不能超过 100 个字符")
        String title
) {
}
