package com.nexarag.infra.queue.document;

import java.time.Duration;
import java.util.Optional;

/**
 * 文档流水线队列，负责整条文档处理任务的排队、租约和状态查询。
 */
public interface DocumentPipelineQueue {

    /**
     * 将文档加入等待队列；如果已在等待或运行中，则返回现有状态。
     *
     * @param documentId 文档ID
     * @return 文档队列状态
     */
    DocumentPipelineQueueStatus enqueue(Long documentId);

    /**
     * 按公平队列顺序获取一个任务租约。
     *
     * @param workerId 工作器ID
     * @param leaseTtl 租约时长
     * @return 获取到的任务；队列为空时返回 Optional.empty()
     */
    Optional<DocumentPipelineTask> poll(String workerId, Duration leaseTtl);

    /**
     * 确认任务完成并删除运行态。
     *
     * @param documentId 文档ID
     * @param leaseToken 租约令牌
     */
    void ack(Long documentId, String leaseToken);

    /**
     * 释放任务租约；requeue=true 时重新回到 waiting 队列尾部。
     *
     * @param documentId 文档ID
     * @param leaseToken 租约令牌
     * @param requeue    是否重新入队
     */
    void release(Long documentId, String leaseToken, boolean requeue);

    /**
     * 查询队列实时状态。
     *
     * @param documentId 文档ID
     * @return 队列状态；Redis 无状态时返回 Optional.empty()
     */
    Optional<DocumentPipelineQueueStatus> queryStatus(Long documentId);
}
