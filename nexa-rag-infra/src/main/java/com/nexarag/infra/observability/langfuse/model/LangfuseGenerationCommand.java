package com.nexarag.infra.observability.langfuse.model;

import java.util.Map;

/**
 * 创建 Langfuse Generation 所需的非敏感元数据。
 *
 * @param name          Generation 名称
 * @param modelName     模型名称
 * @param provider      模型提供方
 * @param callId        模型调用日志 ID
 * @param generationId  聊天生成 ID
 * @param correlationId 业务关联 ID
 * @param attributes    受控扩展属性
 */
public record LangfuseGenerationCommand(String name, String modelName, String provider, String callId,
                                        String generationId, String correlationId, Map<String, Object> attributes) {

    public LangfuseGenerationCommand {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
