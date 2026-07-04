package com.nexarag.boot.worker;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.nexarag.document.service.DocumentPipelineExecutor;
import com.nexarag.infra.queue.document.DocumentPipelineQueue;
import com.nexarag.infra.queue.document.DocumentPipelineQueueStatus;
import com.nexarag.infra.queue.document.DocumentPipelineTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本地文档流水线 Worker 测试。
 */
class LocalDocumentPipelineWorkerTest {

    private Logger workerLogger;
    private Level originalLevel;

    @BeforeEach
    void setUp() {
        workerLogger = (Logger) LoggerFactory.getLogger(LocalDocumentPipelineWorker.class);
        originalLevel = workerLogger.getLevel();
        workerLogger.setLevel(Level.OFF);
    }

    @AfterEach
    void tearDown() {
        workerLogger.setLevel(originalLevel);
    }

    @Test
    void runOnceForTestShouldExecuteTaskByFifoAndAck() {
        TestDocumentPipelineQueue queue = new TestDocumentPipelineQueue();
        queue.enqueue(10L);
        queue.enqueue(20L);
        RecordingDocumentPipelineExecutor executor = new RecordingDocumentPipelineExecutor();
        LocalDocumentPipelineWorker worker = new LocalDocumentPipelineWorker(buildProperties(), queue, executor);

        worker.runOnceForTest("worker-1");
        worker.runOnceForTest("worker-1");

        assertThat(executor.executedDocumentIds()).containsExactly(10L, 20L);
        assertThat(queue.queryStatus(10L)).isEmpty();
        assertThat(queue.queryStatus(20L)).isEmpty();
    }

    @Test
    void runOnceForTestShouldReleaseFailedTaskToQueueTail() {
        TestDocumentPipelineQueue queue = new TestDocumentPipelineQueue();
        queue.enqueue(10L);
        queue.enqueue(20L);
        FailOnceDocumentPipelineExecutor executor = new FailOnceDocumentPipelineExecutor(10L);
        LocalDocumentPipelineWorker worker = new LocalDocumentPipelineWorker(buildProperties(), queue, executor);

        worker.runOnceForTest("worker-1");
        worker.runOnceForTest("worker-1");
        worker.runOnceForTest("worker-1");

        assertThat(executor.executedDocumentIds()).containsExactly(10L, 20L, 10L);
    }

    private DocumentPipelineWorkerProperties buildProperties() {
        DocumentPipelineWorkerProperties properties = new DocumentPipelineWorkerProperties();
        properties.setLeaseTtlSeconds(300L);
        properties.setPollIntervalMs(1L);
        return properties;
    }

    private static class RecordingDocumentPipelineExecutor implements DocumentPipelineExecutor {

        private final List<Long> executedDocumentIds = new ArrayList<>();

        @Override
        public void execute(Long documentId) {
            executedDocumentIds.add(documentId);
        }

        List<Long> executedDocumentIds() {
            return executedDocumentIds;
        }
    }

    private static final class FailOnceDocumentPipelineExecutor extends RecordingDocumentPipelineExecutor {

        private final Long failDocumentId;
        private boolean failed;

        private FailOnceDocumentPipelineExecutor(Long failDocumentId) {
            this.failDocumentId = failDocumentId;
        }

        @Override
        public void execute(Long documentId) {
            super.execute(documentId);
            if (!failed && failDocumentId.equals(documentId)) {
                failed = true;
                throw new IllegalStateException("模拟文档流水线执行失败");
            }
        }
    }

    private static final class TestDocumentPipelineQueue implements DocumentPipelineQueue {

        private final AtomicLong sequence = new AtomicLong();
        private final TreeMap<Long, Long> waitingDocumentIds = new TreeMap<>();
        private final java.util.Map<Long, DocumentPipelineTask> runningTasks = new java.util.HashMap<>();

        @Override
        public DocumentPipelineQueueStatus enqueue(Long documentId) {
            long nextSequence = sequence.incrementAndGet();
            waitingDocumentIds.put(nextSequence, documentId);
            return new DocumentPipelineQueueStatus(documentId, waitingPosition(documentId), waitingDocumentIds.size(),
                    false, null, null, nextSequence);
        }

        @Override
        public Optional<DocumentPipelineTask> poll(String workerId, Duration leaseTtl) {
            if (waitingDocumentIds.isEmpty()) {
                return Optional.empty();
            }
            java.util.Map.Entry<Long, Long> firstEntry = waitingDocumentIds.pollFirstEntry();
            DocumentPipelineTask task = new DocumentPipelineTask(firstEntry.getValue(),
                    "token-" + firstEntry.getValue(), workerId, firstEntry.getKey());
            runningTasks.put(task.documentId(), task);
            return Optional.of(task);
        }

        @Override
        public void ack(Long documentId, String leaseToken) {
            DocumentPipelineTask task = runningTasks.get(documentId);
            if (task != null && task.leaseToken().equals(leaseToken)) {
                runningTasks.remove(documentId);
            }
        }

        @Override
        public void release(Long documentId, String leaseToken, boolean requeue) {
            DocumentPipelineTask task = runningTasks.get(documentId);
            if (task == null || !task.leaseToken().equals(leaseToken)) {
                return;
            }
            runningTasks.remove(documentId);
            if (requeue) {
                waitingDocumentIds.put(sequence.incrementAndGet(), documentId);
            }
        }

        @Override
        public Optional<DocumentPipelineQueueStatus> queryStatus(Long documentId) {
            Integer waitingPosition = waitingPosition(documentId);
            if (waitingPosition != null) {
                return Optional.of(new DocumentPipelineQueueStatus(documentId, waitingPosition,
                        waitingDocumentIds.size(), false, null, null, null));
            }
            if (runningTasks.containsKey(documentId)) {
                return Optional.of(new DocumentPipelineQueueStatus(documentId, null,
                        waitingDocumentIds.size(), true, null, null, null));
            }
            return Optional.empty();
        }

        private Integer waitingPosition(Long documentId) {
            int position = 1;
            for (Long waitingDocumentId : waitingDocumentIds.values()) {
                if (waitingDocumentId.equals(documentId)) {
                    return position;
                }
                position++;
            }
            return null;
        }
    }
}
