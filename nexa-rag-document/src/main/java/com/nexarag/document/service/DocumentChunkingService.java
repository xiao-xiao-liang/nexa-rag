package com.nexarag.document.service;

/**
 * 文档切分阶段服务。
 */
public interface DocumentChunkingService {

    /**
     * 执行指定文档版本的切分阶段。
     *
     * @param documentId        文档ID
     * @param documentVersionId 文档版本ID
     * @return 保存的片段数量
     */
    int chunk(Long documentId, Long documentVersionId);
}
