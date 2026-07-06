package com.nexarag.model.gateway.chat;

import lombok.Builder;

/**
 * Chat 模型流式响应分片。
 *
 * @param content      当前文本分片
 * @param finishReason 结束原因
 * @param errorCode    错误码
 * @param errorMessage 错误信息
 */
@Builder
public record ChatModelStreamResponse(String content, String finishReason, String errorCode, String errorMessage) {

    /**
     * 构造文本分片。
     *
     * @param content 文本内容
     * @return 流式响应分片
     */
    public static ChatModelStreamResponse message(String content) {
        return ChatModelStreamResponse.builder()
                .content(content)
                .build();
    }

    /**
     * 构造结束分片。
     *
     * @param finishReason 结束原因
     * @return 流式响应分片
     */
    public static ChatModelStreamResponse done(String finishReason) {
        return ChatModelStreamResponse.builder()
                .finishReason(finishReason)
                .build();
    }

    /**
     * 构造错误分片。
     *
     * @param errorCode    错误码
     * @param errorMessage 错误信息
     * @return 流式响应分片
     */
    public static ChatModelStreamResponse error(String errorCode, String errorMessage) {
        return ChatModelStreamResponse.builder()
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
