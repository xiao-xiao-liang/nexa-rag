package com.nexarag.retrieval.dto.req;

import com.nexarag.retrieval.enums.RetrievalScope;
import com.nexarag.retrieval.dto.res.IntentRecognitionResult;

/**
 * 对话检索请求。
 *
 * @param question 改写后的问题
 * @param intentResult 意图识别结果
 * @param scope 检索范围
 * @param topK 单通道候选数量
 * @param vectorThreshold 向量相似度阈值
 * @param round 检索轮次
 */
public record ConversationRetrievalRequest(String question, IntentRecognitionResult intentResult,
                                           RetrievalScope scope, int topK, double vectorThreshold, int round) {
}
