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

    private ChatWorkflowStateKeys() {
    }
}
