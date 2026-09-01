package com.nexarag.document.enums;

/**
 * 文档版本操作审计类型。
 */
public enum DocumentVersionOperationType {

    /** 创建并上传新版本。 */
    UPLOAD,

    /** 自动发布索引完成的新版本。 */
    AUTO_PUBLISH,

    /** 将当前版本指针回退到历史版本。 */
    ROLLBACK,

    /** 重试失败版本。 */
    RETRY,

    /** 永久删除历史版本。 */
    DELETE
}
