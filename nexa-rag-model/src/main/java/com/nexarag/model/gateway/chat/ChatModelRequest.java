package com.nexarag.model.gateway.chat;

import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.execution.telemetry.RagTokenBreakdown;
import io.opentelemetry.context.Context;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * 聊天模型请求。
 *
 * @param traceId              链路追踪 ID
 * @param bizType              业务类型
 * @param bizId                业务 ID
 * @param routeKey             路由键
 * @param messages             消息列表
 * @param options              调用选项
 * @param observabilityContext 仅最终回答使用的 RAG Token 分段观测上下文
 * @param generationId         聊天生成 ID
 * @param observationName      Langfuse Generation 语义名称
 * @param langfuseContext      上游 Langfuse Trace 的 OTel 上下文
 */
@Builder
public record ChatModelRequest(String traceId, ModelBizType bizType, String bizId, String routeKey,
                               List<ChatModelMessage> messages, Map<String, Object> options,
                               RagTokenBreakdown observabilityContext, String generationId,
                               String observationName, Context langfuseContext) {
}
