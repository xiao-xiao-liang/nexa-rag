package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.retrieval.retriever.SectionExpansionRetriever;
import com.nexarag.document.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.EVIDENCE_EXPANSION_REASON;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.FUSED_RETRIEVAL_RESULTS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.REWRITTEN_QUESTION;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TRACE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_KNOWLEDGE_BASE_IDS;

/**
 * 章节扩展节点，根据导航范围补充正文片段并返回重排序节点；导航标题不会写入证据状态。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SectionExpansionNode implements NodeAction {

    private final SectionExpansionRetriever sectionExpansionRetriever;
    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 补充受章节范围限制的正文，并与初始候选去重合并。
     *
     * @param state Workflow 当前状态
     * @return 更新后的候选集
     */
    @Override
    public Map<String, Object> apply(OverAllState state) {
        // 1. 读取初始候选和触发原因
        List<RetrievalChunk> initialChunks = state.value(FUSED_RETRIEVAL_RESULTS, List.of());
        Set<Long> knowledgeBaseIds = knowledgeBaseService.validateRequestedKnowledgeBases(
                state.value(RETRIEVAL_KNOWLEDGE_BASE_IDS, List.of()));
        List<RetrievalChunk> expandedChunks = sectionExpansionRetriever.retrieve(state.value(REWRITTEN_QUESTION, ""))
                .stream()
                .filter(chunk -> knowledgeBaseService.isDocumentInCurrentTenantScope(chunk.documentId(), knowledgeBaseIds))
                .toList();

        // 2. 将补充的原始正文优先送入重排序，同时按片段ID去重
        Map<String, RetrievalChunk> uniqueChunks = new LinkedHashMap<>();
        append(uniqueChunks, expandedChunks);
        append(uniqueChunks, initialChunks);
        List<RetrievalChunk> mergedChunks = List.copyOf(uniqueChunks.values());
        log.info("章节扩展完成，traceId={}，原因={}，初始候选数={}，补充正文数={}，合并候选数={}",
                state.value(TRACE_ID, ""), state.value(EVIDENCE_EXPANSION_REASON, "UNKNOWN"), initialChunks.size(),
                expandedChunks.size(), mergedChunks.size());
        return Map.of(FUSED_RETRIEVAL_RESULTS, mergedChunks);
    }

    private void append(Map<String, RetrievalChunk> target, List<RetrievalChunk> chunks) {
        for (RetrievalChunk chunk : chunks) {
            if (chunk == null) {
                continue;
            }
            String key = chunk.chunkId() == null ? chunk.documentId() + ":" + chunk.content().hashCode() : chunk.chunkId();
            target.putIfAbsent(key, chunk);
        }
    }
}
