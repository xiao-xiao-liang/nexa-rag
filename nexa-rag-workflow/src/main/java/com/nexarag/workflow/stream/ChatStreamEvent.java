package com.nexarag.workflow.stream;

/**
 * Chat 流式输出事件，承载正文分片、任务标识和错误信息。
 *
 * @param type 事件类型
 * @param content 正文分片
 * @param conversationId 会话 ID
 * @param traceId 链路 ID
 * @param generationId 生成任务 ID
 * @param messageId 助手消息 ID
 * @param errorCode 错误编码
 * @param errorMessage 错误信息
 */
public record ChatStreamEvent(ChatStreamEventType type, String content, String conversationId,
                              String traceId, String generationId, String messageId,
                              String errorCode, String errorMessage) {

    /**
     * 构造正文分片事件。
     *
     * @param content 正文分片
     * @return TOKEN 事件
     */
    public static ChatStreamEvent token(String content) {
        return new ChatStreamEvent(ChatStreamEventType.TOKEN, content, null,
                null, null, null, null, null);
    }

    /**
     * 构造模型失败事件。
     *
     * @param errorCode 错误编码
     * @param errorMessage 错误信息
     * @return ERROR 事件
     */
    public static ChatStreamEvent error(String errorCode, String errorMessage) {
        return new ChatStreamEvent(ChatStreamEventType.ERROR, null, null,
                null, null, null, errorCode, errorMessage);
    }
}
