package com.nexarag.infra.parser.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 文档转换器输出的文件化制品，正文和资源均位于当前任务工作区。
 *
 * @param markdownPath 主 Markdown 文件
 * @param assets       资源文件列表
 * @param metadata     小型转换元数据
 */
public record ExtractedDocumentBO(Path markdownPath,
                                  List<ExtractedAssetBO> assets,
                                  Map<String, Object> metadata) {
}
