package com.nexarag.document.service.impl;

import com.nexarag.document.entity.DocumentQueueInfo;
import com.nexarag.infra.queue.document.DocumentPipelineQueue;
import com.nexarag.infra.queue.document.DocumentPipelineQueueStatus;
import com.nexarag.infra.queue.document.DocumentPipelineTask;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 文档处理任务分发器测试。
 */
class RedisDocumentProcessTaskDispatcherTest {

    @Test
    void enqueueShouldMapRedisQueueStatusToDocumentQueueInfo() {
        FakeDocumentPipelineQueue queue = new FakeDocumentPipelineQueue(
                new DocumentPipelineQueueStatus(1L, 2, 5, false, null, null, 100L));
        RedisDocumentProcessTaskDispatcher dispatcher = new RedisDocumentProcessTaskDispatcher(queue);

        DocumentQueueInfo result = dispatcher.enqueue(1L);

        assertThat(result.queuePosition()).isEqualTo(2);
        assertThat(result.waitingCount()).isEqualTo(5);
        assertThat(result.running()).isFalse();
        assertThat(result.workerId()).isNull();
        assertThat(result.leaseTtlSeconds()).isNull();
    }

    private record FakeDocumentPipelineQueue(DocumentPipelineQueueStatus status) implements DocumentPipelineQueue {

        @Override
        public DocumentPipelineQueueStatus enqueue(Long documentId) {
            return status;
        }

        @Override
        public Optional<DocumentPipelineTask> poll(String workerId, Duration leaseTtl) {
            return Optional.empty();
        }

        @Override
        public void ack(Long documentId, String leaseToken) {
        }

        @Override
        public void release(Long documentId, String leaseToken, boolean requeue) {
        }

        @Override
        public Optional<DocumentPipelineQueueStatus> queryStatus(Long documentId) {
            return Optional.empty();
        }
    }
}