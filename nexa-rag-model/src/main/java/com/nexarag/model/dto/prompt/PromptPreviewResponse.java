package com.nexarag.model.dto.prompt;

import lombok.Builder;

/**
 * Prompt 脱敏预览响应。
 *
 * @param content 使用示例变量渲染后的正文
 */
@Builder
public record PromptPreviewResponse(String content) {
}
