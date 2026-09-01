package com.nexarag.retrieval.service;

/**
 * 文档历史版本索引清理器，按文档ID和版本ID删除所有派生检索索引。
 */
public interface DocumentVersionIndexCleaner {

    /** 清理指定文档版本的向量、正文关键词和章节导航索引。 */
    void cleanup(Long documentId, Long documentVersionId);
}
