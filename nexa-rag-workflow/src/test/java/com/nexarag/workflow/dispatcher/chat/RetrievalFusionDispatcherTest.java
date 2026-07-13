package com.nexarag.workflow.dispatcher.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.nexarag.retrieval.enums.RetrievalScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowNodeConstants.RERANK_NODE;
import static com.nexarag.workflow.constants.ChatWorkflowNodeConstants.RETRIEVAL_NODE;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.FUSED_RETRIEVAL_RESULTS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.MAX_RETRIEVAL_ROUND;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_ROUND;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_SCOPE;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_TOP_K;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 检索融合路由测试，验证结果不足时仅扩召一次。
 */
class RetrievalFusionDispatcherTest {

    @Test
    void applyShouldExpandRetrievalWhenFirstRoundHasNoResult() {
        OverAllState state = stateWith(1, 2, 10);
        RetrievalFusionDispatcher dispatcher = new RetrievalFusionDispatcher();

        assertThat(dispatcher.apply(state)).isEqualTo(RETRIEVAL_NODE);
        assertThat(state.value(RETRIEVAL_ROUND, 0)).isEqualTo(2);
        assertThat(state.value(RETRIEVAL_TOP_K, 0)).isEqualTo(30);
        assertThat(state.value(RETRIEVAL_SCOPE, RetrievalScope.INTENT)).isEqualTo(RetrievalScope.INTENT_AND_GLOBAL);
    }

    @Test
    void applyShouldContinueToRerankAfterMaximumRound() {
        OverAllState state = stateWith(2, 2, 30);

        assertThat(new RetrievalFusionDispatcher().apply(state)).isEqualTo(RERANK_NODE);
    }

    private OverAllState stateWith(int round, int maxRound, int topK) {
        return new OverAllState(Map.of(
                FUSED_RETRIEVAL_RESULTS, List.of(),
                RETRIEVAL_ROUND, round,
                MAX_RETRIEVAL_ROUND, maxRound,
                RETRIEVAL_TOP_K, topK,
                RETRIEVAL_SCOPE, RetrievalScope.INTENT));
    }
}
