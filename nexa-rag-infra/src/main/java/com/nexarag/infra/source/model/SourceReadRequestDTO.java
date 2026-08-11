package com.nexarag.infra.source.model;

import com.nexarag.infra.enums.ExternalDocumentSourceType;

/**
 * 外部来源读取请求，携带已校验的来源定位信息。
 */
public record SourceReadRequestDTO(
        Long documentId,
        ExternalDocumentSourceType sourceType,
        String sourceUrl
) {
}
