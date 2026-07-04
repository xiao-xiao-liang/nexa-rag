package com.nexarag.infra.parser.mineru;

import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * MinerU ZIP 解压后的中间结果。
 *
 * @param markdownFileName Markdown 文件名
 * @param markdownContent Markdown 内容
 * @param assetFiles 资源文件列表
 * @param metadata 解压元数据
 */
@Builder
public record MinerUExtractedResult(String markdownFileName,
                                    String markdownContent,
                                    List<MinerUAssetFile> assetFiles,
                                    Map<String, Object> metadata) {
}