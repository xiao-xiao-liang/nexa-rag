package com.nexarag.infra.messaging.document;

import com.nexarag.infra.messaging.document.model.DocumentPipelineMessage;
import com.nexarag.infra.messaging.document.model.DocumentPipelinePublishResult;

/**
 * 文档流水线消息发布接口，负责向消息中间件发布待处理文档消息。
 */
public interface DocumentPipelineMessagePublisher {

    /**
     * 发布文档流水线消息。
     *
     * @param message 文档流水线消息
     * @return 消息发布结果
     */
    DocumentPipelinePublishResult publish(DocumentPipelineMessage message);
}
