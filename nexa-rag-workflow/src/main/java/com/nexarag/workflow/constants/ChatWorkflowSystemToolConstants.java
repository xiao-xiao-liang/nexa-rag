package com.nexarag.workflow.constants;

/**
 * 对话工作流面向客户端展示的系统工具名称和执行顺序。
 */
public final class ChatWorkflowSystemToolConstants {

    public static final String QUESTION_REWRITE_TOOL_NAME = "system:question_rewrite";
    public static final String INTENT_RECOGNITION_TOOL_NAME = "system:intent_recognition";
    public static final String KNOWLEDGE_SEARCH_TOOL_NAME = "system:knowledge_search";

    public static final long QUESTION_REWRITE_SEQUENCE = 1L;
    public static final long INTENT_RECOGNITION_SEQUENCE = 2L;
    public static final long KNOWLEDGE_SEARCH_SEQUENCE = 3L;

    private ChatWorkflowSystemToolConstants() {
    }
}
