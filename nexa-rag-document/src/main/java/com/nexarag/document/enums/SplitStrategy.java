package com.nexarag.document.enums;

/**
 * 文档切分策略。
 */
public enum SplitStrategy {

    /**
     * 父子 Markdown 切分。
     */
    PARENT_MARKDOWN,

    /**
     * 同级 Markdown 切分。
     */
    BROTHER_MARKDOWN,

    /**
     * 正则文本切分。
     */
    REGEX_TEXT,

    /**
     * 表格切分。
     */
    EXCEL
}
