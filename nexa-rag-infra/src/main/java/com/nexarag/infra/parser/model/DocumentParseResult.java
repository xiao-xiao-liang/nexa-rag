package com.nexarag.infra.parser.model;

import lombok.Builder;

import java.util.Map;

/**
 * 文档解析结果，描述解析后的标准产物及解析元数据。
 *
 * @param contentType      解析后内容类型
 * @param content          解析后文本内容，小文件可返回，较大文件可为空
 * @param parsedObjectName 解析后文件对象名
 * @param parsedFileUrl    解析后文件访问地址
 * @param metadata         解析元数据
 */
@Builder
public record DocumentParseResult(String contentType,
                                  String content,
                                  String parsedObjectName,
                                  String parsedFileUrl,
                                  Map<String, Object> metadata) {
}
