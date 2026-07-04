package com.nexarag.infra.parser.mineru;

import lombok.Builder;

import java.io.InputStream;

/**
 * MinerU 文件解析命令。
 *
 * @param documentId 文档ID
 * @param fileName 文件名
 * @param inputStream 文件输入流
 * @param enableOcr 是否启用 OCR
 */
@Builder
public record MinerUParseCommand(Long documentId,
                                 String fileName,
                                 InputStream inputStream,
                                 boolean enableOcr) {
}