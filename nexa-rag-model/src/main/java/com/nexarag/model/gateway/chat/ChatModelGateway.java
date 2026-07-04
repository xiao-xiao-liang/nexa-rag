package com.nexarag.model.gateway.chat;

/**
 * 聊天模型网关。
 */
public interface ChatModelGateway {

    /**
     * 调用聊天模型。
     *
     * @param request 聊天模型请求
     * @return 聊天模型响应
     */
    ChatModelResponse call(ChatModelRequest request);
}
