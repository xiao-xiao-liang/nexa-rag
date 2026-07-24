package com.nexarag.model.dto.prompt;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * Prompt 脱敏预览请求。
 *
 * @param content 待预览的模板正文
 */
@Builder
public record PromptPreviewRequest(@NotBlank(message = "Prompt模板正文不能为空") String content) {
}
