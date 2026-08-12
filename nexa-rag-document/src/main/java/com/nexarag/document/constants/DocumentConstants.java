package com.nexarag.document.constants;

/**
 * 文档领域通用常量，统一维护文档默认值与持久化字段长度约束。
 */
public final class DocumentConstants {

    /** 外部文档在远端标题尚未读取完成前使用的默认标题。 */
    public static final String DEFAULT_EXTERNAL_DOCUMENT_TITLE = "外部文档";

    /** 文档标题最大长度，与 document.title 字段保持一致。 */
    public static final int MAX_TITLE_LENGTH = 256;

    /** 原始文件名最大长度，与 document.original_file_name 字段保持一致。 */
    public static final int MAX_ORIGINAL_FILE_NAME_LENGTH = 512;

    /** Markdown 文件扩展名。 */
    public static final String MARKDOWN_FILE_EXTENSION = ".md";

    private DocumentConstants() {
    }
}
