package com.nexarag.chat.domain;

/**
 * 面向客户端公开的消息内引用摘要。
 *
 * @param citationId 消息内引用编号
 */
public record ChatCitationSummaryVO(int citationId) {
}
