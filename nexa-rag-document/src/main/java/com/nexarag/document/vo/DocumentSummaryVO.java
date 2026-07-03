package com.nexarag.document.vo;

import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.FileType;

/**
 * 文档摘要响应。
 *
 * @param documentId       文档ID
 * @param title            文档标题
 * @param originalFileName 原始文件名
 * @param fileType         文件类型
 * @param status           文档状态
 */
public record DocumentSummaryVO(Long documentId, String title, String originalFileName,
                                FileType fileType, DocumentStatus status) {
}
