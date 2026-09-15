package com.nexarag.boot.observability;

/**
 * 模型调用明细的安全展示字段。
 *
 * <p>不包含问题、证据、提示词、回答、会话和用户标识。</p>
 *
 * @param traceId               Langfuse Trace 标识
 * @param generationId          Langfuse Generation 标识
 * @param startTime             调用开始时间
 * @param model                 模型名称
 * @param routeKey              模型路由标识
 * @param status                调用状态
 * @param inputTokens           vLLM 返回的输入 Token
 * @param outputTokens          vLLM 返回的输出 Token
 * @param totalTokens           vLLM 返回的总 Token
 * @param timeToFirstTokenMs    首 Token 时延
 * @param ragTokenStatus        RAG 分段 Token 统计状态
 */
public record ModelObservabilityTraceVO(String traceId, String generationId, String startTime, String model,
                                        String routeKey, String status, Integer inputTokens, Integer outputTokens,
                                        Integer totalTokens, Integer timeToFirstTokenMs, String ragTokenStatus) {
}
