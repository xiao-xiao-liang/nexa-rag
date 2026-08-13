package com.nexarag.infra.parser.model;

import java.nio.file.Path;

/**
 * 解析过程中保留的结构辅助制品。
 *
 * @param file 工作区内的制品文件
 * @param relativePath 发布到结构目录时使用的安全相对文件名
 * @param contentType 制品内容类型
 */
public record ExtractedStructureArtifactBO(Path file, String relativePath, String contentType) {
}
