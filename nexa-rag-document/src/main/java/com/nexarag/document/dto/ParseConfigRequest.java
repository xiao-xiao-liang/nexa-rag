package com.nexarag.document.dto;

/**
 * 文档解析配置请求，描述本次文档处理的解析附加能力。
 *
 * @param enableOcr              是否启用 OCR
 * @param enableImageDescription 是否启用图片描述
 */
public record ParseConfigRequest(Boolean enableOcr,
                                 Boolean enableImageDescription) {
}
