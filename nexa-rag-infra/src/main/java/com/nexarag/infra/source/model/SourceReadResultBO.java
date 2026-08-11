package com.nexarag.infra.source.model;

import java.util.Map;

/**
 * 来源 Reader 读取成功后的内存结果，尚未写入对象存储。
 */
public record SourceReadResultBO(byte[] snapshotContent, String snapshotContentType, String markdownContent,
                                 String title, String externalDocumentId, String externalRevisionId,
                                 Map<String, Object> metadata) {
}
