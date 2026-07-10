package com.nexarag.infra.parser.service;

import com.nexarag.infra.parser.model.DocumentParseRequest;
import com.nexarag.infra.parser.model.DocumentParseResult;

/**
 * 文档解析服务，负责根据文件类型选择解析器并执行解析。
 */
public interface DocumentParseService {

    /**
     * 解析文档并返回解析结果。
     *
     * @param request 文档解析请求
     * @return 文档解析结果
     */
    DocumentParseResult parse(DocumentParseRequest request);
}
