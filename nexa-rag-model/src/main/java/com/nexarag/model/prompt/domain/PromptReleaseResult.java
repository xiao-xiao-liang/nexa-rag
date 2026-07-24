package com.nexarag.model.prompt.domain;

/**
 * Prompt 发布操作结果。
 *
 * @param versionId  本次使用的正式版本ID
 * @param releaseId  新增发布记录ID
 * @param releaseRevision 新发布代次
 */
public record PromptReleaseResult(Long versionId, Long releaseId, Long releaseRevision) {
}
