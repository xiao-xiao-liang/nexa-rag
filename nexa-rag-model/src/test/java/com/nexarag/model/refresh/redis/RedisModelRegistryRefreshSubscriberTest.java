package com.nexarag.model.refresh.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.model.enums.ModelRefreshChannel;
import com.nexarag.model.refresh.ModelRegistryChangeListener;
import com.nexarag.model.refresh.ModelRegistryChangedMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Redis 模型注册表刷新消息订阅器测试。
 */
class RedisModelRegistryRefreshSubscriberTest {

    @Test
    void onMessageShouldDeserializeAndNotifyListener() throws Exception {
        RecordingModelRegistryChangeListener listener = new RecordingModelRegistryChangeListener();
        ObjectMapper objectMapper = new ObjectMapper();
        RedisModelRegistryRefreshSubscriber subscriber =
                new RedisModelRegistryRefreshSubscriber(objectMapper, listener);
        String message = objectMapper.writeValueAsString(
                new ModelRegistryChangedMessage(9L, ModelRefreshChannel.PUB_SUB));

        subscriber.onMessage(message);

        assertThat(listener.message.versionNo()).isEqualTo(9L);
        assertThat(listener.message.channel()).isEqualTo(ModelRefreshChannel.PUB_SUB);
    }

    @Test
    void onMessageShouldNotThrowWhenPayloadInvalid() {
        RecordingModelRegistryChangeListener listener = new RecordingModelRegistryChangeListener();
        RedisModelRegistryRefreshSubscriber subscriber =
                new RedisModelRegistryRefreshSubscriber(new ObjectMapper(), listener);

        assertThatCode(() -> subscriber.onMessage("bad json")).doesNotThrowAnyException();

        assertThat(listener.message).isNull();
    }

    private static class RecordingModelRegistryChangeListener implements ModelRegistryChangeListener {

        private ModelRegistryChangedMessage message;

        @Override
        public void onMessage(ModelRegistryChangedMessage message) {
            this.message = message;
        }
    }
}
