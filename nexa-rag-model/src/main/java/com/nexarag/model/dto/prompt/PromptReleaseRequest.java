package com.nexarag.model.dto.prompt;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

/**
 * Prompt 正式版与灰度版发布请求。
 *
 * @param stableVersionId 正式版本 ID
 * @param canaryVersionId 灰度版本 ID
 * @param canaryPercentage 灰度用户百分比
 */
@Builder
public record PromptReleaseRequest(@NotNull(message = "正式版本ID不能为空") Long stableVersionId,
                                   Long canaryVersionId,
                                   @Min(value = 0, message = "灰度百分比必须在0到100之间")
                                   @Max(value = 100, message = "灰度百分比必须在0到100之间")
                                   Integer canaryPercentage) {
}
