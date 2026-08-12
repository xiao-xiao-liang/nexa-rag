package com.nexarag.infra.source.model;

import com.nexarag.infra.parser.model.DocumentFormat;

import java.nio.file.Path;
import java.util.Map;

/**
 * 来源 Reader 读取成功后的文件化结果，原始内容位于当前任务工作区。
 */
public record SourceReadResultBO(Path sourcePath, String sourceContentType, DocumentFormat documentFormat,
                                 String originalFileName, String title, String externalDocumentId,
                                 String externalRevisionId,
                                 Map<String, Object> metadata) {
}
