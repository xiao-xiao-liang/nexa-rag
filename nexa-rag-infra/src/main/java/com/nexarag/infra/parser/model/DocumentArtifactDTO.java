package com.nexarag.infra.parser.model;

import lombok.Builder;

/**
 * 文档制品处理请求，在工作流与 infra 解析门面之间传递处理上下文。
 *
 * @param documentId             文档ID
 * @param originalFileName       原始文件名
 * @param format                 文件格式
 * @param originalObjectName     原始文件对象名
 * @param originalFileUrl        原始文件访问地址
 * @param enableOcr              是否启用 OCR
 * @param enableImageDescription 是否启用图片描述
 */
@Builder
public record DocumentArtifactDTO(Long documentId,
                                  String originalFileName,
                                  DocumentFormat format,
                                  String originalObjectName,
                                  String originalFileUrl,
                                  Boolean enableOcr,
                                  Boolean enableImageDescription) {
}
