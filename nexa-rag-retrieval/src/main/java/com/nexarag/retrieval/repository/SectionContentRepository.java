package com.nexarag.retrieval.repository;

import com.nexarag.retrieval.model.SectionContentChunk;

import java.util.List;

/**
 * 章节正文仓储，负责在导航确定的文档和章节范围内读取原始正文片段。
 */
public interface SectionContentRepository {

    /**
     * 查询根章节及全部后代章节中的正文片段。
     *
     * @param documentId 文档ID
     * @param rootSectionId 导航命中的根章节ID
     * @param limit 返回上限
     * @return 原始正文片段
     */
    List<SectionContentChunk> listBySectionScope(Long documentId, Long rootSectionId, int limit);
}
