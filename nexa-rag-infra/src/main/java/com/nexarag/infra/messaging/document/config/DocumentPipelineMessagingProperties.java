package com.nexarag.infra.messaging.document.config;

import com.nexarag.infra.messaging.document.enums.DocumentPipelineMessagingType;
import com.nexarag.infra.messaging.document.enums.DocumentPipelinePublishMode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文档流水线消息配置属性，负责维护消息类型、发布模式、主题、消费组和重试参数。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "nexa.document.pipeline.messaging")
public class DocumentPipelineMessagingProperties {

    /**
     * 消息中间件类型。
     */
    private DocumentPipelineMessagingType type = DocumentPipelineMessagingType.ROCKETMQ;

    /**
     * 消息发布模式。
     */
    private DocumentPipelinePublishMode publishMode = DocumentPipelinePublishMode.OUTBOX;

    /**
     * 文档流水线任务主题。
     */
    private String topic = "nexa-document-pipeline";

    /**
     * 文档流水线失败消息主题。
     */
    private String failureTopic = "nexa-document-pipeline-failure";

    /**
     * 文档流水线工作消费者组。
     */
    private String consumerGroup = "nexa-document-pipeline-worker";

    /**
     * 文档流水线失败消息处理消费者组。
     */
    private String failureConsumerGroup = "nexa-document-pipeline-failure-handler";

    /**
     * 消息最大重新消费次数。
     */
    private int maxReconsumeTimes = 5;
}
