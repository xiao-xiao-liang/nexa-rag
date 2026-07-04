package com.nexarag.infra.queue.document;

/**
 * 文档流水线队列状态。
 *
 * @param documentId      文档ID
 * @param queuePosition   等待队列位置，从 1 开始
 * @param waitingCount    等待队列总数
 * @param running         是否运行中
 * @param workerId        当前 Worker ID
 * @param leaseTtlSeconds 租约剩余秒数
 * @param enqueueSequence 入队序号
 */
public record DocumentPipelineQueueStatus(Long documentId,
                                          Integer queuePosition,
                                          Integer waitingCount,
                                          Boolean running,
                                          String workerId,
                                          Long leaseTtlSeconds,
                                          Long enqueueSequence) {
}
