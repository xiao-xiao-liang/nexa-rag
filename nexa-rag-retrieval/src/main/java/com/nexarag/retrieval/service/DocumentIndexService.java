package com.nexarag.retrieval.service;

import com.nexarag.retrieval.dto.res.DocumentIndexResult;

/**
 * 文档索引服务，对 Workflow 暴露文档索引写入和索引清理入口。
 */
public interface DocumentIndexService {

    /**
     * 执行指定文档版本的索引写入。该入口以版本和处理轮次状态为准，不影响历史版本索引。
     *
     * @param documentId        文档ID
     * @param documentVersionId 文档版本ID
     * @return 文档索引结果
     */
    DocumentIndexResult indexDocument(Long documentId, Long documentVersionId);

    /**
     * 重新写入已就绪版本的索引元数据，不改变版本状态或当前生效指针。
     *
     * <p>仅用于受控回填已迁移历史数据的 `documentVersionId` 元数据。</p>
     */
    DocumentIndexResult rebuildDocumentVersionIndex(Long documentId, Long documentVersionId);

}
