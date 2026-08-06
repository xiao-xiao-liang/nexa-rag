package com.nexarag.retrieval.retriever;

import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.retrieval.model.SectionContentChunk;
import com.nexarag.retrieval.model.SectionNavigationHit;
import com.nexarag.retrieval.repository.SectionContentRepository;
import com.nexarag.retrieval.repository.SectionNavigationIndexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 章节扩展检索器。导航命中仅用于确定章节范围，实际返回值始终是该范围内的原始正文片段。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SectionExpansionRetriever {

    public static final String CHANNEL = "SECTION_EXPANSION";

    private final SectionNavigationIndexRepository sectionNavigationIndexRepository;
    private final SectionContentRepository sectionContentRepository;
    private final RetrievalProperties retrievalProperties;

    /**
     * 根据问题的章节导航命中补充受限数量的正文证据。
     *
     * @param question 改写后的用户问题
     * @return 原始正文片段；不包含导航标题或路径
     */
    public List<RetrievalChunk> retrieve(String question) {
        if (!StringUtils.hasText(question)) {
            return List.of();
        }

        // 1. 导航索引仅定位候选章节，不能直接进入回答证据集合
        List<SectionNavigationHit> navigationHits = sectionNavigationIndexRepository.search(question,
                retrievalProperties.getCandidate().getExpansionCandidateLimit());
        int evidenceLimit = retrievalProperties.getCandidate().getExpansionEvidenceLimit();
        List<RetrievalChunk> result = new ArrayList<>();
        Set<String> addedChunkIds = new LinkedHashSet<>();
        for (SectionNavigationHit navigationHit : navigationHits) {
            if (result.size() >= evidenceLimit) {
                break;
            }
            int remaining = evidenceLimit - result.size();

            // 2. 使用文档ID和章节树范围读取原始正文，不推断 chunkOrder 邻接关系
            List<SectionContentChunk> contentChunks = sectionContentRepository.listBySectionScope(
                    navigationHit.documentId(), navigationHit.sectionId(), remaining);
            for (SectionContentChunk contentChunk : contentChunks) {
                if (result.size() >= evidenceLimit) {
                    break;
                }
                if (contentChunk == null || !addedChunkIds.add(contentChunk.chunkId())) {
                    continue;
                }
                result.add(new RetrievalChunk(contentChunk.chunkId(), contentChunk.documentId(), null, null,
                        null, null, contentChunk.content(), navigationHit.score(), CHANNEL, result.size() + 1));
            }
        }
        log.info("章节扩展检索完成，导航范围={}，导航命中数={}，补充正文数={}，正文上限={}",
                navigationHits.stream().map(hit -> hit.documentId() + ":" + hit.sectionId()).toList(),
                navigationHits.size(), result.size(), evidenceLimit);
        return result;
    }
}
