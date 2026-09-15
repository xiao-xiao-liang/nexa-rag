package com.nexarag.boot.observability;

import static com.nexarag.boot.constants.ModelObservabilityConstant.LANGFUSE_UNAVAILABLE_REASON;

/**
 * 完整 RAG 语义分段的 Token 分布。
 *
 * @param available                    Langfuse 数据是否可用
 * @param reason                       不可用原因
 * @param completeBreakdownCount       完整分段记录数
 * @param systemTokensP50              系统提示词 Token P50
 * @param summaryTokensP50             会话摘要 Token P50
 * @param historyTokensP50             历史消息 Token P50
 * @param questionTokensP50            当前问题 Token P50
 * @param retrievalTokensP50           接纳证据 Token P50
 * @param toolTokensP50                工具状态 Token P50
 * @param templateOverheadTokensP50    Chat Template 开销 P50
 * @param truncated                    是否因分页上限而截断样本
 */
public record ModelTokenDistributionVO(boolean available, String reason, long completeBreakdownCount,
                                        Integer systemTokensP50, Integer summaryTokensP50, Integer historyTokensP50,
                                        Integer questionTokensP50, Integer retrievalTokensP50, Integer toolTokensP50,
                                        Integer templateOverheadTokensP50, boolean truncated) {

    /** 创建不可用时的安全空响应。 */
    public static ModelTokenDistributionVO unavailable() {
        return new ModelTokenDistributionVO(false, LANGFUSE_UNAVAILABLE_REASON, 0,
                null, null, null, null, null, null, null, false);
    }
}
