package com.nexarag.chat.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新会话请求。
 *
 * @param title 会话新标题
 */
public record UpdateConversationRequest(
        @NotBlank(message = "会话标题不能为空")
        @Size(max = 100, message = "会话标题长度不能超过 100 个字符")
        String title
) {
}
