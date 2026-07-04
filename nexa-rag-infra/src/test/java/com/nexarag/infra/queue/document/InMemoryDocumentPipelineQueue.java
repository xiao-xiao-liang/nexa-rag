package com.nexarag.infra.queue.document;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 文档流水线内存队列，仅用于单元测试验证队列契约。
 */
class InMemoryDocumentPipelineQueue implements DocumentPipelineQueue {

    private final AtomicLong sequence = new AtomicLong();
    private final TreeMap<Long, Long> waiting = new TreeMap<>();
    private final Map<Long, RunningTask> running = new HashMap<>();

    @Override
    public synchronized DocumentPipelineQueueStatus enqueue(Long documentId) {
        // 1. 如果文档已经等待或运行，直接返回现有状态，避免重复入队
        Optional<DocumentPipelineQueueStatus> existingStatus = queryStatus(documentId);
        if (existingStatus.isPresent()) {
            return existingStatus.get();
        }

        // 2. 使用单调递增序号模拟 Redis INCR，确保公平 FIFO
        long enqueueSequence = sequence.incrementAndGet();
        waiting.put(enqueueSequence, documentId);
        return queryStatus(documentId).orElseThrow();
    }

    @Override
    public synchronized Optional<DocumentPipelineTask> poll(String workerId, Duration leaseTtl) {
        if (waiting.isEmpty()) {
            return Optional.empty();
        }

        // 1. 取最小入队序号，模拟 Redis ZSET score 最小项
        Map.Entry<Long, Long> firstEntry = waiting.pollFirstEntry();
        String leaseToken = UUID.randomUUID().toString();
        RunningTask runningTask = new RunningTask(firstEntry.getValue(), leaseToken, workerId, firstEntry.getKey());
        running.put(firstEntry.getValue(), runningTask);
        return Optional.of(new DocumentPipelineTask(firstEntry.getValue(), leaseToken, workerId, firstEntry.getKey()));
    }

    @Override
    public synchronized void ack(Long documentId, String leaseToken) {
        RunningTask runningTask = running.get(documentId);
        if (runningTask != null && runningTask.leaseToken().equals(leaseToken)) {
            // 1. 租约匹配时确认完成并删除运行态
            running.remove(documentId);
        }
    }

    @Override
    public synchronized void release(Long documentId, String leaseToken, boolean requeue) {
        RunningTask runningTask = running.get(documentId);
        if (runningTask == null || !runningTask.leaseToken().equals(leaseToken)) {
            return;
        }

        // 1. 租约匹配时先删除运行态
        running.remove(documentId);
        if (requeue) {
            // 2. 失败任务重新入队时使用新序号进入队尾，避免插队
            waiting.put(sequence.incrementAndGet(), documentId);
        }
    }

    @Override
    public synchronized Optional<DocumentPipelineQueueStatus> queryStatus(Long documentId) {
        if (running.containsKey(documentId)) {
            RunningTask runningTask = running.get(documentId);
            return Optional.of(new DocumentPipelineQueueStatus(documentId, null, waiting.size(), true,
                    runningTask.workerId(), null, runningTask.enqueueSequence()));
        }
        int position = 1;
        for (Map.Entry<Long, Long> entry : waiting.entrySet()) {
            if (entry.getValue().equals(documentId)) {
                return Optional.of(new DocumentPipelineQueueStatus(documentId, position, waiting.size(), false,
                        null, null, entry.getKey()));
            }
            position++;
        }
        return Optional.empty();
    }

    private record RunningTask(Long documentId, String leaseToken, String workerId, Long enqueueSequence) {
    }
}
