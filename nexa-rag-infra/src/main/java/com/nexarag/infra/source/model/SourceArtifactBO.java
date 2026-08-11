package com.nexarag.infra.source.model;

import com.nexarag.infra.parser.model.ParsedArtifact;

import java.util.Map;

/**
 * 外部来源已持久化后的制品信息，供工作流回写文档状态。
 */
public record SourceArtifactBO(ParsedArtifact parsedArtifact, String title, String sourceSnapshotObjectName,
                               Map<String, Object> metadata) {
}
