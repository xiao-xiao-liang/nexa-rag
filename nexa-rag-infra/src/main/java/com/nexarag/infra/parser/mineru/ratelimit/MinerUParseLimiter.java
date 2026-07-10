package com.nexarag.infra.parser.mineru.ratelimit;

import java.util.function.Supplier;

/**
 * MinerU 解析限流器，负责在真正调用 MinerU 前后包裹并发许可控制。
 */
public interface MinerUParseLimiter {

    /**
     * 在 MinerU 解析许可内执行解析动作。
     *
     * @param documentId 文档ID
     * @param action     解析动作
     * @param <T>        解析结果类型
     * @return 解析结果
     */
    <T> T execute(Long documentId, Supplier<T> action);
}
