package com.nexarag.document.enums;

import java.util.Locale;

/**
 * 文档文件类型。
 */
public enum FileType {

    /**
     * PDF 文件。
     */
    PDF,

    /**
     * Word 文件。
     */
    WORD,

    /**
     * Excel 或 CSV 文件。
     */
    EXCEL,

    /**
     * PPT 文件。
     */
    PPT,

    /**
     * Markdown 文件。
     */
    MARKDOWN,

    /**
     * 纯文本文件。
     */
    TEXT,

    /**
     * 未知文件类型。
     */
    UNKNOWN;

    /**
     * 根据文件名解析文件类型。
     *
     * @param fileName 文件名
     * @return 文件类型
     */
    public static FileType fromFileName(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return UNKNOWN;
        }
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "pdf" -> PDF;
            case "doc", "docx" -> WORD;
            case "xls", "xlsx", "csv" -> EXCEL;
            case "ppt", "pptx" -> PPT;
            case "md", "markdown" -> MARKDOWN;
            case "txt" -> TEXT;
            default -> UNKNOWN;
        };
    }
}
