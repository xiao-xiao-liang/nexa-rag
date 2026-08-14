package com.nexarag.document.model.vo;

/**
 * 文档片段状态统计响应，用于文档诊断概览展示各状态片段数量。
 *
 * @param total   片段总数
 * @param indexed 已索引片段数
 * @param failed  索引失败片段数
 * @param skipped 跳过索引片段数
 * @param pending 待索引片段数
 */
public record DocumentChunkStatisticsVO(long total, long indexed, long failed, long skipped, long pending) {
}
