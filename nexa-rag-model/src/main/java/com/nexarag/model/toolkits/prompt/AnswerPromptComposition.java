package com.nexarag.model.toolkits.prompt;

import com.nexarag.model.gateway.chat.ChatModelMessage;

import java.util.List;

/**
 * 最终回答 Prompt 的渲染结果，同时保留互不重叠的原始语义分段。
 *
 * <p>最终消息可因模型 Chat Template 约束合并多个分段；Token 观测必须使用本对象的原始分段，
 * 不能反向从合并后的消息中推导。</p>
 *
 * @param messages          实际发送给模型的消息
 * @param systemInstruction 纯系统指令
 * @param summary           会话摘要
 * @param historyMessages   历史消息
 * @param question          当前问题
 * @param retrievalContent  检索证据
 * @param toolContent       工具执行状态
 */
public record AnswerPromptComposition(
        List<ChatModelMessage> messages,
        String systemInstruction,
        String summary,
        List<ChatModelMessage> historyMessages,
        String question,
        String retrievalContent,
        String toolContent) {

    public AnswerPromptComposition {
        messages = messages == null ? List.of() : List.copyOf(messages);
        systemInstruction = safe(systemInstruction);
        summary = safe(summary);
        historyMessages = historyMessages == null ? List.of() : List.copyOf(historyMessages);
        question = safe(question);
        retrievalContent = safe(retrievalContent);
        toolContent = safe(toolContent);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
