package com.nexarag.model.dto.prompt;

import lombok.Builder;

/**
 * Prompt 发布操作响应。
 *
 * @param versionId 生效的正式版本 ID
 * @param releaseId 新增发布记录 ID
 * @param releaseRevision 新增发布代次
 */
@Builder
public record PromptReleaseResponse(Long versionId, Long releaseId, Long releaseRevision) {
}
