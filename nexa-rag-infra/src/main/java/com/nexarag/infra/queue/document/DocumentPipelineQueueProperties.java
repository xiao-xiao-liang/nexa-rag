package com.nexarag.infra.queue.document;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文档流水线队列配置属性，负责维护 Redis key 前缀和租约时间。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "nexa.document.pipeline.queue")
public class DocumentPipelineQueueProperties {

    /**
     * Redis key 前缀。
     */
    private String keyPrefix = "nexa:document:pipeline";

    /**
     * 任务租约秒数。
     */
    private long leaseTtlSeconds = 300L;

    /**
     * 运行态重试副本保留秒数。
     */
    private long retryTtlSeconds = 86400L;
}
