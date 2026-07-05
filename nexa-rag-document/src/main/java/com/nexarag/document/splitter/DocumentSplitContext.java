package com.nexarag.document.splitter;

import com.nexarag.document.dto.SplitConfigRequest;
import com.nexarag.document.enums.FileType;

/**
 * 文档切分上下文，承载一次切分所需的稳定输入。
 *
 * @param documentId        文档ID
 * @param title             文档标题
 * @param originalFileName  原始文件名
 * @param fileType          文件类型
 * @param originalObjectName 原始文件对象名
 * @param originalFileUrl   原始文件地址
 * @param parsedObjectName  解析后文件对象名
 * @param parsedFileUrl     解析后文件地址
 * @param parsedContentType 解析后内容类型
 * @param content           文本内容
 * @param fileBytes         文件字节
 * @param config            切分配置
 */
public record DocumentSplitContext(Long documentId,
                                   String title,
                                   String originalFileName,
                                   FileType fileType,
                                   String originalObjectName,
                                   String originalFileUrl,
                                   String parsedObjectName,
                                   String parsedFileUrl,
                                   String parsedContentType,
                                   String content,
                                   byte[] fileBytes,
                                   SplitConfigRequest config) {
}
