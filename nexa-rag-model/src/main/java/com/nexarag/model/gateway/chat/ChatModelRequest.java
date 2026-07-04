package com.nexarag.model.gateway.chat;

import com.nexarag.model.enums.ModelBizType;

import java.util.List;
import java.util.Map;

/**
 * 聊天模型请求。
 *
 * @param traceId  链路追踪ID
 * @param bizType  业务类型
 * @param bizId    业务ID
 * @param routeKey 路由Key
 * @param messages 消息列表
 * @param options  调用选项
 */
public record ChatModelRequest(String traceId, ModelBizType bizType, String bizId, String routeKey,
                               List<ChatMessage> messages, Map<String, Object> options) {

    /**
     * 聊天消息。
     *
     * @param role    角色
     * @param content 内容
     */
    public record ChatMessage(String role, String content) {
    }
}
