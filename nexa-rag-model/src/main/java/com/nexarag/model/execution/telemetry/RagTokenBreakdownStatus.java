package com.nexarag.model.execution.telemetry;

/**
 * RAG 语义分段 Token 统计的终态。
 */
public enum RagTokenBreakdownStatus {

    /** 所有分段都已取得 vLLM 精确 Token 数。 */
    COMPLETE,

    /** 仅部分分段取得 vLLM 精确 Token 数。 */
    PARTIAL,

    /** 未取得任何 vLLM 精确 Token 数。 */
    UNAVAILABLE
}
