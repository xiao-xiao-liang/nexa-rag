package com.nexarag.model.dto.prompt;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * Prompt 正文提交请求。
 *
 * @param content 模板正文
 */
@Builder
public record PromptSubmitRequest(@NotBlank(message = "Prompt模板正文不能为空") String content) {
}
