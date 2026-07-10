package com.nexarag.infra.parser.mineru.ratelimit;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.config.MinerUProperties;
import com.nexarag.infra.ratelimit.DistributedPermitLimiter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Redisson MinerU 解析限流器，负责限制跨实例 MinerU 并发解析任务数。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.parser.mineru", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedissonMinerUParseLimiter implements MinerUParseLimiter {

    private final MinerUProperties properties;
    private final DistributedPermitLimiter permitLimiter;

    /**
     * 初始化 MinerU 解析信号量。
     */
    @PostConstruct
    public void initialize() {
        permitLimiter.initialize(properties.getSemaphoreName(), properties.getConcurrencyLimit());
        log.info("MinerU解析限流初始化完成，semaphoreName={}，maxConcurrent={}",
                properties.getSemaphoreName(), properties.getConcurrencyLimit());
    }

    /**
     * 在 MinerU 解析许可内执行解析动作。
     *
     * @param documentId 文档ID
     * @param action     解析动作
     * @param <T>        解析结果类型
     * @return 解析结果
     */
    @Override
    public <T> T execute(Long documentId, Supplier<T> action) {
        // 1. 获取跨实例 MinerU 解析许可
        Optional<String> permitId = permitLimiter.acquire(properties.getSemaphoreName(),
                properties.getMaxWaitSeconds(), properties.getLeaseSeconds());
        if (permitId.isEmpty()) {
            throw new ServiceException("MinerU解析任务过多，请稍后重试，documentId=" + documentId,
                    BaseErrorCode.SERVICE_ERROR);
        }

        try {
            // 2. 在许可保护内执行实际解析动作
            return action.get();
        } finally {
            // 3. 无论解析成功或失败，都释放 MinerU 解析许可
            permitLimiter.release(properties.getSemaphoreName(), permitId.get());
        }
    }
}
