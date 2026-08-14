package com.nexarag.document.model.vo;

import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.FileType;
import com.nexarag.infra.enums.ExternalDocumentSourceType;

import java.time.LocalDateTime;

/**
 * 文档诊断概览响应，承载文档基础信息、处理配置快照与片段状态统计。
 *
 * @param documentId        文档ID
 * @param title             文档标题
 * @param description       文档描述
 * @param originalFileName  原始文件名
 * @param fileType          文件类型
 * @param fileSize          文件大小
 * @param status            文档处理状态
 * @param sourceType        文档来源类型
 * @param sourceUrl         外部来源链接
 * @param processConfigJson 处理配置快照JSON
 * @param createTime        创建时间
 * @param updateTime        更新时间
 * @param chunkStatistics   片段状态统计
 */
public record DocumentOverviewVO(
        Long documentId,
        String title,
        String description,
        String originalFileName,
        FileType fileType,
        Long fileSize,
        DocumentStatus status,
        ExternalDocumentSourceType sourceType,
        String sourceUrl,
        String processConfigJson,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        DocumentChunkStatisticsVO chunkStatistics) {
}
