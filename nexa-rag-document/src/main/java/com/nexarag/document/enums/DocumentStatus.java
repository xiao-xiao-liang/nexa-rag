package com.nexarag.document.enums;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 文档处理状态。
 */
public enum DocumentStatus {

    /**
     * 已上传。
     */
    UPLOADED,

    /**
     * 排队中。
     */
    QUEUED,

    /**
     * 解析中。
     */
    PARSING,

    /**
     * 解析完成。
     */
    PARSED,

    /**
     * 切分中。
     */
    CHUNKING,

    /**
     * 切分完成。
     */
    CHUNKED,

    /**
     * 索引写入中。
     */
    INDEXING,

    /**
     * 索引完成。
     */
    INDEXED,

    /**
     * 处理失败。
     */
    FAILED;

    private static final Map<DocumentStatus, Set<DocumentStatus>> TRANSITIONS = new EnumMap<>(DocumentStatus.class);

    static {
        TRANSITIONS.put(UPLOADED, EnumSet.of(QUEUED));
        TRANSITIONS.put(QUEUED, EnumSet.of(PARSING, FAILED));
        TRANSITIONS.put(PARSING, EnumSet.of(QUEUED, PARSED, FAILED));
        TRANSITIONS.put(PARSED, EnumSet.of(CHUNKING, FAILED));
        TRANSITIONS.put(CHUNKING, EnumSet.of(QUEUED, CHUNKED, FAILED));
        TRANSITIONS.put(CHUNKED, EnumSet.of(INDEXING, FAILED));
        TRANSITIONS.put(INDEXING, EnumSet.of(QUEUED, INDEXED, FAILED));
        TRANSITIONS.put(INDEXED, EnumSet.of(QUEUED));
        TRANSITIONS.put(FAILED, EnumSet.of(QUEUED));
    }

    /**
     * 判断是否允许流转到目标状态。
     *
     * @param targetStatus 目标状态
     * @return true 表示允许流转，false 表示不允许
     */
    public boolean canTransferTo(DocumentStatus targetStatus) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(targetStatus);
    }
}
