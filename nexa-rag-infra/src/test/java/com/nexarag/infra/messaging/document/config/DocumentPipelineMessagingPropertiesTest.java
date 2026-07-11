package com.nexarag.infra.messaging.document.config;

import com.nexarag.infra.messaging.document.enums.DocumentPipelineMessagingType;
import com.nexarag.infra.messaging.document.enums.DocumentPipelinePublishMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档流水线消息配置属性测试，验证默认消息参数符合约定。
 */
class DocumentPipelineMessagingPropertiesTest {

    @Test
    void shouldProvideDefaultMessagingProperties() {
        // 1. 创建未绑定外部配置的消息属性
        DocumentPipelineMessagingProperties properties = new DocumentPipelineMessagingProperties();

        // 2. 验证消息中间件与发布模式默认值
        assertThat(properties.getType()).isEqualTo(DocumentPipelineMessagingType.ROCKETMQ);
        assertThat(properties.getPublishMode()).isEqualTo(DocumentPipelinePublishMode.OUTBOX);

        // 3. 验证主题、消费组与重试次数默认值
        assertThat(properties.getTopic()).isEqualTo("nexa-document-pipeline");
        assertThat(properties.getFailureTopic()).isEqualTo("nexa-document-pipeline-failure");
        assertThat(properties.getConsumerGroup()).isEqualTo("nexa-document-pipeline-worker");
        assertThat(properties.getFailureConsumerGroup()).isEqualTo("nexa-document-pipeline-failure-handler");
        assertThat(properties.getMaxReconsumeTimes()).isEqualTo(5);
    }
}
