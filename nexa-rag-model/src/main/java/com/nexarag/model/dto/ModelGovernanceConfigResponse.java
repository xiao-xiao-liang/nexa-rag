package com.nexarag.model.dto;

import lombok.Builder;

/**
 * 模型治理配置响应。
 *
 * @param governanceId              治理配置ID
 * @param configId                  模型配置ID
 * @param enabled                   是否启用治理配置
 * @param retryEnabled              是否启用重试
 * @param maxAttempts               最大尝试次数
 * @param retryWaitMs               重试等待时间，单位毫秒
 * @param circuitEnabled            是否启用熔断
 * @param failureRateThreshold      失败率阈值
 * @param slowCallRateThreshold     慢调用比例阈值
 * @param slowCallDurationMs        慢调用判定时长，单位毫秒
 * @param minimumNumberOfCalls      熔断统计最小调用数
 * @param slidingWindowSize         熔断滑动窗口大小
 * @param waitDurationInOpenStateMs 熔断打开后的等待时间，单位毫秒
 * @param rateLimitEnabled          是否启用限流
 * @param limitForPeriod            单周期允许调用数
 * @param limitRefreshPeriodMs      限流周期刷新时间，单位毫秒
 * @param timeoutDurationMs         获取限流许可等待时间，单位毫秒
 * @param bulkheadEnabled           是否启用并发隔离
 * @param maxConcurrentCalls        最大并发调用数
 * @param maxWaitDurationMs         获取并发许可等待时间，单位毫秒
 */
@Builder
public record ModelGovernanceConfigResponse(
        Long governanceId,
        Long configId,
        Boolean enabled,
        Boolean retryEnabled,
        Integer maxAttempts,
        Integer retryWaitMs,
        Boolean circuitEnabled,
        Integer failureRateThreshold,
        Integer slowCallRateThreshold,
        Integer slowCallDurationMs,
        Integer minimumNumberOfCalls,
        Integer slidingWindowSize,
        Integer waitDurationInOpenStateMs,
        Boolean rateLimitEnabled,
        Integer limitForPeriod,
        Integer limitRefreshPeriodMs,
        Integer timeoutDurationMs,
        Boolean bulkheadEnabled,
        Integer maxConcurrentCalls,
        Integer maxWaitDurationMs) {
}
