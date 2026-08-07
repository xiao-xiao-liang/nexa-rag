package com.nexarag.workflow.constants;

/**
 * Chat Workflow 图名称和默认运行参数常量。
 */
public final class ChatWorkflowGraphConstants {

    public static final String CHAT_CONVERSATION_GRAPH_NAME = "chat-conversation";
    public static final String CHAT_THREAD_PREFIX = "chat:";
    public static final int DEFAULT_RETRIEVAL_TOP_K = 10;
    public static final int MAX_RETRIEVAL_ROUND_VALUE = 2;
    public static final double DEFAULT_VECTOR_THRESHOLD = 0.5D;

    private ChatWorkflowGraphConstants() {
    }
}
