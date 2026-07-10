package com.nexarag.infra.parser;

import com.nexarag.infra.parser.model.DocumentParseRequest;
import com.nexarag.infra.parser.model.DocumentParseResult;

/**
 * 文档解析器接口，定义不同文件类型解析适配器需要实现的统一能力。
 */
public interface DocumentParser {

    /**
     * 判断当前解析器是否支持本次解析请求。
     *
     * @param request 文档解析请求
     * @return true 表示支持，false 表示不支持
     */
    boolean supports(DocumentParseRequest request);

    /**
     * 解析文档并返回解析产物信息。
     *
     * @param request 文档解析请求
     * @return 文档解析结果
     */
    DocumentParseResult parse(DocumentParseRequest request);
}
