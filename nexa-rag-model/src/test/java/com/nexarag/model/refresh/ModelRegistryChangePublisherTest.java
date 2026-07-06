package com.nexarag.model.refresh;

import com.nexarag.model.config.ModelRegistryRefreshProperties;
import com.nexarag.model.enums.ModelRefreshChannel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 模型注册表刷新消息发布器测试。
 */
class ModelRegistryChangePublisherTest {

    @Test
    void publishShouldUseConfiguredChannelAndTopic() {
        ModelRegistryRefreshProperties properties = new ModelRegistryRefreshProperties();
        properties.setRefreshChannel(ModelRefreshChannel.PUB_SUB);
        properties.setRefreshTopic("nexa.model.registry.changed");
        RecordingMessageClient client = new RecordingMessageClient();
        DefaultModelRegistryChangePublisher publisher =
                new DefaultModelRegistryChangePublisher(properties, List.of(client));

        publisher.publish(3L);

        assertThat(client.topic).isEqualTo("nexa.model.registry.changed");
        assertThat(client.message.versionNo()).isEqualTo(3L);
        assertThat(client.message.channel()).isEqualTo(ModelRefreshChannel.PUB_SUB);
    }

    @Test
    void publishShouldNotBlockWhenMessageClientMissing() {
        ModelRegistryRefreshProperties properties = new ModelRegistryRefreshProperties();
        DefaultModelRegistryChangePublisher publisher =
                new DefaultModelRegistryChangePublisher(properties, List.of());

        assertThatCode(() -> publisher.publish(4L)).doesNotThrowAnyException();
    }

    @Test
    void publishShouldNotBlockWhenMessageClientThrowsException() {
        ModelRegistryRefreshProperties properties = new ModelRegistryRefreshProperties();
        properties.setRefreshChannel(ModelRefreshChannel.PUB_SUB);
        DefaultModelRegistryChangePublisher publisher =
                new DefaultModelRegistryChangePublisher(properties, List.of(new FailingMessageClient()));

        assertThatCode(() -> publisher.publish(5L)).doesNotThrowAnyException();
    }

    private static class RecordingMessageClient implements ModelRefreshMessageClient {

        private String topic;
        private ModelRegistryChangedMessage message;

        @Override
        public ModelRefreshChannel channel() {
            return ModelRefreshChannel.PUB_SUB;
        }

        @Override
        public void publish(String topic, ModelRegistryChangedMessage message) {
            this.topic = topic;
            this.message = message;
        }
    }

    private static class FailingMessageClient implements ModelRefreshMessageClient {

        @Override
        public ModelRefreshChannel channel() {
            return ModelRefreshChannel.PUB_SUB;
        }

        @Override
        public void publish(String topic, ModelRegistryChangedMessage message) {
            throw new IllegalStateException("模拟发布失败");
        }
    }
}
