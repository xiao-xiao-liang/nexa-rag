package com.nexarag.retrieval.model;

/**
 * 章节导航命中，仅用于确定文档和章节范围，不能直接作为回答证据。
 *
 * @param sectionId  章节ID
 * @param documentId 文档ID
 * @param score      导航相关度分数
 * @param channel    命中通道
 */
public record SectionNavigationHit(
        Long sectionId,
        Long documentId,
        Long documentVersionId,
        double score,
        String channel
) {
    public SectionNavigationHit(Long sectionId, Long documentId, double score, String channel) {
        this(sectionId, documentId, null, score, channel);
    }
}
