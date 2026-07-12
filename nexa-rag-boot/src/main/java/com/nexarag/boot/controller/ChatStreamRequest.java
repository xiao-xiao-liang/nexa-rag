package com.nexarag.boot.controller;

/**
 * Chat 流式对话请求。
 *
 * @param conversationId 会话 ID，可为空
 * @param content 用户问题
 */
public record ChatStreamRequest(String conversationId, String content) {
}
