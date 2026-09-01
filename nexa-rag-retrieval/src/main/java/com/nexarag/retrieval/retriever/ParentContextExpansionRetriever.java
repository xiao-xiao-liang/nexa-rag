package com.nexarag.retrieval.retriever;

import com.nexarag.document.model.entity.DocumentChunk;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.model.RetrievalChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 父子片段上下文扩展器。
 *
 * <p>该组件只消费重排序后的子片段：命中足够集中且父片段可纳入证据预算时，返回完整父片段；
 * 否则保留命中片段并补充其相邻兄弟片段。最终 Token 预算仍由工作流的证据质量评估器统一控制。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ParentContextExpansionRetriever {

    public static final String PARENT_CONTEXT_CHANNEL = "PARENT_CONTEXT";
    public static final String PARENT_NEIGHBOR_CHANNEL = "PARENT_NEIGHBOR";

    private static final int CHARACTERS_PER_TOKEN = 4;

    private final DocumentChunkService documentChunkService;
    private final RetrievalProperties retrievalProperties;

    /**
     * 对重排序结果执行父子上下文扩展。
     *
     * @param rankedChunks 已按相关度排序的候选片段
     * @return 已去重且可供证据质量阶段继续筛选的片段
     */
    public List<RetrievalChunk> expand(List<RetrievalChunk> rankedChunks) {
        if (rankedChunks == null || rankedChunks.isEmpty()
                || !retrievalProperties.getCandidate().isParentContextExpansionEnabled()) {
            return rankedChunks == null ? List.of() : rankedChunks;
        }

        Map<String, List<RetrievalChunk>> groupedHits = rankedChunks.stream()
                .filter(chunk -> chunk != null && StringUtils.hasText(chunk.parentChunkId()))
                .collect(Collectors.groupingBy(RetrievalChunk::parentChunkId, LinkedHashMap::new, Collectors.toList()));
        if (groupedHits.isEmpty()) {
            return rankedChunks;
        }

        // 1. 批量读取父片段及其子片段，避免按命中逐条访问数据库。
        Set<String> parentChunkIds = groupedHits.keySet();
        Map<String, DocumentChunk> parents = documentChunkService.listByIds(parentChunkIds).stream()
                .filter(parent -> parent != null && StringUtils.hasText(parent.getChunkId()))
                .collect(Collectors.toMap(DocumentChunk::getChunkId, parent -> parent));
        Map<String, List<DocumentChunk>> childrenByParent = documentChunkService.listByParentChunkIds(List.copyOf(parentChunkIds))
                .stream()
                .filter(child -> child != null && StringUtils.hasText(child.getParentChunkId()))
                .collect(Collectors.groupingBy(DocumentChunk::getParentChunkId, LinkedHashMap::new, Collectors.toList()));

        // 2. 保持 Rerank 的父组先后顺序，并按父组一次性展开，避免重复注入同一上下文。
        List<RetrievalChunk> result = new ArrayList<>();
        Set<String> processedParents = new LinkedHashSet<>();
        Set<String> addedChunkIds = new LinkedHashSet<>();
        for (RetrievalChunk rankedChunk : rankedChunks) {
            if (rankedChunk == null) {
                continue;
            }
            if (!StringUtils.hasText(rankedChunk.parentChunkId())) {
                addIfAbsent(result, addedChunkIds, rankedChunk);
                continue;
            }
            String parentChunkId = rankedChunk.parentChunkId();
            if (!processedParents.add(parentChunkId)) {
                continue;
            }
            List<RetrievalChunk> parentHits = groupedHits.getOrDefault(parentChunkId, List.of());
            DocumentChunk parent = parents.get(parentChunkId);
            List<RetrievalChunk> versionMatchedParentHits = parentHits.stream()
                    .filter(hit -> belongsToSameVersion(parent, hit))
                    .toList();
            if (shouldUseFullParent(parent, versionMatchedParentHits.size())) {
                addIfAbsent(result, addedChunkIds, toParentContext(parent, versionMatchedParentHits));
                continue;
            }
            appendHitAndNeighbors(result, addedChunkIds, parentHits,
                    childrenByParent.getOrDefault(parentChunkId, List.of()));
        }
        log.info("父子上下文扩展完成，重排序候选数={}，父组数={}，扩展后候选数={}",
                rankedChunks.size(), groupedHits.size(), result.size());
        return List.copyOf(result);
    }

    private boolean shouldUseFullParent(DocumentChunk parent, int hitCount) {
        if (parent == null || !StringUtils.hasText(parent.getText())) {
            return false;
        }
        int parentTokens = estimateTokens(parent.getText());
        RetrievalProperties.Candidate candidate = retrievalProperties.getCandidate();
        if (parentTokens <= candidate.getParentContextFullParentMaxTokens()) {
            return true;
        }
        return hitCount >= candidate.getParentContextFullParentMinimumHits()
                && parentTokens <= candidate.getEvidenceTokenBudget();
    }

    private RetrievalChunk toParentContext(DocumentChunk parent, List<RetrievalChunk> parentHits) {
        RetrievalChunk highestRankedHit = parentHits.getFirst();
        double score = parentHits.stream().mapToDouble(RetrievalChunk::score).max().orElse(highestRankedHit.score());
        return new RetrievalChunk(parent.getChunkId(), parent.getDocumentId(), parent.getChunkOrder(), null,
                highestRankedHit.title(), highestRankedHit.source(), parent.getText(), score,
                PARENT_CONTEXT_CHANNEL, highestRankedHit.rank(), parent.getDocumentVersionId());
    }

    private void appendHitAndNeighbors(List<RetrievalChunk> result, Set<String> addedChunkIds,
                                       List<RetrievalChunk> parentHits, List<DocumentChunk> siblings) {
        for (RetrievalChunk parentHit : parentHits) {
            addIfAbsent(result, addedChunkIds, parentHit);
            List<DocumentChunk> orderedSiblings = siblings.stream()
                    .filter(sibling -> belongsToSameVersion(sibling, parentHit))
                    .sorted(Comparator.comparing(DocumentChunk::getChunkOrder, Comparator.nullsLast(Integer::compareTo)))
                    .toList();
            Map<String, Integer> siblingIndexes = new LinkedHashMap<>();
            orderedSiblings.forEach(sibling -> siblingIndexes.put(sibling.getChunkId(), siblingIndexes.size()));
            Integer siblingIndex = siblingIndexes.get(parentHit.chunkId());
            if (siblingIndex == null) {
                continue;
            }
            int window = retrievalProperties.getCandidate().getParentContextNeighborWindowCount();
            int start = Math.max(0, siblingIndex - window);
            int end = Math.min(orderedSiblings.size() - 1, siblingIndex + window);
            for (int index = start; index <= end; index++) {
                DocumentChunk sibling = orderedSiblings.get(index);
                if (sibling.getChunkId().equals(parentHit.chunkId())) {
                    continue;
                }
                addIfAbsent(result, addedChunkIds, toNeighborContext(sibling, parentHit));
            }
        }
    }

    private RetrievalChunk toNeighborContext(DocumentChunk sibling, RetrievalChunk anchor) {
        return new RetrievalChunk(sibling.getChunkId(), sibling.getDocumentId(), sibling.getChunkOrder(),
                sibling.getParentChunkId(), anchor.title(), anchor.source(), sibling.getText(), anchor.score(),
                PARENT_NEIGHBOR_CHANNEL, anchor.rank(), sibling.getDocumentVersionId());
    }

    private void addIfAbsent(List<RetrievalChunk> target, Set<String> addedChunkIds, RetrievalChunk chunk) {
        String identity = StringUtils.hasText(chunk.chunkId())
                ? chunk.chunkId() + ":" + chunk.documentVersionId()
                : chunk.documentId() + ":" + chunk.documentVersionId() + ":" + chunk.content().hashCode();
        if (addedChunkIds.add(identity)) {
            target.add(chunk);
        }
    }

    private int estimateTokens(String text) {
        return Math.max(1, (text.length() + CHARACTERS_PER_TOKEN - 1) / CHARACTERS_PER_TOKEN);
    }

    private boolean belongsToSameVersion(DocumentChunk chunk, RetrievalChunk hit) {
        return chunk != null && hit != null && chunk.getDocumentVersionId() != null
                && chunk.getDocumentVersionId().equals(hit.documentVersionId());
    }
}
