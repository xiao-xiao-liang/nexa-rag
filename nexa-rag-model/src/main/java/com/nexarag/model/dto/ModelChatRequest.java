package com.nexarag.model.dto;

import com.nexarag.model.gateway.chat.ChatModelMessage;

import java.util.List;
import java.util.Map;

/**
 * 裸 Chat 调用请求，用于直接验证 Chat 模型能力。
 *
 * @param routeKey 模型路由 Key
 * @param messages 聊天消息列表
 * @param options  调用选项
 */
public record ModelChatRequest(String routeKey, List<ChatModelMessage> messages, Map<String, Object> options) {
}
