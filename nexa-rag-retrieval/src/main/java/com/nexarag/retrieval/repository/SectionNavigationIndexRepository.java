package com.nexarag.retrieval.repository;

import com.nexarag.retrieval.model.SectionNavigationHit;

import java.util.List;

/**
 * 章节导航索引仓储，维护独立于正文证据索引的章节范围检索数据。
 */
public interface SectionNavigationIndexRepository {

    /**
     * 写入指定文档的章节标题和路径导航索引。
     *
     * @param documentId 文档ID
     */
    void upsert(Long documentId);

    /**
     * 删除指定文档的章节导航索引。
     *
     * @param documentId 文档ID
     * @return 删除数量
     */
    int deleteByDocumentId(Long documentId);

    /**
     * 查询章节导航命中范围。
     *
     * @param query 查询文本
     * @param limit 返回数量
     * @return 章节导航命中列表
     */
    List<SectionNavigationHit> search(String query, int limit);
}
