package com.nexarag.infra.parser.model;

import lombok.Builder;

import java.io.InputStream;
import java.util.Map;

/**
 * MinerU 文件解析响应。
 *
 * @param zipInputStream ZIP 解析产物输入流
 * @param metadata 解析元数据
 */
@Builder
public record MinerUParseResponse(InputStream zipInputStream,
                                  Map<String, Object> metadata) {
}