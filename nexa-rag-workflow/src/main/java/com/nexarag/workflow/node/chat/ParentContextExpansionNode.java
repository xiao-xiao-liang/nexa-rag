package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.retrieval.retriever.ParentContextExpansionRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RERANKED_RETRIEVAL_RESULTS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TRACE_ID;

/**
 * 重排序后父子上下文扩展节点。
 *
 * <p>本节点只重组已经完成相关性排序的正文证据，随后仍由证据质量节点统一执行 Token 预算控制。</p>
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
     * @return 替换后的重排序结果
     */
    @Override
    public Map<String, Object> apply(OverAllState state) {
        List<RetrievalChunk> rankedChunks = state.value(RERANKED_RETRIEVAL_RESULTS, List.of());
        List<RetrievalChunk> expandedChunks = parentContextExpansionRetriever.expand(rankedChunks);
        log.info("父子上下文节点完成，traceId={}，重排序候选数={}，扩展后候选数={}",
                state.value(TRACE_ID, ""), rankedChunks.size(), expandedChunks.size());
        return Map.of(RERANKED_RETRIEVAL_RESULTS, expandedChunks);
    }
}
