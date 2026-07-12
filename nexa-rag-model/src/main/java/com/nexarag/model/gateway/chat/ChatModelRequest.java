package com.nexarag.model.gateway.chat;

import com.nexarag.model.enums.ModelBizType;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * 聊天模型请求。
 *
 * @param traceId  链路追踪 ID
 * @param bizType  业务类型
 * @param bizId    业务 ID
 * @param routeKey 路由键
 * @param messages 消息列表
 * @param options  调用选项
 */
@Builder
public record ChatModelRequest(String traceId, ModelBizType bizType, String bizId, String routeKey,
                               List<ChatModelMessage> messages, Map<String, Object> options) {
}
