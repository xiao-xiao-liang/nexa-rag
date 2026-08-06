package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.retrieval.service.ConversationRetrievalService;
import com.nexarag.retrieval.dto.req.ConversationRetrievalRequest;
import com.nexarag.retrieval.dto.res.IntentRecognitionResult;
import com.nexarag.retrieval.enums.RetrievalScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.INTENT_RESULT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RAW_RETRIEVAL_RESULTS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_ROUND;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_SCOPE;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_TOP_K;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_VECTOR_THRESHOLD;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.REWRITTEN_QUESTION;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TRACE_ID;

/**
 * 对话检索节点，负责按当前轮次参数调用混合检索服务。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RetrievalNode implements NodeAction {
    private final ConversationRetrievalService retrievalService;
    private final RetrievalProperties retrievalProperties;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        var request = new ConversationRetrievalRequest(
                state.value(REWRITTEN_QUESTION, ""),
                state.value(INTENT_RESULT, new IntentRecognitionResult(java.util.List.of(), 0D)),
                state.value(RETRIEVAL_SCOPE, RetrievalScope.INTENT),
                state.value(RETRIEVAL_TOP_K, retrievalProperties.getCandidate().getVectorCandidateLimit()),
                state.value(RETRIEVAL_VECTOR_THRESHOLD, retrievalProperties.getCandidate().getCoarseScoreFloor()),
                state.value(RETRIEVAL_ROUND, 1));
        List<RetrievalChunk> results = retrievalService.retrieve(request);
        // 1. 仅记录检索范围和命中数量，不记录查询词或片段正文
        log.info("知识库检索完成，traceId={}，当前检索轮次={}，scope={}，topK={}，相似度阈值={}，检索数={}",
                state.value(TRACE_ID, ""), request.round(), request.scope(), request.topK(),
                request.vectorThreshold(), results.size());
        return Map.of(RAW_RETRIEVAL_RESULTS, results);
    }
}
