package com.nexarag.infra.ratelimit;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 分布式可过期许可限流器，负责封装 Redisson 信号量的初始化、获取和释放。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedPermitLimiter {

    private final RedissonClient redissonClient;

    /**
     * 初始化分布式信号量许可数量。
     *
     * @param semaphoreName 信号量名称
     * @param maxPermits    最大许可数量
     */
    public void initialize(String semaphoreName, int maxPermits) {
        validateSemaphoreName(semaphoreName);
        if (maxPermits <= 0) {
            throw new ServiceException("分布式许可数量必须大于0，semaphoreName=" + semaphoreName);
        }

        // 1. 使用 Redisson 可过期信号量维护跨实例并发许可
        RPermitExpirableSemaphore semaphore = redissonClient.getPermitExpirableSemaphore(semaphoreName);
        semaphore.setPermits(maxPermits);
        log.info("初始化分布式许可限流器完成，semaphoreName={}，maxPermits={}", semaphoreName, maxPermits);
    }

    /**
     * 获取分布式许可。
     *
     * @param semaphoreName  信号量名称
     * @param maxWaitSeconds 最大等待秒数
     * @param leaseSeconds   许可自动释放秒数
     * @return 许可ID；获取超时时返回空
     */
    public Optional<String> acquire(String semaphoreName, int maxWaitSeconds, int leaseSeconds) {
        validateSemaphoreName(semaphoreName);
        if (maxWaitSeconds < 0 || leaseSeconds <= 0) {
            throw new ServiceException("分布式许可等待时间或租约时间不合法，semaphoreName=" + semaphoreName);
        }

        // 1. 在限定等待时间内尝试获取可过期许可
        try {
            RPermitExpirableSemaphore semaphore = redissonClient.getPermitExpirableSemaphore(semaphoreName);
            return Optional.ofNullable(semaphore.tryAcquire(maxWaitSeconds, leaseSeconds, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServiceException("获取分布式许可被中断，semaphoreName=" + semaphoreName,
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 释放分布式许可。
     *
     * @param semaphoreName 信号量名称
     * @param permitId      许可ID
     */
    public void release(String semaphoreName, String permitId) {
        validateSemaphoreName(semaphoreName);
        if (!StringUtils.hasText(permitId)) {
            return;
        }

        // 1. 释放许可；如果许可已自动过期，仅记录告警用于排查
        RPermitExpirableSemaphore semaphore = redissonClient.getPermitExpirableSemaphore(semaphoreName);
        boolean released = semaphore.tryRelease(permitId);
        if (!released) {
            log.warn("分布式许可已过期或已释放，semaphoreName={}，permitId={}", semaphoreName, permitId);
        }
    }

    private void validateSemaphoreName(String semaphoreName) {
        if (!StringUtils.hasText(semaphoreName)) {
            throw new ServiceException("分布式信号量名称不能为空");
        }
    }
}
