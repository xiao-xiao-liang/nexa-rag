package com.nexarag.model.constants;

/**
 * 模型调用与 RAG Token 观测使用的稳定名称、终态和值键。
 */
public final class ModelTelemetryConstant {

    public static final String ROUTE_KEY = "nexa.route_key";
    public static final String RERANK_GENERATION_NAME = "rag.rerank-model";
    public static final String DEFAULT_CHAT_GENERATION_NAME = "model.chat";
    public static final String COMPLETE_BREAKDOWN_STATUS = "COMPLETE";
    public static final String RAG_TOKEN_STATUS = "nexa.rag_token_status";
    public static final String RAG_INPUT_BUDGET_TOKENS = "nexa.rag_input_budget_tokens";
    public static final String RAG_RETRIEVAL_CANDIDATE_COUNT = "nexa.rag_retrieval_candidate_count";
    public static final String RAG_RETRIEVAL_ACCEPTED_COUNT = "nexa.rag_retrieval_accepted_count";
    public static final String RAG_RETRIEVAL_SKIPPED_COUNT = "nexa.rag_retrieval_skipped_count";
    public static final String RAG_SYSTEM_TOKENS = "nexa.rag_system_tokens";
    public static final String RAG_SUMMARY_TOKENS = "nexa.rag_summary_tokens";
    public static final String RAG_HISTORY_TOKENS = "nexa.rag_history_tokens";
    public static final String RAG_QUESTION_TOKENS = "nexa.rag_question_tokens";
    public static final String RAG_RETRIEVAL_TOKENS = "nexa.rag_retrieval_tokens";
    public static final String RAG_TOOL_TOKENS = "nexa.rag_tool_tokens";
    public static final String RAG_TEMPLATE_OVERHEAD_TOKENS = "nexa.rag_template_overhead_tokens";
    public static final String PROVIDER_USAGE_INPUT = "input";
    public static final String PROVIDER_USAGE_INPUT_CAMEL_CASE = "inputTokens";
    public static final String PROVIDER_USAGE_INPUT_SNAKE_CASE = "input_tokens";
    public static final String PROVIDER_USAGE_OUTPUT = "output";
    public static final String PROVIDER_USAGE_OUTPUT_CAMEL_CASE = "outputTokens";
    public static final String PROVIDER_USAGE_OUTPUT_SNAKE_CASE = "output_tokens";
    public static final String PROVIDER_USAGE_TOTAL = "total";
    public static final String PROVIDER_USAGE_TOTAL_CAMEL_CASE = "totalTokens";
    public static final String PROVIDER_USAGE_TOTAL_SNAKE_CASE = "total_tokens";

    private ModelTelemetryConstant() {
    }
}
