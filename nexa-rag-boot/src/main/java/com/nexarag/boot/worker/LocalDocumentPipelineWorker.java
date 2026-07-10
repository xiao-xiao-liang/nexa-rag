package com.nexarag.boot.worker;

import com.nexarag.infra.queue.document.DocumentPipelineQueue;
import com.nexarag.infra.queue.document.DocumentPipelineTask;
import com.nexarag.workflow.service.WorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.nexarag.workflow.constants.DocumentIngestionGraphConstants.DOCUMENT_INGESTION_GRAPH_NAME;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.DOCUMENT_ID;

/**
 * 本地文档流水线 Worker，负责轮询 Redis 队列并启动文档入库 Workflow Graph。
 */
@Component
public class LocalDocumentPipelineWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(LocalDocumentPipelineWorker.class);
    private static final String LOCAL_MODE = "local";
    private static final String PIPELINE_QUEUE_MODE = "pipeline";

    private final DocumentPipelineWorkerProperties properties;
    private final DocumentPipelineQueue documentPipelineQueue;
    private final WorkflowService workflowService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ExecutorService executorService;

    public LocalDocumentPipelineWorker(DocumentPipelineWorkerProperties properties,
                                       DocumentPipelineQueue documentPipelineQueue,
                                       WorkflowService workflowService) {
        this.properties = properties;
        this.documentPipelineQueue = documentPipelineQueue;
        this.workflowService = workflowService;
    }

    /**
     * 启动本地 Worker 线程池。
     */
    @Override
    public void start() {
        if (!isWorkerEnabled()) {
            log.info("本地文档流水线 Worker 未启用，mode={}，queueMode={}", properties.getMode(), properties.getQueueMode());
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }

        // 1. 根据配置创建固定大小线程池
        int concurrency = Math.max(1, properties.getMaxConcurrency());
        executorService = Executors.newFixedThreadPool(concurrency);

        // 2. 提交轮询任务，每个线程持有独立 workerId
        for (int index = 0; index < concurrency; index++) {
            String workerId = "local-worker-" + index + "-" + UUID.randomUUID();
            executorService.submit(() -> runLoop(workerId));
        }
        log.info("本地文档流水线 Worker 已启动，maxConcurrency={}", concurrency);
    }

    /**
     * 停止本地 Worker 线程池。
     */
    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        // 1. 请求线程池停止接收新任务
        if (executorService != null) {
            executorService.shutdownNow();
            try {
                executorService.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.warn("等待本地文档流水线 Worker 停止时被中断");
            }
        }
        log.info("本地文档流水线 Worker 已停止");
    }

    /**
     * 判断 Worker 当前是否运行。
     *
     * @return true 表示运行中
     */
    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 判断是否随 Spring 容器自动启动。
     *
     * @return true 表示自动启动
     */
    @Override
    public boolean isAutoStartup() {
        return isWorkerEnabled();
    }

    /**
     * 单次执行队列任务，供单元测试验证 Worker 编排行为。
     *
     * @param workerId 工作器ID
     */
    void runOnceForTest(String workerId) {
        runOnce(workerId);
    }

    private void runLoop(String workerId) {
        while (running.get()) {
            try {
                // 1. 单次轮询失败不能让 Worker 线程静默退出，避免 Redis 短暂异常导致队列停摆
                boolean executed = runOnce(workerId);
                if (!executed) {
                    sleepWhenQueueEmpty();
                }
            } catch (RuntimeException exception) {
                log.error("本地文档流水线 Worker 轮询任务失败，将继续重试，workerId={}", workerId, exception);
                sleepWhenQueueEmpty();
            }
        }
    }

    private boolean runOnce(String workerId) {
        // 1. 从队列获取一个带租约的任务
        Optional<DocumentPipelineTask> optionalTask = documentPipelineQueue.poll(workerId,
                Duration.ofSeconds(properties.getLeaseTtlSeconds()));
        if (optionalTask.isEmpty()) {
            return false;
        }

        DocumentPipelineTask task = optionalTask.get();
        try {
            // 2. 调用 Workflow Graph 执行文档入库流水线
            workflowService.run(DOCUMENT_INGESTION_GRAPH_NAME, Map.of(DOCUMENT_ID, task.documentId()));

            // 3. 执行成功后确认任务完成
            documentPipelineQueue.ack(task.documentId(), task.leaseToken());
            return true;
        } catch (RuntimeException exception) {
            log.error("文档流水线任务执行失败，准备释放回队尾，documentId={}，workerId={}",
                    task.documentId(), workerId, exception);
            documentPipelineQueue.release(task.documentId(), task.leaseToken(), true);
            return true;
        }
    }

    private void sleepWhenQueueEmpty() {
        try {
            TimeUnit.MILLISECONDS.sleep(properties.getPollIntervalMs());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isWorkerEnabled() {
        return properties.isWorkerEnabled()
                && LOCAL_MODE.equalsIgnoreCase(properties.getMode())
                && PIPELINE_QUEUE_MODE.equalsIgnoreCase(properties.getQueueMode());
    }
}
