package com.nexarag.boot.observability;

import static com.nexarag.boot.constants.ModelObservabilityConstant.LANGFUSE_UNAVAILABLE_REASON;

/**
 * 模型 Token 与响应性能概览。
 *
 * @param available                    Langfuse 数据是否可用
 * @param reason                       不可用原因
 * @param generationCount              Generation 总数
 * @param validUsageCount              带有效 provider usage 的 Generation 数
 * @param tokenDataCompleteness        有效 usage 完整率
 * @param inputTokensP50               输入 Token P50
 * @param inputTokensP95               输入 Token P95
 * @param outputTokensP50              输出 Token P50
 * @param outputTokensP95              输出 Token P95
 * @param ttftMsP50                    首 Token 时延 P50
 * @param ttftMsP95                    首 Token 时延 P95
 * @param templateOverheadTokensP50    Chat Template 开销 P50
 * @param truncated                    是否因分页上限而截断样本
 */
public record ModelObservabilityOverviewVO(boolean available, String reason, long generationCount,
                                           long validUsageCount, Double tokenDataCompleteness,
                                           Integer inputTokensP50, Integer inputTokensP95,
                                            Integer outputTokensP50, Integer outputTokensP95,
                                            Integer ttftMsP50, Integer ttftMsP95,
                                            Integer templateOverheadTokensP50, boolean truncated) {

    /**
     * 兼容未感知分页截断标识的既有调用方。
     */
    public ModelObservabilityOverviewVO(boolean available, String reason, long generationCount,
                                        long validUsageCount, Double tokenDataCompleteness,
                                        Integer inputTokensP50, Integer inputTokensP95,
                                        Integer outputTokensP50, Integer outputTokensP95,
                                        Integer ttftMsP50, Integer ttftMsP95,
                                        Integer templateOverheadTokensP50) {
        this(available, reason, generationCount, validUsageCount, tokenDataCompleteness,
                inputTokensP50, inputTokensP95, outputTokensP50, outputTokensP95,
                ttftMsP50, ttftMsP95, templateOverheadTokensP50, false);
    }

    /**
     * 创建 Langfuse 不可用时的安全响应。
     *
     * @return 空指标响应
     */
    public static ModelObservabilityOverviewVO unavailable() {
        return new ModelObservabilityOverviewVO(false, LANGFUSE_UNAVAILABLE_REASON, 0, 0, null,
                null, null, null, null, null, null, null, false);
    }
}
