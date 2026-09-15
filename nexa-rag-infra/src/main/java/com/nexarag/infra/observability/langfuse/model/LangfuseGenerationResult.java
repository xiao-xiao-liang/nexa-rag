package com.nexarag.infra.observability.langfuse.model;

import java.util.Map;
import java.time.Instant;

/**
 * Langfuse Generation 的终态指标，不包含 Prompt 或输出正文。
 *
 * @param inputTokens    厂商返回的输入 Token 数
 * @param outputTokens   厂商返回的输出 Token 数
 * @param totalTokens    厂商返回的总 Token 数
 * @param firstTokenMs   首个文本 Token 时延
 * @param terminalState  终态，例如 COMPLETED、ERROR、CANCELED
 * @param attributes     受控扩展指标
 * @param finishedAt     模型调用实际结束时间
 */
public record LangfuseGenerationResult(Integer inputTokens, Integer outputTokens, Integer totalTokens,
                                        Long firstTokenMs, String terminalState, Map<String, Object> attributes,
                                        Instant finishedAt) {

    public LangfuseGenerationResult(Integer inputTokens, Integer outputTokens, Integer totalTokens,
                                    Long firstTokenMs, String terminalState, Map<String, Object> attributes) {
        this(inputTokens, outputTokens, totalTokens, firstTokenMs, terminalState, attributes, null);
    }

    public LangfuseGenerationResult {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
