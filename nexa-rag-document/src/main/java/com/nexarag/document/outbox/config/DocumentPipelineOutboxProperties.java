package com.nexarag.document.outbox.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 文档流水线Outbox发布配置，控制批量扫描、抢占超时和发布重试策略。
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "nexa.document.pipeline.outbox")
public class DocumentPipelineOutboxProperties {

    /**
     * 单次扫描最大消息数量。
     */
    @Min(value = 1, message = "Outbox单次扫描数量必须大于0")
    private int batchSize = 100;

    /**
     * 发布任务轮询间隔，单位：毫秒。
     */
    @Min(value = 100, message = "Outbox轮询间隔不能小于100毫秒")
    private long pollIntervalMs = 1000L;

    /**
     * 发布中状态的抢占超时时间，单位：秒。
     */
    @Min(value = 1, message = "Outbox抢占超时时间必须大于0秒")
    private long publishingTimeoutSeconds = 60L;

    /**
     * 消息发布最大尝试次数。
     */
    @Min(value = 1, message = "Outbox最大发布次数必须大于0")
    private int maxPublishRetries = 10;

    /**
     * 首次发布重试延迟，单位：秒。
     */
    @Min(value = 1, message = "Outbox首次重试延迟必须大于0秒")
    private long initialRetryDelaySeconds = 5L;

    /**
     * 发布重试最大延迟，单位：秒。
     */
    @Min(value = 1, message = "Outbox最大重试延迟必须大于0秒")
    private long maxRetryDelaySeconds = 300L;
}
