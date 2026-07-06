package com.nexarag.model.refresh.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.model.enums.ModelRefreshChannel;
import com.nexarag.model.refresh.ModelRegistryChangedMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Redis 模型注册表刷新消息客户端测试。
 */
class RedisModelRegistryRefreshMessageClientTest {

    @Test
    void publishShouldSerializeMessageAndSendToRedisTopic() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RedisModelRegistryRefreshMessageClient client =
                new RedisModelRegistryRefreshMessageClient(redisTemplate, objectMapper);

        client.publish("nexa.model.registry.changed",
                new ModelRegistryChangedMessage(8L, ModelRefreshChannel.PUB_SUB));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("nexa.model.registry.changed"),
                messageCaptor.capture()
        );
        ModelRegistryChangedMessage actual =
                objectMapper.readValue(messageCaptor.getValue(), ModelRegistryChangedMessage.class);
        assertThat(actual.versionNo()).isEqualTo(8L);
        assertThat(actual.channel()).isEqualTo(ModelRefreshChannel.PUB_SUB);
        assertThat(client.channel()).isEqualTo(ModelRefreshChannel.PUB_SUB);
    }
}
