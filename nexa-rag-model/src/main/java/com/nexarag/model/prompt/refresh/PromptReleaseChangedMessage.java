package com.nexarag.model.prompt.refresh;

/**
 * Prompt 当前发布记录变更的轻量刷新消息。
 *
 * @param promptCode      Prompt 编码
 * @param releaseId       发布记录 ID
 * @param releaseRevision 单调递增的发布代次
 */
public record PromptReleaseChangedMessage(String promptCode, Long releaseId, Long releaseRevision) {
}
