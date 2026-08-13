package com.nexarag.document.model.bo.split;

import com.nexarag.document.model.dto.SplitConfigRequest;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.model.bo.structure.StructureArtifactReferenceBO;

import java.util.List;

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
 * @param structureArtifacts 已发布的结构辅助制品引用
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
                                   SplitConfigRequest config,
                                   List<StructureArtifactReferenceBO> structureArtifacts) {

    /** 兼容尚未产生结构制品的现有调用方。 */
    public DocumentSplitContext(Long documentId, String title, String originalFileName, FileType fileType,
                                String originalObjectName, String originalFileUrl, String parsedObjectName,
                                String parsedFileUrl, String parsedContentType, String content, byte[] fileBytes,
                                SplitConfigRequest config) {
        this(documentId, title, originalFileName, fileType, originalObjectName, originalFileUrl, parsedObjectName,
                parsedFileUrl, parsedContentType, content, fileBytes, config, List.of());
    }
}
