package com.nexarag.infra.queue.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 文档流水线队列 Lua 脚本测试。
 */
class RedisDocumentPipelineQueueScriptTest {

    @Test
    void enqueueScriptShouldUseRedisSequenceAndZsetAtomically() {
        assertThat(RedisDocumentPipelineQueue.ENQUEUE_SCRIPT).contains("INCR");
        assertThat(RedisDocumentPipelineQueue.ENQUEUE_SCRIPT).contains("ZADD");
        assertThat(RedisDocumentPipelineQueue.ENQUEUE_SCRIPT).contains("ZRANK");
    }

    @Test
    void pollScriptShouldMoveWaitingTaskToRunningAtomically() {
        assertThat(RedisDocumentPipelineQueue.POLL_SCRIPT).contains("ZRANGE");
        assertThat(RedisDocumentPipelineQueue.POLL_SCRIPT).contains("ZREM");
        assertThat(RedisDocumentPipelineQueue.POLL_SCRIPT).contains("HSET");
        assertThat(RedisDocumentPipelineQueue.POLL_SCRIPT).contains("SET");
    }

    @Test
    void ackAndReleaseScriptsShouldValidateLeaseTokenBeforeChangingState() {
        assertThat(RedisDocumentPipelineQueue.ACK_SCRIPT).contains("GET");
        assertThat(RedisDocumentPipelineQueue.ACK_SCRIPT).contains("HDEL");
        assertThat(RedisDocumentPipelineQueue.RELEASE_SCRIPT).contains("GET");
        assertThat(RedisDocumentPipelineQueue.RELEASE_SCRIPT).contains("INCR");
        assertThat(RedisDocumentPipelineQueue.RELEASE_SCRIPT).contains("ZADD");
    }
}