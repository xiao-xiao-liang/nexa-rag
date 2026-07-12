package com.nexarag.workflow.prompt;

import com.nexarag.model.gateway.chat.ChatModelMessage;

import java.util.List;

/**
 * 会话 Workflow Prompt 构建器，负责生成改写和意图识别所需的模型消息。
 */
public class ChatWorkflowPromptBuilder {

    /**
     * 构建问题改写消息。
     *
     * @param question 当前问题
     * @param context 会话上下文
     * @return 模型消息列表
     */
    public List<ChatModelMessage> buildRewriteMessages(String question, String context) {
        return List.of(
                new ChatModelMessage("SYSTEM", "请将当前问题改写为独立、清晰、适合知识库检索的问题，只返回改写结果。"),
                new ChatModelMessage("USER", "会话上下文：\n" + safe(context) + "\n当前问题：\n" + safe(question)));
    }

    /**
     * 构建意图识别消息。
     *
     * @param question 检索问题
     * @return 模型消息列表
     */
    public List<ChatModelMessage> buildIntentMessages(String question) {
        return List.of(
                new ChatModelMessage("SYSTEM", "识别问题的知识库检索意图。只返回 JSON：{\"intentIds\":[],\"confidence\":0}。"),
                new ChatModelMessage("USER", safe(question)));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
