package com.nexarag.document.dto;

import com.nexarag.document.enums.SplitStrategy;

/**
 * 文档切分配置请求。
 *
 * @param splitStrategy 切分策略
 * @param chunkSize     片段大小
 * @param chunkOverlap  片段重叠大小
 */
public record SplitConfigRequest(SplitStrategy splitStrategy, Integer chunkSize, Integer chunkOverlap) {
}
