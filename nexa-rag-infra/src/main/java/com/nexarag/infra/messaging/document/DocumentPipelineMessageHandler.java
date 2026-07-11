package com.nexarag.infra.messaging.document;

import com.nexarag.infra.messaging.document.model.DocumentPipelineMessage;

/**
 * 文档流水线消息处理接口，负责处理消息中间件投递的文档任务。
 */
public interface DocumentPipelineMessageHandler {

    /**
     * 处理文档流水线消息。
     *
     * @param message 文档流水线消息
     */
    void handle(DocumentPipelineMessage message);
}
