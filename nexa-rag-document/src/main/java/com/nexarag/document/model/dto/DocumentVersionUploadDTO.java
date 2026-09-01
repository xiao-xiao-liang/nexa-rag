package com.nexarag.document.model.dto;

import com.nexarag.document.enums.FileType;
import com.nexarag.infra.enums.ExternalDocumentSourceType;
import lombok.Builder;

/**
 * 创建文档版本时保存文件快照的传输对象。
 *
 * @param originalFileName 原始文件名
 * @param fileType 文件类型
 * @param fileSize 文件大小
 * @param originalFileUrl 原始文件地址
 * @param originalObjectName 原始文件对象名
 * @param sourceType 文档来源类型
 * @param sourceUrl 外部来源URL
 */
@Builder
public record DocumentVersionUploadDTO(
        String originalFileName,
        FileType fileType,
        Long fileSize,
        String originalFileUrl,
        String originalObjectName,
        ExternalDocumentSourceType sourceType,
        String sourceUrl) {
}
