package com.nexarag.infra.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 文档任务消息配置，维护索引清理和最终失败告警的独立 Topic 与消费者组。
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "nexa.document.task")
public class DocumentTaskMessagingProperties {

    /** 索引清理任务 Topic。 */
    @NotBlank(message = "索引清理任务Topic不能为空")
    private String cleanupTopic = "nexa-document-index-cleanup";

    /** 索引清理消费者组。 */
    @NotBlank(message = "索引清理消费者组不能为空")
    private String cleanupConsumerGroup = "nexa-document-index-cleanup-worker";

    /** RocketMQ 最大重新消费次数。 */
    @Min(value = 0, message = "文档任务最大重新消费次数不能小于0")
    private int maxReconsumeTimes = 5;
}
