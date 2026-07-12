package com.nexarag.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;

import java.util.concurrent.Semaphore;

import static com.nexarag.chat.constants.ChatContextConstants.SUMMARY_EXECUTOR_NAME;
import static com.nexarag.chat.constants.ChatContextConstants.SUMMARY_MAX_CONCURRENCY;

/**
 * 会话摘要异步任务执行器配置。
 */
@Configuration
public class ExecutorConfiguration {

    /**
     * 创建虚拟线程执行器，承载模型摘要的阻塞调用。
     *
     * @return 虚拟线程执行器
     */
    @Bean(name = SUMMARY_EXECUTOR_NAME)
    public TaskExecutor chatSummaryExecutor() {
        return new VirtualThreadTaskExecutor("chat-summary-");
    }

    /**
     * 创建摘要任务并发控制器。
     *
     * @return 摘要任务并发控制器
     */
    @Bean
    public Semaphore chatSummarySemaphore() {
        return new Semaphore(SUMMARY_MAX_CONCURRENCY);
    }
}
