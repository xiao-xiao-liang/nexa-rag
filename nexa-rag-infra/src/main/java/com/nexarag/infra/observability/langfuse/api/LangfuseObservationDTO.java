package com.nexarag.infra.observability.langfuse.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Langfuse Observations API v2 的安全字段映射。
 *
 * <p>刻意不声明 input 和 output 字段，避免正文进入应用查询链路。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LangfuseObservationDTO(String id, String traceId, String name, String type, String startTime,
                                     String endTime, String model, Map<String, Object> usageDetails,
                                     Number latency, Number timeToFirstToken, Map<String, Object> metadata,
                                     String level, String statusMessage, String sessionId, String userId) {
}
