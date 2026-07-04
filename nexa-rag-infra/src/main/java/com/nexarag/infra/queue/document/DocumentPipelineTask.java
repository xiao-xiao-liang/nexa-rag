package com.nexarag.infra.queue.document;

/**
 * 文档流水线任务租约。
 *
 * @param documentId      文档ID
 * @param leaseToken      租约令牌
 * @param workerId        Worker ID
 * @param enqueueSequence 入队序号
 */
public record DocumentPipelineTask(Long documentId,
                                   String leaseToken,
                                   String workerId,
                                   Long enqueueSequence) {
}
