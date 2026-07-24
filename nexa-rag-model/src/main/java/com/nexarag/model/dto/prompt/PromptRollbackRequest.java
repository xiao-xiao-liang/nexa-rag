package com.nexarag.model.dto.prompt;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

/**
 * Prompt 回滚请求。
 *
 * @param targetVersionId 目标历史版本 ID
 */
@Builder
public record PromptRollbackRequest(@NotNull(message = "目标版本ID不能为空") Long targetVersionId) {
}
