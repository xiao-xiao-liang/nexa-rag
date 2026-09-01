package com.nexarag.retrieval.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * 对话检索阻塞 I/O 的虚拟线程执行器配置。
 */
@Configuration
public class RetrievalTaskExecutorConfiguration {

    /**
     * 创建受并发上限保护的虚拟线程执行器。
     *
     * @param maxConcurrency 允许同时访问外部检索系统的最大任务数
     * @return 对话检索异步执行器
     */
    @Bean("retrievalTaskExecutor")
    public AsyncTaskExecutor retrievalTaskExecutor(
            @Value("${nexa.retrieval.execution.max-concurrency:32}") int maxConcurrency) {
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("检索虚拟线程最大并发数必须大于0");
        }
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("retrieval-vt-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(maxConcurrency);
        return executor;
    }
}
