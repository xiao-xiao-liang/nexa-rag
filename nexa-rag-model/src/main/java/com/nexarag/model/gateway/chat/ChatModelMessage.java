package com.nexarag.model.gateway.chat;

/**
 * 模型网关使用的单条聊天消息。
 *
 * @param role 消息角色，支持 SYSTEM、USER 和 ASSISTANT
 * @param content 消息内容
 */
public record ChatModelMessage(String role, String content) {
}
