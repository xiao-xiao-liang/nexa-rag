package com.nexarag.infra.queue.document;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档流水线队列内存实现测试，用于验证公平队列基础契约。
 */
class InMemoryDocumentPipelineQueueTest {

    @Test
    void pollShouldFollowSuccessfulEnqueueOrder() {
        InMemoryDocumentPipelineQueue queue = new InMemoryDocumentPipelineQueue();

        queue.enqueue(10L);
        queue.enqueue(20L);
        queue.enqueue(30L);

        assertThat(queue.poll("worker-1", Duration.ofMinutes(5)).orElseThrow().documentId()).isEqualTo(10L);
        assertThat(queue.poll("worker-1", Duration.ofMinutes(5)).orElseThrow().documentId()).isEqualTo(20L);
        assertThat(queue.poll("worker-1", Duration.ofMinutes(5)).orElseThrow().documentId()).isEqualTo(30L);
    }

    @Test
    void enqueueShouldReturnExistingStatusWhenDocumentAlreadyWaiting() {
        InMemoryDocumentPipelineQueue queue = new InMemoryDocumentPipelineQueue();

        DocumentPipelineQueueStatus firstStatus = queue.enqueue(10L);
        DocumentPipelineQueueStatus secondStatus = queue.enqueue(10L);

        assertThat(firstStatus.queuePosition()).isEqualTo(1);
        assertThat(secondStatus.queuePosition()).isEqualTo(1);
        assertThat(secondStatus.waitingCount()).isEqualTo(1);
    }

    @Test
    void ackShouldRemoveRunningTaskWhenLeaseMatches() {
        InMemoryDocumentPipelineQueue queue = new InMemoryDocumentPipelineQueue();
        queue.enqueue(10L);
        DocumentPipelineTask task = queue.poll("worker-1", Duration.ofMinutes(5)).orElseThrow();

        queue.ack(task.documentId(), task.leaseToken());

        assertThat(queue.queryStatus(10L)).isEmpty();
    }

    @Test
    void releaseShouldRequeueTaskToTailWhenLeaseMatches() {
        InMemoryDocumentPipelineQueue queue = new InMemoryDocumentPipelineQueue();
        queue.enqueue(10L);
        queue.enqueue(20L);
        DocumentPipelineTask failedTask = queue.poll("worker-1", Duration.ofMinutes(5)).orElseThrow();

        queue.release(failedTask.documentId(), failedTask.leaseToken(), true);

        assertThat(queue.poll("worker-1", Duration.ofMinutes(5)).orElseThrow().documentId()).isEqualTo(20L);
        assertThat(queue.poll("worker-1", Duration.ofMinutes(5)).orElseThrow().documentId()).isEqualTo(10L);
    }
}