package com.nexarag.boot.controller;

import java.util.List;

/**
 * Chat 流式对话请求。
 *
 * @param conversationId 会话 ID，可为空
 * @param content 用户问题
 * @param knowledgeBaseIds 可选知识库范围；为空时检索当前租户全部知识库
 */
public record ChatStreamRequest(String conversationId, String content, List<Long> knowledgeBaseIds) {

    /** 创建未限定知识库范围的对话请求。 */
    public ChatStreamRequest(String conversationId, String content) {
        this(conversationId, content, List.of());
    }
}
