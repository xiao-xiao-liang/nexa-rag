package com.nexarag.model.dto.prompt;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Prompt 管理查询响应，包含定义、版本与发布历史。
 */
@Builder
public record PromptResponse(String promptCode, String name, String variableSchema, Boolean enabled,
                             Long currentReleaseId, Long currentReleaseRevision,
                             List<Version> versions, List<Release> releases) {

    /**
     * Prompt 不可变正文版本响应。
     */
    @Builder
    public record Version(Long versionId, Long versionNo, String content, String createdBy,
                          LocalDateTime createdAt, String remark) {
    }

    /**
     * Prompt 发布历史响应。
     */
    @Builder
    public record Release(Long releaseId, Long stableVersionId, Long canaryVersionId, String canaryRule,
                          Long releaseRevision, String releasedBy, LocalDateTime releasedAt,
                          Long rollbackFromReleaseId, String remark) {
    }
}
