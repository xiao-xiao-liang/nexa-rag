package com.nexarag.document.constants;

/**
 * 文档索引阶段的批处理参数。
 */
public final class DocumentIndexingConstants {

    /**
     * 单次数据库批量回写索引状态的最大片段数。
     */
    public static final int INDEX_STATUS_UPDATE_BATCH_SIZE = 200;

    private DocumentIndexingConstants() {
    }
}
