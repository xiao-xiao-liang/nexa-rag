package com.nexarag.infra.parser;

/**
 * 文档解析结果。
 *
 * @param contentType   内容类型
 * @param content       解析后的内容
 * @param parsedFileUrl 解析产物地址
 */
public record DocumentParseResult(String contentType, String content, String parsedFileUrl) {
}
