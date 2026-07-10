package com.nexarag.boot.worker;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.nexarag.infra.queue.document.DocumentPipelineQueue;
import com.nexarag.infra.queue.document.DocumentPipelineQueueStatus;
import com.nexarag.infra.queue.document.DocumentPipelineTask;
import com.nexarag.workflow.service.WorkflowService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.nexarag.workflow.constants.DocumentIngestionGraphConstants.DOCUMENT_INGESTION_GRAPH_NAME;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.DOCUMENT_ID;
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
    void runOnceForTestShouldRunDocumentIngestionGraphByFifoAndAck() {
        TestDocumentPipelineQueue queue = new TestDocumentPipelineQueue();
        queue.enqueue(10L);
        queue.enqueue(20L);
        RecordingWorkflowService workflowService = new RecordingWorkflowService();
        LocalDocumentPipelineWorker worker = new LocalDocumentPipelineWorker(buildProperties(), queue, workflowService);

        worker.runOnceForTest();
        worker.runOnceForTest();

        assertThat(workflowService.graphNames()).containsExactly(DOCUMENT_INGESTION_GRAPH_NAME, DOCUMENT_INGESTION_GRAPH_NAME);
        assertThat(workflowService.documentIds()).containsExactly(10L, 20L);
        assertThat(queue.queryStatus(10L)).isEmpty();
        assertThat(queue.queryStatus(20L)).isEmpty();
    }

    @Test
    void runOnceForTestShouldReleaseFailedTaskToQueueTail() {
        TestDocumentPipelineQueue queue = new TestDocumentPipelineQueue();
        queue.enqueue(10L);
        queue.enqueue(20L);
        FailOnceWorkflowService workflowService = new FailOnceWorkflowService(10L);
        LocalDocumentPipelineWorker worker = new LocalDocumentPipelineWorker(buildProperties(), queue, workflowService);

        worker.runOnceForTest();
        worker.runOnceForTest();
        worker.runOnceForTest();

        assertThat(workflowService.documentIds()).containsExactly(10L, 20L, 10L);
    }

    @Test
    void startShouldContinuePollingWhenQueuePollFailsOnce() throws InterruptedException {
        FailOncePollingQueue queue = new FailOncePollingQueue();
        queue.enqueue(10L);
        RecordingWorkflowService workflowService = new RecordingWorkflowService();
        LocalDocumentPipelineWorker worker = new LocalDocumentPipelineWorker(buildEnabledProperties(), queue, workflowService);

        worker.start();
        try {
            waitUntilDocumentExecuted(workflowService, 10L);
        } finally {
            worker.stop();
        }

        assertThat(workflowService.documentIds()).contains(10L);
    }

    private DocumentPipelineWorkerProperties buildProperties() {
        DocumentPipelineWorkerProperties properties = new DocumentPipelineWorkerProperties();
        properties.setLeaseTtlSeconds(300L);
        properties.setPollIntervalMs(1L);
        return properties;
    }

    private DocumentPipelineWorkerProperties buildEnabledProperties() {
        DocumentPipelineWorkerProperties properties = buildProperties();
        properties.setWorkerEnabled(true);
        properties.setMaxConcurrency(1);
        return properties;
    }

    private void waitUntilDocumentExecuted(RecordingWorkflowService workflowService, Long documentId)
            throws InterruptedException {
        for (int index = 0; index < 100; index++) {
            if (workflowService.documentIds().contains(documentId)) {
                return;
            }
            Thread.sleep(20L);
        }
    }

    /**
     * 记录 Workflow 调用的测试服务。
     */
    private static class RecordingWorkflowService implements WorkflowService {

        private final List<String> graphNames = new ArrayList<>();
        private final List<Long> documentIds = new ArrayList<>();

        @Override
        public void run(String graphName, Map<String, Object> initialState) {
            graphNames.add(graphName);
            documentIds.add((Long) initialState.get(DOCUMENT_ID));
        }

        List<String> graphNames() {
            return graphNames;
        }

        List<Long> documentIds() {
            return documentIds;
        }
    }

    /**
     * 首次执行指定文档时失败的 Workflow 测试服务。
     */
    private static final class FailOnceWorkflowService extends RecordingWorkflowService {

        private final Long failDocumentId;
        private boolean failed;

        private FailOnceWorkflowService(Long failDocumentId) {
            this.failDocumentId = failDocumentId;
        }

        @Override
        public void run(String graphName, Map<String, Object> initialState) {
            super.run(graphName, initialState);
            Long documentId = (Long) initialState.get(DOCUMENT_ID);
            if (!failed && failDocumentId.equals(documentId)) {
                failed = true;
                throw new IllegalStateException("模拟文档入库 Graph 执行失败");
            }
        }
    }

    /**
     * 用于验证 FIFO、确认和释放行为的内存队列。
     */
    private static class TestDocumentPipelineQueue implements DocumentPipelineQueue {

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

    /**
     * 首次轮询抛出异常的内存队列，用于验证 Worker 不会静默退出。
     */
    private static final class FailOncePollingQueue extends TestDocumentPipelineQueue {

        private final AtomicInteger pollCount = new AtomicInteger();

        @Override
        public Optional<DocumentPipelineTask> poll(String workerId, Duration leaseTtl) {
            if (pollCount.incrementAndGet() == 1) {
                throw new IllegalStateException("模拟 Redis 队列轮询失败");
            }
            return super.poll(workerId, leaseTtl);
        }
    }
}
