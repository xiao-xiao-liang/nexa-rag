package com.nexarag.document.enums;

/**
 * 文档片段状态。
 */
public enum ChunkStatus {

    /**
     * 待索引。
     */
    PENDING_INDEX,

    /**
     * 已索引。
     */
    INDEXED,

    /**
     * 跳过索引。
     */
    SKIP_INDEX,

    /**
     * 索引失败。
     */
    FAILED
}
