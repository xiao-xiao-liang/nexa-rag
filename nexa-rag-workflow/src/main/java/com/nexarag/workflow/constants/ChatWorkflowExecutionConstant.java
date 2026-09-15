package com.nexarag.workflow.constants;

/**
 * 对话工作流内部执行策略使用的固定参数。
 */
public final class ChatWorkflowExecutionConstant {

    public static final int RETRIEVAL_MAX_ATTEMPTS = 3;
    public static final int INITIAL_RETRIEVAL_ROUND = 1;
    public static final int DEFAULT_MAX_RETRIEVAL_ROUND = 2;
    public static final double RRF_RANK_CONSTANT = 60D;
    public static final double RRF_RANK_OFFSET = 1D;
    public static final int TEMPORARY_TITLE_MAX_LENGTH = 20;

    private ChatWorkflowExecutionConstant() {
    }
}
