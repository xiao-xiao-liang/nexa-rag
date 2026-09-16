package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.nexarag.infra.observability.langfuse.aop.LangfuseSpan;
import com.nexarag.infra.observability.langfuse.model.LangfuseObservationType;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.retrieval.retriever.ParentContextExpansionRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.*;
import static com.nexarag.workflow.constants.ChatWorkflowTelemetryConstant.*;

/**
 * 重排序后父子上下文扩展节点。
 *
 * <p>本节点保留替换前的直接命中子片段，供回答节点在完整父片段超出模型窗口时定向回退，
 * 不会扩展为未经重排序的兄弟片段。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ParentContextExpansionNode implements NodeAction {

    private final ParentContextExpansionRetriever parentContextExpansionRetriever;

    /**
     * 将命中的子片段扩展为完整父片段或相邻兄弟片段。
     *
     * @param state Workflow 当前状态
     * @return 替换后的重排序结果及原始直接命中子片段
     */
    @Override
    @LangfuseSpan(name = PARENT_CONTEXT_EXPANSION_SPAN_NAME, type = LangfuseObservationType.RETRIEVER,
            parentContextCarrier = STATE_CARRIER_EXPRESSION,
            attributes = PARENT_CONTEXT_INPUT_COUNT_ATTRIBUTE + "=" + STATE_RERANKED_COUNT_EXPRESSION,
            resultAttributes = PARENT_CONTEXT_OUTPUT_COUNT_ATTRIBUTE + "=" + RESULT_PARENT_CONTEXT_OUTPUT_COUNT_EXPRESSION)
    public Map<String, Object> apply(OverAllState state) {
        List<RetrievalChunk> rankedChunks = state.value(RERANKED_RETRIEVAL_RESULTS, List.of());
        List<RetrievalChunk> expandedChunks = parentContextExpansionRetriever.expand(rankedChunks);
        log.info("父子上下文节点完成，traceId={}，重排序候选数={}，扩展后候选数={}",
                state.value(TRACE_ID, ""), rankedChunks.size(), expandedChunks.size());
        return Map.of(RERANKED_RETRIEVAL_RESULTS, expandedChunks,
                PARENT_CONTEXT_FALLBACK_RESULTS, List.copyOf(rankedChunks));
    }
}
