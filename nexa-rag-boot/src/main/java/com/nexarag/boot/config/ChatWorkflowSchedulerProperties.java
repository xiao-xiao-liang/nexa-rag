package com.nexarag.boot.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 对话工作流专用调度器配置，限制长连接模型调用对 Web 请求线程的影响。
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "nexa.chat.workflow.scheduler")
public class ChatWorkflowSchedulerProperties {

    /** 工作流最大并发线程数。 */
    @Min(1)
    private int maxConcurrency = 16;

    /** 工作流等待队列容量。 */
    @Min(1)
    private int queueCapacity = 1_000;

    /** 调度器线程名称前缀。 */
    private String threadNamePrefix = "chat-workflow";
}
