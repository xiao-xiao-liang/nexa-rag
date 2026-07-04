package com.nexarag.document.service;

/**
 * 文档队列信息，描述上传提交后在处理流水线中的实时排队概况。
 *
 * @param queuePosition 当前队列位置
 * @param waitingCount  等待处理数量
 * @param running       是否运行中
 * @param workerId      当前 Worker ID
 * @param leaseTtlSeconds 租约剩余秒数
 */
public record DocumentQueueInfo(Integer queuePosition, Integer waitingCount, Boolean running,
                                String workerId, Long leaseTtlSeconds) {

    /**
     * 创建等待队列信息，兼容已有上传响应构造逻辑。
     *
     * @param queuePosition 当前队列位置
     * @param waitingCount  等待处理数量
     */
    public DocumentQueueInfo(Integer queuePosition, Integer waitingCount) {
        this(queuePosition, waitingCount, false, null, null);
    }
}
