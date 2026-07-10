package com.nexarag.infra.constants;

/**
 * 解析产物内容类型常量，避免解析器之间重复硬编码 MIME 类型。
 */
public final class ParsedContentTypes {

    public static final String TEXT_MARKDOWN = "text/markdown";
    public static final String TEXT_PLAIN = "text/plain";
    public static final String EXCEL = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String OCTET_STREAM = "application/octet-stream";

    private ParsedContentTypes() {
    }
}
