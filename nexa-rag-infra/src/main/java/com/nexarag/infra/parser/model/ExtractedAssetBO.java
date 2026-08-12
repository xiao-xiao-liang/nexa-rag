package com.nexarag.infra.parser.model;

import java.nio.file.Path;

/**
 * 文档转换过程中生成的本地资源文件。
 *
 * @param file         工作区内的资源文件
 * @param relativePath Markdown 中引用的相对路径
 * @param contentType  资源内容类型
 */
public record ExtractedAssetBO(Path file, String relativePath, String contentType) {
}
