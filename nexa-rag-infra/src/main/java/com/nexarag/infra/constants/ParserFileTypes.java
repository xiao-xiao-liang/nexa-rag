package com.nexarag.infra.constants;

/**
 * 解析器使用的文件类型常量，保持 infra 不依赖 document 模块的 FileType 枚举。
 */
public final class ParserFileTypes {

    public static final String PDF = "PDF";
    public static final String WORD = "WORD";
    public static final String EXCEL = "EXCEL";
    public static final String PPT = "PPT";
    public static final String MARKDOWN = "MARKDOWN";
    public static final String TEXT = "TEXT";
    public static final String UNKNOWN = "UNKNOWN";

    private ParserFileTypes() {
    }
}
