package com.nexarag.document.dto;

import com.nexarag.infra.parser.ParserType;

/**
 * 文档解析配置请求，描述本次文档处理希望使用的解析能力。
 *
 * @param parserType             解析器类型
 * @param enableOcr              是否启用 OCR
 * @param enableImageDescription 是否启用图片描述
 */
public record ParseConfigRequest(ParserType parserType,
                                 Boolean enableOcr,
                                 Boolean enableImageDescription) {
}
