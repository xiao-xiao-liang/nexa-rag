package com.nexarag.document.vo;

import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.FileType;

/**
 * 文档详情响应。
 *
 * @param documentId        文档ID
 * @param title             文档标题
 * @param description       文档描述
 * @param originalFileName  原始文件名
 * @param fileType          文件类型
 * @param fileSize          文件大小
 * @param originalFileUrl   原始文件地址
 * @param parsedFileUrl     解析后文件地址
 * @param status            文档状态
 * @param processConfigJson 处理配置快照
 */
public record DocumentDetailVO(Long documentId, String title, String description, String originalFileName,
                               FileType fileType, Long fileSize, String originalFileUrl,
                               String parsedFileUrl, DocumentStatus status, String processConfigJson) {
}
