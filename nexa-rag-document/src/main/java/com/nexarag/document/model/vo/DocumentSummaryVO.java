package com.nexarag.document.model.vo;

import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.FileType;

import java.time.LocalDateTime;

/**
 * 文档摘要响应。
 *
 * @param documentId       文档ID
 * @param title            文档标题
 * @param originalFileName 原始文件名
 * @param fileType         文件类型
 * @param fileSize         文件大小（字节）
 * @param status           文档状态
 * @param createBy         创建人
 * @param updatedTime      更新时间
 */
public record DocumentSummaryVO(Long documentId, String title, String originalFileName,
                                FileType fileType, Long fileSize, DocumentStatus status, String createBy,
                                LocalDateTime updatedTime) {
}
