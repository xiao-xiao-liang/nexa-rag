package com.nexarag.infra.parser.model;

import lombok.Builder;

import java.util.Map;

/**
 * 解析产物，表示已持久化到对象存储的整份标准化文档。
 *
 * <p>该对象只用于解析阶段与后续工作流之间交接定位信息；正文只能由切分阶段
 * 根据 {@code objectKey} 从对象存储读取，不能作为内存字段跨阶段传递。</p>
 *
 * @param objectKey   解析产物对象键
 * @param contentType 解析产物内容类型
 * @param metadata    解析元数据
 */
@Builder
public record ParsedArtifact(String objectKey,
                             String contentType,
                             Map<String, Object> metadata) {
}
