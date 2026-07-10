package com.nexarag.infra.parser.model;

/**
 * MinerU 解析资源文件。
 *
 * @param relativePath 资源相对路径
 * @param fileName 文件名
 * @param content 文件内容
 */
public record MinerUAssetFile(String relativePath,
                              String fileName,
                              byte[] content) {
}