package com.nexarag.workflow.model;

import com.nexarag.retrieval.model.RetrievalChunk;

import java.util.List;

/**
 * 回答证据质量判定结果，保存最终允许进入回答提示词的原始正文片段及判定原因。
 *
 * @param acceptedChunks 已接受的原始正文片段
 * @param sufficient 是否具备回答所需的最低证据
 * @param reason 判定原因
 * @param estimatedTokenCount 已接受正文的估算 Token 数
 */
public record EvidenceQuality(List<RetrievalChunk> acceptedChunks, boolean sufficient, String reason,
                              int estimatedTokenCount) {

    /**
     * 返回无可用证据的保守判定结果。
     *
     * @param reason 拒绝原因
     * @return 不含正文的判定结果
     */
    public static EvidenceQuality insufficient(String reason) {
        return new EvidenceQuality(List.of(), false, reason, 0);
    }
}
