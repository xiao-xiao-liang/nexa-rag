package com.nexarag.model.governance;

import lombok.Builder;
import lombok.Getter;

/**
 * 模型治理执行参数，承载重试、熔断、限流和并发隔离配置。
 */
@Getter
@Builder
public class ModelGovernanceSettings {

    /**
     * 是否启用重试。
     */
    private Boolean retryEnabled;

    /**
     * 最大尝试次数。
     */
    private Integer maxAttempts;

    /**
     * 重试等待时间，单位毫秒。
     */
    private Integer retryWaitMs;

    /**
     * 是否启用熔断。
     */
    private Boolean circuitEnabled;

    /**
     * 失败率阈值。
     */
    private Integer failureRateThreshold;

    /**
     * 慢调用比例阈值。
     */
    private Integer slowCallRateThreshold;

    /**
     * 慢调用判定时长，单位毫秒。
     */
    private Integer slowCallDurationMs;

    /**
     * 熔断统计最小调用数。
     */
    private Integer minimumNumberOfCalls;

    /**
     * 熔断滑动窗口大小。
     */
    private Integer slidingWindowSize;

    /**
     * 熔断打开后的等待时间，单位毫秒。
     */
    private Integer waitDurationInOpenStateMs;

    /**
     * 是否启用限流。
     */
    private Boolean rateLimitEnabled;

    /**
     * 单周期允许调用数。
     */
    private Integer limitForPeriod;

    /**
     * 限流周期刷新时间，单位毫秒。
     */
    private Integer limitRefreshPeriodMs;

    /**
     * 获取限流许可等待时间，单位毫秒。
     */
    private Integer timeoutDurationMs;

    /**
     * 是否启用并发隔离。
     */
    private Boolean bulkheadEnabled;

    /**
     * 是否启用同步调用超时保护。
     */
    private Boolean timeLimiterEnabled;

    /**
     * 同步调用超时时间，单位毫秒。
     */
    private Integer timeLimiterTimeoutMs;

    /**
     * 流式调用首个分片超时时间，单位毫秒。
     */
    private Integer streamFirstChunkTimeoutMs;

    /**
     * 流式调用最大持续时间，单位毫秒。
     */
    private Integer streamMaxDurationMs;

    /**
     * 最大并发调用数。
     */
    private Integer maxConcurrentCalls;

    /**
     * 获取并发许可等待时间，单位毫秒。
     */
    private Integer maxWaitDurationMs;

    /**
     * 创建关闭全部治理能力的运行时配置。
     *
     * @return 关闭治理能力的运行时配置
     */
    public static ModelGovernanceSettings disabled() {
        return ModelGovernanceSettings.builder()
                .retryEnabled(false)
                .circuitEnabled(false)
                .rateLimitEnabled(false)
                .bulkheadEnabled(false)
                .timeLimiterEnabled(false)
                .build();
    }
}
