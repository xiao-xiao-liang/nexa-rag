package com.nexarag.infra.queue.document;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 文档流水线队列真实 Redis 集成测试，默认跳过，显式开启后验证完整排队流程。
 */
class RedisDocumentPipelineQueueIntegrationTest {

    private LettuceConnectionFactory connectionFactory;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(Boolean.getBoolean("nexa.redis.integration.enabled"), "未开启真实 Redis 集成测试");
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void redisQueueShouldCompleteRealFifoLeaseAckAndReleaseFlow() {
        RedisDocumentPipelineQueue queue = createQueueWithDedicatedPrefix();

        // 1. 入队三条文档任务，验证成功入队顺序决定等待位置
        DocumentPipelineQueueStatus firstStatus = queue.enqueue(1001L);
        DocumentPipelineQueueStatus secondStatus = queue.enqueue(1002L);
        DocumentPipelineQueueStatus thirdStatus = queue.enqueue(1003L);

        assertThat(firstStatus.queuePosition()).isEqualTo(1);
        assertThat(secondStatus.queuePosition()).isEqualTo(2);
        assertThat(thirdStatus.queuePosition()).isEqualTo(3);
        assertThat(thirdStatus.waitingCount()).isEqualTo(3);

        // 2. 获取并确认第一条任务，验证 ack 会清理运行态
        DocumentPipelineTask firstTask = queue.poll("integration-worker", Duration.ofMinutes(5)).orElseThrow();
        assertThat(firstTask.documentId()).isEqualTo(1001L);
        queue.ack(firstTask.documentId(), firstTask.leaseToken());
        assertThat(queue.queryStatus(1001L)).isEmpty();

        // 3. 释放第二条任务并重新入队，验证失败任务回到队尾而不是插队
        DocumentPipelineTask secondTask = queue.poll("integration-worker", Duration.ofMinutes(5)).orElseThrow();
        assertThat(secondTask.documentId()).isEqualTo(1002L);
        queue.release(secondTask.documentId(), secondTask.leaseToken(), true);

        DocumentPipelineTask thirdTask = queue.poll("integration-worker", Duration.ofMinutes(5)).orElseThrow();
        DocumentPipelineTask requeuedSecondTask = queue.poll("integration-worker", Duration.ofMinutes(5)).orElseThrow();

        assertThat(thirdTask.documentId()).isEqualTo(1003L);
        assertThat(requeuedSecondTask.documentId()).isEqualTo(1002L);

        // 4. 确认剩余任务，避免测试前缀下残留运行态租约
        queue.ack(thirdTask.documentId(), thirdTask.leaseToken());
        queue.ack(requeuedSecondTask.documentId(), requeuedSecondTask.leaseToken());
    }

    private RedisDocumentPipelineQueue createQueueWithDedicatedPrefix() {
        // 1. 创建真实 Redis 连接，密码只从运行时参数读取，不写入仓库
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                System.getProperty("nexa.redis.host", "192.168.0.134"),
                Integer.getInteger("nexa.redis.port", 6379));
        String password = System.getProperty("nexa.redis.password", System.getenv("NEXA_REDIS_PASSWORD"));
        if (password != null && !password.isBlank()) {
            configuration.setPassword(RedisPassword.of(password));
        }
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();

        // 2. 使用独立测试前缀，避免污染业务队列
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        DocumentPipelineQueueProperties properties = new DocumentPipelineQueueProperties();
        properties.setKeyPrefix("nexa:test:document:pipeline:" + System.currentTimeMillis());
        DocumentPipelineQueueKeys keys = new DocumentPipelineQueueKeys(properties);
        return new RedisDocumentPipelineQueue(redisTemplate, keys);
    }
}
