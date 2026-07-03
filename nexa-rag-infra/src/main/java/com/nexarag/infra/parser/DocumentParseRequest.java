package com.nexarag.infra.parser;

import java.io.InputStream;

/**
 * 文档解析请求。
 *
 * @param documentId  文档ID
 * @param fileName    文件名
 * @param fileType    文件类型
 * @param inputStream 文件输入流
 */
public record DocumentParseRequest(Long documentId, String fileName, String fileType, InputStream inputStream) {
}
