package com.nexarag.workflow.constants;

/**
 * Chat Workflow 节点名称常量。
 */
public final class ChatWorkflowNodeConstants {

    public static final String CONVERSATION_VALIDATION_NODE = "conversationValidation";
    public static final String CONVERSATION_CONTEXT_NODE = "conversationContext";
    public static final String QUESTION_REWRITE_NODE = "questionRewrite";
    public static final String INTENT_RECOGNITION_NODE = "intentRecognition";
    public static final String RETRIEVAL_NODE = "retrieval";
    public static final String RETRIEVAL_FUSION_NODE = "retrievalFusion";
    public static final String RERANK_NODE = "rerank";
    public static final String ANSWER_GENERATION_NODE = "answerGeneration";
    public static final String ASSISTANT_MESSAGE_PERSISTENCE_NODE = "assistantMessagePersistence";

    private ChatWorkflowNodeConstants() {
    }
}
