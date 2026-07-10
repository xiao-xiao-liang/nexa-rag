package com.nexarag.infra.parser.model;

import lombok.Builder;

/**
 * 文档解析请求，承载解析器选择和读取原始文件所需的上下文。
 *
 * @param documentId             文档ID
 * @param originalFileName       原始文件名
 * @param fileType               文件类型
 * @param originalObjectName     原始文件对象名
 * @param originalFileUrl        原始文件访问地址
 * @param enableOcr              是否启用 OCR
 * @param enableImageDescription 是否启用图片描述
 */
@Builder
public record DocumentParseRequest(Long documentId,
                                   String originalFileName,
                                   String fileType,
                                   String originalObjectName,
                                   String originalFileUrl,
                                   Boolean enableOcr,
                                   Boolean enableImageDescription) {
}
