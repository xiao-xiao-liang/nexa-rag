package com.nexarag.workflow.stream;

import java.util.List;

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
 * @param eventVersion 生成任务内单调递增的事件版本
 * @param operations 工具调用最小展示快照
 */
public record ChatStreamEvent(ChatStreamEventType type, String content, String conversationId,
                              String traceId, String generationId, String messageId,
                              String errorCode, String errorMessage, long eventVersion,
                              List<ChatToolOperationDTO> operations) {

    /**
     * 兼容既有调用的事件构造器，版本由 Redis 缓冲分配。
     */
    public ChatStreamEvent(ChatStreamEventType type, String content, String conversationId,
                           String traceId, String generationId, String messageId,
                           String errorCode, String errorMessage) {
        this(type, content, conversationId, traceId, generationId, messageId,
                errorCode, errorMessage, 0L, List.of());
    }

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

    /**
     * 返回携带指定版本的不可变事件副本。
     *
     * @param version 事件版本
     * @return 版本化事件
     */
    public ChatStreamEvent withEventVersion(long version) {
        return new ChatStreamEvent(type, content, conversationId, traceId, generationId, messageId,
                errorCode, errorMessage, version, operations == null ? List.of() : List.copyOf(operations));
    }
}
