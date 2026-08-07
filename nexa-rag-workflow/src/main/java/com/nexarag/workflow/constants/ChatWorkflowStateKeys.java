package com.nexarag.workflow.constants;

/**
 * 会话对话 Workflow 状态键，统一维护节点之间传递的数据名称。
 */
public final class ChatWorkflowStateKeys {

    public static final String USER_QUESTION = "userQuestion";
    public static final String CONVERSATION_CONTEXT = "conversationContext";
    public static final String REWRITTEN_QUESTION = "rewrittenQuestion";
    public static final String INTENT_RESULT = "intentResult";
    public static final String FUSED_RETRIEVAL_RESULTS = "fusedRetrievalResults";
    public static final String RERANKED_RETRIEVAL_RESULTS = "rerankedRetrievalResults";
    public static final String ASSISTANT_CONTENT = "assistantContent";
    public static final String STREAM_STATUS = "streamStatus";
    public static final String FINISH_REASON = "finishReason";
    public static final String PROMPT_TOKENS = "promptTokens";
    public static final String COMPLETION_TOKENS = "completionTokens";
    public static final String TOTAL_TOKENS = "totalTokens";
    public static final String ERROR_CODE = "errorCode";
    public static final String ERROR_MESSAGE = "errorMessage";
    public static final String USER_ID = "userId";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String TRACE_ID = "traceId";
    public static final String GENERATION_ID = "generationId";
    public static final String IS_NEW_CONVERSATION = "isNewConversation";
    public static final String USER_MESSAGE_ID = "userMessageId";
    public static final String ASSISTANT_MESSAGE_ID = "assistantMessageId";
    public static final String RETRIEVAL_SCOPE = "retrievalScope";
    public static final String RETRIEVAL_ROUND = "retrievalRound";
    public static final String MAX_RETRIEVAL_ROUND = "maxRetrievalRound";
    public static final String RETRIEVAL_TOP_K = "retrievalTopK";
    public static final String RETRIEVAL_VECTOR_THRESHOLD = "retrievalVectorThreshold";
    public static final String RAW_RETRIEVAL_RESULTS = "rawRetrievalResults";
    public static final String MODEL_STREAM_RESULT = "modelStreamResult";
    public static final String PROMPT_EXECUTION_SNAPSHOT = "promptExecutionSnapshot";

    private ChatWorkflowStateKeys() {
    }
}
