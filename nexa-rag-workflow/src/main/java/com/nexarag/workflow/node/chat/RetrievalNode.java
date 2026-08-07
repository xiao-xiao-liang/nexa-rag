package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.fastjson2.JSON;
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

    @Override
    public Map<String, Object> apply(OverAllState state) {
        var request = new ConversationRetrievalRequest(
                state.value(REWRITTEN_QUESTION, ""),
                state.value(INTENT_RESULT, new IntentRecognitionResult(java.util.List.of(), 0D)),
                state.value(RETRIEVAL_SCOPE, RetrievalScope.INTENT),
                state.value(RETRIEVAL_TOP_K, 10),
                state.value(RETRIEVAL_VECTOR_THRESHOLD, 0.5D),
                state.value(RETRIEVAL_ROUND, 1));
        List<RetrievalChunk> results = retrievalService.retrieve(request);
        // 1. 输出检索范围、命中数量和前十个片段元数据，不记录片段正文
        log.info("知识库检索完成，当前检索轮次：{}，scope={}，topK：{}，相似度阈值={}，检索数：{}",
                request.round(), request.scope(), request.topK(), request.vectorThreshold(), results.size());
        log.debug("知识库检索完整结果：\n{}", JSON.toJSONString(results));
        return Map.of(RAW_RETRIEVAL_RESULTS, results);
    }
}
