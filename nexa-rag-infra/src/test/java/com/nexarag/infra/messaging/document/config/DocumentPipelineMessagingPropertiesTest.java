package com.nexarag.infra.messaging.document.config;

import com.nexarag.infra.messaging.document.enums.DocumentPipelineMessagingType;
import com.nexarag.infra.messaging.document.enums.DocumentPipelinePublishMode;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void shouldBindKebabCasePropertiesAndConvertEnums() {
        // 1. 清空枚举默认值，避免未绑定时产生伪通过
        DocumentPipelineMessagingProperties properties = new DocumentPipelineMessagingProperties();
        properties.setType(null);
        properties.setPublishMode(null);

        // 2. 使用完整配置前缀和 kebab-case 属性执行绑定
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "nexa.document.pipeline.messaging.type", "rocketmq",
                "nexa.document.pipeline.messaging.publish-mode", "outbox",
                "nexa.document.pipeline.messaging.failure-topic", "custom-failure-topic",
                "nexa.document.pipeline.messaging.consumer-group", "custom-consumer-group",
                "nexa.document.pipeline.messaging.failure-consumer-group", "custom-failure-consumer-group",
                "nexa.document.pipeline.messaging.max-reconsume-times", "8"));
        new Binder(source).bind(
                "nexa.document.pipeline.messaging",
                Bindable.ofInstance(properties));

        // 3. 验证枚举转换和 kebab-case 属性绑定结果
        assertThat(properties.getType()).isEqualTo(DocumentPipelineMessagingType.ROCKETMQ);
        assertThat(properties.getPublishMode()).isEqualTo(DocumentPipelinePublishMode.OUTBOX);
        assertThat(properties.getFailureTopic()).isEqualTo("custom-failure-topic");
        assertThat(properties.getConsumerGroup()).isEqualTo("custom-consumer-group");
        assertThat(properties.getFailureConsumerGroup()).isEqualTo("custom-failure-consumer-group");
        assertThat(properties.getMaxReconsumeTimes()).isEqualTo(8);
    }

    @Test
    void shouldRejectBlankTopicWhenBinding() {
        // 1. 准备空白主题配置
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "nexa.document.pipeline.messaging.topic", " "));

        // 2. 绑定并执行配置校验
        assertThatThrownBy(() -> bindWithValidation(source))
                // 3. 验证空白主题导致绑定失败
                .isInstanceOf(BindException.class);
    }

    @Test
    void shouldRejectNegativeMaxReconsumeTimesWhenBinding() {
        // 1. 准备负数最大重新消费次数配置
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "nexa.document.pipeline.messaging.max-reconsume-times", "-1"));

        // 2. 绑定并执行配置校验
        assertThatThrownBy(() -> bindWithValidation(source))
                // 3. 验证负数最大重新消费次数导致绑定失败
                .isInstanceOf(BindException.class);
    }

    @Test
    void shouldRejectBlankMessagingTypeWhenBinding() {
        // 1. 清空默认消息中间件类型并准备空白配置
        DocumentPipelineMessagingProperties properties = new DocumentPipelineMessagingProperties();
        properties.setType(null);
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "nexa.document.pipeline.messaging.type", ""));

        // 2. 绑定并执行配置校验
        assertThatThrownBy(() -> bindWithValidation(source, properties))
                // 3. 验证空白消息中间件类型触发非空校验
                .isInstanceOf(BindException.class)
                .hasCauseInstanceOf(BindValidationException.class);
    }

    @Test
    void shouldRejectBlankPublishModeWhenBinding() {
        // 1. 清空默认消息发布模式并准备空白配置
        DocumentPipelineMessagingProperties properties = new DocumentPipelineMessagingProperties();
        properties.setPublishMode(null);
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "nexa.document.pipeline.messaging.publish-mode", ""));

        // 2. 绑定并执行配置校验
        assertThatThrownBy(() -> bindWithValidation(source, properties))
                // 3. 验证空白消息发布模式触发非空校验
                .isInstanceOf(BindException.class)
                .hasCauseInstanceOf(BindValidationException.class);
    }

    /**
     * 使用 Jakarta Bean Validation 校验器绑定文档流水线消息配置。
     *
     * @param source 配置属性源
     */
    private void bindWithValidation(MapConfigurationPropertySource source) {
        // 1. 使用默认配置对象执行绑定校验
        bindWithValidation(source, new DocumentPipelineMessagingProperties());
    }

    /**
     * 使用 Jakarta Bean Validation 校验器绑定到指定文档流水线消息配置对象。
     *
     * @param source 配置属性源
     * @param properties 待绑定配置对象
     */
    private void bindWithValidation(
            MapConfigurationPropertySource source,
            DocumentPipelineMessagingProperties properties) {
        // 1. 创建 Jakarta Bean Validation 校验器
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            SpringValidatorAdapter validator = new SpringValidatorAdapter(validatorFactory.getValidator());

            // 2. 绑定配置并执行约束校验
            new Binder(source).bind(
                    "nexa.document.pipeline.messaging",
                    Bindable.ofInstance(properties),
                    new ValidationBindHandler(validator));
        }
    }
}
