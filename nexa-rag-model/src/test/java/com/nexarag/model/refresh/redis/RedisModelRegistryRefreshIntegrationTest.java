package com.nexarag.model.refresh.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.model.refresh.DefaultModelRegistryChangeListener;
import com.nexarag.model.refresh.ModelRegistryChangedMessage;
import com.nexarag.model.registry.ModelRegistryRefresher;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Redis Pub/Sub 模型注册表刷新集成测试。
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisModelRegistryRefreshIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Test
    void redisPubSubShouldNotifyAnotherInstanceToRefresh() throws Exception {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig());
        factory.afterPropertiesSet();
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);

        CountDownLatch latch = new CountDownLatch(1);
        ModelRegistryRefresher refresher = mock(ModelRegistryRefresher.class);
        RedisModelRegistryRefreshSubscriber subscriber = new RedisModelRegistryRefreshSubscriber(
                new ObjectMapper(),
                message -> {
                    new DefaultModelRegistryChangeListener(refresher).onMessage(message);
                    latch.countDown();
                });
        container.addMessageListener((message, pattern) -> subscriber.onMessage(
                new String(message.getBody(), StandardCharsets.UTF_8)), new ChannelTopic("nexa.model.registry.changed"));
        container.afterPropertiesSet();
        container.start();

        RedisModelRegistryRefreshMessageClient publisher =
                new RedisModelRegistryRefreshMessageClient(new StringRedisTemplate(factory), new ObjectMapper());
        publisher.publish("nexa.model.registry.changed", new ModelRegistryChangedMessage(10L));

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        verify(refresher).refreshIfNewer(10L);

        container.stop();
        factory.destroy();
    }

    private RedisStandaloneConfiguration redisConfig() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName(REDIS.getHost());
        configuration.setPort(REDIS.getMappedPort(6379));
        return configuration;
    }
}
