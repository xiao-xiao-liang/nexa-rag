package com.nexarag.model.gateway.rerank;

import java.util.List;

/**
 * 重排序模型响应。
 *
 * @param scores       重排序分数
 * @param modelProfile 实际使用的模型Profile
 * @param totalTokens  总Token数量
 */
public record RerankModelResponse(List<RerankScore> scores, String modelProfile, Integer totalTokens) {

    /**
     * 重排序分数。
     *
     * @param id    候选ID
     * @param score 分数
     */
    public record RerankScore(String id, double score) {
    }
}
