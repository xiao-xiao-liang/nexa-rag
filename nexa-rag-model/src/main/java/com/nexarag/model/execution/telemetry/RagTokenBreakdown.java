package com.nexarag.model.execution.telemetry;

import com.nexarag.model.gateway.chat.ChatModelMessage;

import java.util.List;

/**
 * 最终回答请求的 RAG 语义分段 Token 快照。
 *
 * <p>文本仅用于同一进程内调用 vLLM {@code /tokenize}，不得作为 Langfuse 属性、日志或查询接口字段。</p>
 *
 * @param systemContent           系统提示词正文
 * @param summaryContent          会话摘要正文
 * @param historyMessages         历史消息正文
 * @param questionContent         最终问题正文
 * @param retrievalContent        最终接纳证据正文
 * @param toolContent             工具执行状态正文
 * @param inputBudgetTokens       当前模型的输入 Token 预算
 * @param retrievalCandidateCount 证据候选数
 * @param retrievalAcceptedCount  接纳证据数
 * @param retrievalSkippedCount   跳过证据数
 * @param systemTokens            系统提示词精确 Token 数
 * @param summaryTokens           会话摘要精确 Token 数
 * @param historyTokens           历史消息精确 Token 数
 * @param questionTokens          最终问题精确 Token 数
 * @param retrievalTokens         最终接纳证据精确 Token 数
 * @param toolTokens              工具执行状态精确 Token 数
 * @param finalPromptTokens       模型服务返回的最终输入 Token 数
 * @param status                  分段统计终态
 */
public record RagTokenBreakdown(
        String systemContent,
        String summaryContent,
        List<ChatModelMessage> historyMessages,
        String questionContent,
        String retrievalContent,
        String toolContent,
        Integer inputBudgetTokens,
        Integer retrievalCandidateCount,
        Integer retrievalAcceptedCount,
        Integer retrievalSkippedCount,
        Integer systemTokens,
        Integer summaryTokens,
        Integer historyTokens,
        Integer questionTokens,
        Integer retrievalTokens,
        Integer toolTokens,
        Integer finalPromptTokens,
        RagTokenBreakdownStatus status) {

    /**
     * 计算 Chat Template 与消息角色包装产生的额外输入 Token。
     *
     * @return 全部语义分段与最终输入 Token 都已知时返回差额，否则返回 {@code null}
     */
    public Integer templateOverheadTokens() {
        if (finalPromptTokens == null || systemTokens == null || summaryTokens == null || historyTokens == null
                || questionTokens == null || retrievalTokens == null || toolTokens == null) {
            return null;
        }
        return finalPromptTokens - systemTokens - summaryTokens - historyTokens - questionTokens
                - retrievalTokens - toolTokens;
    }

    /**
     * 基于 vLLM Tokenizer 的返回值创建已统计的新快照。
     *
     * @param systemTokens    系统提示词 Token 数
     * @param summaryTokens   会话摘要 Token 数
     * @param historyTokens   历史消息 Token 数
     * @param questionTokens  问题 Token 数
     * @param retrievalTokens 接纳证据 Token 数
     * @param toolTokens      工具状态 Token 数
     * @return 带精确 Token 结果的新快照
     */
    public RagTokenBreakdown withExactTokenCounts(Integer systemTokens, Integer summaryTokens, Integer historyTokens,
                                                  Integer questionTokens, Integer retrievalTokens,
                                                  Integer toolTokens) {
        int availableCount = countAvailable(systemTokens, summaryTokens, historyTokens, questionTokens,
                retrievalTokens, toolTokens);
        RagTokenBreakdownStatus resolvedStatus = availableCount == 6 ? RagTokenBreakdownStatus.COMPLETE
                : availableCount == 0 ? RagTokenBreakdownStatus.UNAVAILABLE : RagTokenBreakdownStatus.PARTIAL;
        return new RagTokenBreakdown(systemContent, summaryContent, historyMessages, questionContent,
                retrievalContent, toolContent, inputBudgetTokens, retrievalCandidateCount, retrievalAcceptedCount,
                retrievalSkippedCount, systemTokens, summaryTokens, historyTokens, questionTokens, retrievalTokens,
                toolTokens, finalPromptTokens, resolvedStatus);
    }

    /**
     * 写入模型服务最终返回的输入 Token 数。
     *
     * @param finalPromptTokens vLLM {@code usage.prompt_tokens}
     * @return 带最终输入 Token 数的新快照
     */
    public RagTokenBreakdown withFinalPromptTokens(Integer finalPromptTokens) {
        return new RagTokenBreakdown(systemContent, summaryContent, historyMessages, questionContent,
                retrievalContent, toolContent, inputBudgetTokens, retrievalCandidateCount, retrievalAcceptedCount,
                retrievalSkippedCount, systemTokens, summaryTokens, historyTokens, questionTokens, retrievalTokens,
                toolTokens, finalPromptTokens, status);
    }

    private int countAvailable(Integer... values) {
        int count = 0;
        for (Integer value : values) {
            if (value != null) {
                count++;
            }
        }
        return count;
    }
}
