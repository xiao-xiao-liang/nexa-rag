package com.nexarag.auth.config;

import com.nexarag.auth.constants.SecurityNotificationConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 安全邮件通知的专用异步执行器配置，避免 SMTP 阻塞对话流和业务请求线程。
 */
@Configuration
public class SecurityNotificationExecutorConfiguration {

    /**
     * 创建受限的安全通知执行器。
     *
     * @return 安全通知专用执行器
     */
    @Bean(name = SecurityNotificationConstants.EXECUTOR_NAME)
    public TaskExecutor securityNotificationExecutor() {
        // 1. 使用独立且有界的线程池隔离可能阻塞的 SMTP 调用。
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("security-notify-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
