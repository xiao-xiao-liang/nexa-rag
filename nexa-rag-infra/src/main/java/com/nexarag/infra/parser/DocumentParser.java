package com.nexarag.infra.parser;

/**
 * 文档解析器接口。
 */
public interface DocumentParser {

    /**
     * 判断当前解析器是否支持指定文件类型。
     *
     * @param fileType 文件类型
     * @return true 表示支持，false 表示不支持
     */
    boolean supports(String fileType);

    /**
     * 解析文档。
     *
     * @param request 文档解析请求
     * @return 文档解析结果
     */
    DocumentParseResult parse(DocumentParseRequest request);
}
