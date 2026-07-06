package com.nexarag.model.governance;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 模型治理执行器，负责在模型调用外层应用重试、熔断、限流和并发隔离能力。
 */
@Component
public class ModelGovernanceExecutor {

    private static final int DEFAULT_MAX_ATTEMPTS = 1;
    private static final int DEFAULT_RETRY_WAIT_MS = 0;
    private static final int DEFAULT_FAILURE_RATE_THRESHOLD = 50;
    private static final int DEFAULT_SLOW_CALL_RATE_THRESHOLD = 100;
    private static final int DEFAULT_SLOW_CALL_DURATION_MS = 3000;
    private static final int DEFAULT_MINIMUM_NUMBER_OF_CALLS = 10;
    private static final int DEFAULT_SLIDING_WINDOW_SIZE = 20;
    private static final int DEFAULT_WAIT_DURATION_IN_OPEN_STATE_MS = 30000;
    private static final int DEFAULT_LIMIT_FOR_PERIOD = 100;
    private static final int DEFAULT_LIMIT_REFRESH_PERIOD_MS = 1000;
    private static final int DEFAULT_TIMEOUT_DURATION_MS = 0;
    private static final int DEFAULT_MAX_CONCURRENT_CALLS = 20;
    private static final int DEFAULT_MAX_WAIT_DURATION_MS = 0;

    /**
     * 执行受治理保护的模型调用。
     *
     * @param configKey 模型配置标识
     * @param settings  治理配置
     * @param supplier  实际模型调用
     * @param <T>       返回类型
     * @return 模型调用结果
     */
    public <T> T execute(String configKey, ModelGovernanceSettings settings, Supplier<T> supplier) {
        // 1. 根据治理配置逐步包装模型调用
        Supplier<T> decoratedSupplier = decorateRetry(configKey, settings, supplier);
        decoratedSupplier = decorateCircuitBreaker(configKey, settings, decoratedSupplier);
        decoratedSupplier = decorateBulkhead(configKey, settings, decoratedSupplier);
        decoratedSupplier = decorateRateLimiter(configKey, settings, decoratedSupplier);

        // 2. 执行包装后的模型调用
        return decoratedSupplier.get();
    }

    private <T> Supplier<T> decorateRetry(String configKey, ModelGovernanceSettings settings, Supplier<T> supplier) {
        if (settings == null || !Boolean.TRUE.equals(settings.getRetryEnabled())) {
            return supplier;
        }

        // 1. 使用 Resilience4j Retry 构建重试保护
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(defaultIfInvalid(settings.getMaxAttempts(), DEFAULT_MAX_ATTEMPTS))
                .waitDuration(Duration.ofMillis(defaultIfNegative(settings.getRetryWaitMs(), DEFAULT_RETRY_WAIT_MS)))
                .build();
        Retry retry = Retry.of("model-" + configKey, retryConfig);
        return Retry.decorateSupplier(retry, supplier);
    }

    private <T> Supplier<T> decorateCircuitBreaker(String configKey, ModelGovernanceSettings settings,
                                                   Supplier<T> supplier) {
        if (settings == null || !Boolean.TRUE.equals(settings.getCircuitEnabled())) {
            return supplier;
        }

        // 1. 使用 Resilience4j CircuitBreaker 构建熔断保护
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(defaultIfInvalid(settings.getFailureRateThreshold(),
                        DEFAULT_FAILURE_RATE_THRESHOLD))
                .slowCallRateThreshold(defaultIfInvalid(settings.getSlowCallRateThreshold(),
                        DEFAULT_SLOW_CALL_RATE_THRESHOLD))
                .slowCallDurationThreshold(Duration.ofMillis(defaultIfInvalid(settings.getSlowCallDurationMs(),
                        DEFAULT_SLOW_CALL_DURATION_MS)))
                .minimumNumberOfCalls(defaultIfInvalid(settings.getMinimumNumberOfCalls(),
                        DEFAULT_MINIMUM_NUMBER_OF_CALLS))
                .slidingWindowSize(defaultIfInvalid(settings.getSlidingWindowSize(), DEFAULT_SLIDING_WINDOW_SIZE))
                .waitDurationInOpenState(Duration.ofMillis(defaultIfInvalid(settings.getWaitDurationInOpenStateMs(),
                        DEFAULT_WAIT_DURATION_IN_OPEN_STATE_MS)))
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("model-" + configKey, circuitBreakerConfig);
        return CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
    }

    private <T> Supplier<T> decorateBulkhead(String configKey, ModelGovernanceSettings settings, Supplier<T> supplier) {
        if (settings == null || !Boolean.TRUE.equals(settings.getBulkheadEnabled())) {
            return supplier;
        }

        // 1. 使用 Resilience4j Bulkhead 构建并发隔离保护
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(defaultIfInvalid(settings.getMaxConcurrentCalls(), DEFAULT_MAX_CONCURRENT_CALLS))
                .maxWaitDuration(Duration.ofMillis(defaultIfNegative(settings.getMaxWaitDurationMs(),
                        DEFAULT_MAX_WAIT_DURATION_MS)))
                .build();
        Bulkhead bulkhead = Bulkhead.of("model-" + configKey, bulkheadConfig);
        return Bulkhead.decorateSupplier(bulkhead, supplier);
    }

    private <T> Supplier<T> decorateRateLimiter(String configKey, ModelGovernanceSettings settings,
                                                Supplier<T> supplier) {
        if (settings == null || !Boolean.TRUE.equals(settings.getRateLimitEnabled())) {
            return supplier;
        }

        // 1. 使用 Resilience4j RateLimiter 构建限流保护
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                .limitForPeriod(defaultIfInvalid(settings.getLimitForPeriod(), DEFAULT_LIMIT_FOR_PERIOD))
                .limitRefreshPeriod(Duration.ofMillis(defaultIfInvalid(settings.getLimitRefreshPeriodMs(),
                        DEFAULT_LIMIT_REFRESH_PERIOD_MS)))
                .timeoutDuration(Duration.ofMillis(defaultIfNegative(settings.getTimeoutDurationMs(),
                        DEFAULT_TIMEOUT_DURATION_MS)))
                .build();
        RateLimiter rateLimiter = RateLimiter.of("model-" + configKey, rateLimiterConfig);
        return RateLimiter.decorateSupplier(rateLimiter, supplier);
    }

    private int defaultIfInvalid(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private int defaultIfNegative(Integer value, int defaultValue) {
        return value == null || value < 0 ? defaultValue : value;
    }
}
