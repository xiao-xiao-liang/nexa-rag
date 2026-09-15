package com.nexarag.infra.observability.langfuse.model;

import java.util.Map;

/**
 * 创建 Langfuse 根 Trace 所需的可信属性快照。
 *
 * @param name          Trace 名称
 * @param correlationId NexaRAG 业务链路关联 ID
 * @param sessionId     可选会话 ID
 * @param userId        可选用户 ID
 * @param attributes    已校验的自定义属性
 */
public record LangfuseTraceCommand(String name, String correlationId, String sessionId, String userId,
                                   Map<String, Object> attributes) {

    /**
     * 防御性复制自定义属性，避免流式生命周期中被调用方篡改。
     */
    public LangfuseTraceCommand {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
