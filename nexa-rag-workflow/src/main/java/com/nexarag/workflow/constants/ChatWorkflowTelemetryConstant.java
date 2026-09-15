package com.nexarag.workflow.constants;

/**
 * 对话工作流节点上报 Langfuse 时使用的 Span、Generation 和属性名称。
 */
public final class ChatWorkflowTelemetryConstant {

    public static final String CHAT_TRACE_NAME = "rag.chat";
    public static final String ANSWER_GENERATION_NAME = "rag.answer";
    public static final String QUESTION_REWRITE_GENERATION_NAME = "rag.question-rewrite";
    public static final String INTENT_RECOGNITION_GENERATION_NAME = "rag.intent-recognition";
    public static final String CONVERSATION_TITLE_GENERATION_NAME = "rag.conversation-title";
    public static final String RETRIEVAL_SPAN_NAME = "rag.retrieval";
    public static final String RERANK_SPAN_NAME = "rag.rerank";
    public static final String EVIDENCE_SELECTION_SPAN_NAME = "rag.evidence-selection";
    public static final String RETRIEVAL_ROUND_ATTRIBUTE = "nexa.retrieval.round";
    public static final String RETRIEVAL_TOP_K_ATTRIBUTE = "nexa.retrieval.top_k";
    public static final String RETRIEVAL_VECTOR_THRESHOLD_ATTRIBUTE = "nexa.retrieval.vector_threshold";
    public static final String RETRIEVAL_RESULT_COUNT_ATTRIBUTE = "nexa.retrieval.result_count";
    public static final String RETRIEVAL_DEGRADED_ATTRIBUTE = "nexa.retrieval.degraded";
    public static final String RERANK_CANDIDATE_COUNT_ATTRIBUTE = "nexa.rerank.candidate_count";
    public static final String RERANK_ACCEPTED_THRESHOLD_ATTRIBUTE = "nexa.rerank.accepted_threshold";
    public static final String RERANK_SKIPPED_ATTRIBUTE = "nexa.rerank.skipped";
    public static final String RERANK_ACCEPTED_COUNT_ATTRIBUTE = "nexa.rerank.accepted_count";
    public static final String RERANK_FALLBACK_ATTRIBUTE = "nexa.rerank.fallback";
    public static final String EVIDENCE_CANDIDATE_COUNT_ATTRIBUTE = "nexa.evidence.candidate_count";
    public static final String EVIDENCE_ACCEPTED_COUNT_ATTRIBUTE = "nexa.evidence.accepted_count";
    public static final String EVIDENCE_ESTIMATED_TOKENS_ATTRIBUTE = "nexa.evidence.estimated_tokens";
    public static final String EVIDENCE_SUFFICIENT_ATTRIBUTE = "nexa.evidence.sufficient";
    public static final String RERANK_BIZ_ID = "chat-rerank";
    public static final String RERANK_ROUTE_KEY = "rerank";
    public static final String RETRIEVAL_OPERATION_ID_SUFFIX = ":tool:retrieval:1";
    public static final String QUESTION_REWRITE_OPERATION_ID_SUFFIX = ":tool:question-rewrite:1";
    public static final String INTENT_RECOGNITION_OPERATION_ID_SUFFIX = ":tool:intent-recognition:1";

    private ChatWorkflowTelemetryConstant() {
    }
}
