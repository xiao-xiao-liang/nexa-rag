package com.nexarag.document.service;

/**
 * 文档队列信息，描述上传提交后在处理流水线中的实时排队概况。
 *
 * @param queuePosition 当前队列位置
 * @param waitingCount  等待处理数量
 */
public record DocumentQueueInfo(Integer queuePosition, Integer waitingCount) {
}
