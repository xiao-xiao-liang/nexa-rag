package com.nexarag.document.enums;

/**
 * 文档版本处理状态。
 */
public enum DocumentVersionStatus {

    /** 已上传，等待提交处理。 */
    UPLOADED,

    /** 已进入处理队列。 */
    QUEUED,

    /** 正在解析。 */
    PARSING,

    /** 解析完成。 */
    PARSED,

    /** 正在切分。 */
    CHUNKING,

    /** 切分完成。 */
    CHUNKED,

    /** 正在写入索引。 */
    INDEXING,

    /** 索引已预热，可被发布或回退。 */
    INDEX_READY,

    /** 处理失败，可重新提交。 */
    FAILED,

    /** 正在执行永久删除清理。 */
    DELETING
}
