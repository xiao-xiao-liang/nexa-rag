package com.nexarag.infra.messaging.document.config;

import com.nexarag.infra.messaging.document.enums.DocumentPipelineMessagingType;
import com.nexarag.infra.messaging.document.enums.DocumentPipelinePublishMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 文档流水线消息配置属性，负责维护消息类型、发布模式、主题、消费组和重试参数。
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "nexa.document.pipeline.messaging")
public class DocumentPipelineMessagingProperties {

    /**
     * 消息中间件类型。
     */
    @NotNull(message = "消息中间件类型不能为空")
    private DocumentPipelineMessagingType type = DocumentPipelineMessagingType.ROCKETMQ;

    /**
     * 消息发布模式。
     */
    @NotNull(message = "消息发布模式不能为空")
    private DocumentPipelinePublishMode publishMode = DocumentPipelinePublishMode.OUTBOX;

    /**
     * 文档流水线任务主题。
     */
    @NotBlank(message = "文档流水线任务主题不能为空")
    private String topic = "nexa-document-pipeline";

    /**
     * 文档流水线失败消息主题。
     */
    @NotBlank(message = "文档流水线失败消息主题不能为空")
    private String failureTopic = "nexa-document-pipeline-failure";

    /**
     * 文档流水线工作消费者组。
     */
    @NotBlank(message = "文档流水线工作消费者组不能为空")
    private String consumerGroup = "nexa-document-pipeline-worker";

    /**
     * 文档流水线失败消息处理消费者组。
     */
    @NotBlank(message = "文档流水线失败消息处理消费者组不能为空")
    private String failureConsumerGroup = "nexa-document-pipeline-failure-handler";

    /**
     * 消息最大重新消费次数。
     */
    @Min(value = 0, message = "消息最大重新消费次数不能小于0")
    private int maxReconsumeTimes = 5;
}
