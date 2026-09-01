package com.nexarag.retrieval.dto.req;

import com.nexarag.retrieval.dto.res.IntentRecognitionResult;
import com.nexarag.retrieval.enums.RetrievalScope;

import java.util.List;
import java.util.Set;

/**
 * 对话检索请求。
 *
 * @param question         改写后的问题
 * @param intentResult     意图识别结果
 * @param scope            检索范围
 * @param topK             单通道候选数量
 * @param vectorThreshold  向量相似度阈值
 * @param round            检索轮次
 * @param tenantId         已在入口处校验的可信租户ID
 * @param knowledgeBaseIds 可选知识库范围；为空时检索当前租户全部知识库
 */
public record ConversationRetrievalRequest(String question, IntentRecognitionResult intentResult,
                                           RetrievalScope scope, int topK, double vectorThreshold, int round,
                                           String tenantId, List<Long> knowledgeBaseIds, Set<Long> activeVersionIds) {

    /**
     * 创建带知识库范围但尚未进入异步工作流的检索请求。
     */
    public ConversationRetrievalRequest(String question, IntentRecognitionResult intentResult,
                                        RetrievalScope scope, int topK, double vectorThreshold, int round,
                                        List<Long> knowledgeBaseIds) {
        this(question, intentResult, scope, topK, vectorThreshold, round, null, knowledgeBaseIds, Set.of());
    }

    /**
     * 创建未限定知识库范围的检索请求。
     */
    public ConversationRetrievalRequest(String question, IntentRecognitionResult intentResult,
                                        RetrievalScope scope, int topK, double vectorThreshold, int round) {
        this(question, intentResult, scope, topK, vectorThreshold, round, null, List.of(), Set.of());
    }

    public ConversationRetrievalRequest(String question, IntentRecognitionResult intentResult, RetrievalScope scope, int topK,
                                        double vectorThreshold, int round, String tenantId, List<Long> knowledgeBaseIds) {
        this(question, intentResult, scope, topK, vectorThreshold, round, tenantId, knowledgeBaseIds, Set.of());
    }
}
