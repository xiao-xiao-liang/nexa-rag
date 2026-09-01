package com.nexarag.retrieval.repository;

import com.nexarag.retrieval.model.SectionNavigationHit;

import java.util.List;
import java.util.Set;

/**
 * 章节导航索引仓储，维护独立于正文证据索引的章节范围检索数据。
 */
public interface SectionNavigationIndexRepository {

    /**
     * 写入指定文档版本的章节导航索引。
     */
    void upsert(Long documentId, Long documentVersionId);

    /**
     * 删除指定文档版本的章节导航索引。
     */
    int deleteByDocumentVersionId(Long documentId, Long documentVersionId);

    /**
     * 查询指定生效版本范围内的章节导航命中。
     *
     * @param query            查询文本
     * @param limit            返回数量
     * @param activeVersionIds 当前生效的文档版本ID集合
     * @return 章节导航命中列表
     */
    List<SectionNavigationHit> search(String query, int limit, Set<Long> activeVersionIds);
}
