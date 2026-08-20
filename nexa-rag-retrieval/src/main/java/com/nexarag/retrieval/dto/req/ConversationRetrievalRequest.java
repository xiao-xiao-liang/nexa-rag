package com.nexarag.retrieval.dto.req;

import com.nexarag.retrieval.enums.RetrievalScope;
import com.nexarag.retrieval.dto.res.IntentRecognitionResult;

import java.util.List;

/**
 * 对话检索请求。
 *
 * @param question 改写后的问题
 * @param intentResult 意图识别结果
 * @param scope 检索范围
 * @param topK 单通道候选数量
 * @param vectorThreshold 向量相似度阈值
 * @param round 检索轮次
 * @param knowledgeBaseIds 可选知识库范围；为空时检索当前租户全部知识库
 */
public record ConversationRetrievalRequest(String question, IntentRecognitionResult intentResult,
                                           RetrievalScope scope, int topK, double vectorThreshold, int round,
                                           List<Long> knowledgeBaseIds) {

    /** 创建未限定知识库范围的检索请求。 */
    public ConversationRetrievalRequest(String question, IntentRecognitionResult intentResult,
                                        RetrievalScope scope, int topK, double vectorThreshold, int round) {
        this(question, intentResult, scope, topK, vectorThreshold, round, List.of());
    }
}
