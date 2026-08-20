package com.nexarag.chat.domain;

/**
 * 同一聊天回合中原子创建的用户与助手消息。
 *
 * @param userMessage 已完成的用户消息
 * @param assistantMessage 生成中的助手消息
 */
public record ChatGenerationTurnBO(ChatMessageVO userMessage, ChatMessageVO assistantMessage) {
}
