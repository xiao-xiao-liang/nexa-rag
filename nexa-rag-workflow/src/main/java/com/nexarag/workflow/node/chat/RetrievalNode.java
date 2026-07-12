package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.nexarag.retrieval.chat.ConversationRetrievalService;
import com.nexarag.retrieval.chat.model.ConversationRetrievalRequest;
import com.nexarag.retrieval.chat.model.IntentRecognitionResult;
import com.nexarag.retrieval.chat.model.RetrievalScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.INTENT_RESULT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RAW_RETRIEVAL_RESULTS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_ROUND;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_SCOPE;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_TOP_K;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_VECTOR_THRESHOLD;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.REWRITTEN_QUESTION;

/**
 * 对话检索节点，负责按当前轮次参数调用混合检索服务。
 */
@Component
@RequiredArgsConstructor
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
        return Map.of(RAW_RETRIEVAL_RESULTS, retrievalService.retrieve(request));
    }
}
