package com.nexarag.boot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * 注册对话工作流专用的有界调度器，隔离模型流和其他阻塞任务。
 */
@Configuration
public class ChatWorkflowSchedulerConfiguration {

    /**
     * 创建对话工作流调度器。
     *
     * @param properties 调度器容量配置
     * @return 对话工作流专用调度器
     */
    @Bean(destroyMethod = "dispose")
    public Scheduler chatWorkflowScheduler(ChatWorkflowSchedulerProperties properties) {
        return Schedulers.newBoundedElastic(properties.getMaxConcurrency(), properties.getQueueCapacity(),
                properties.getThreadNamePrefix());
    }
}
