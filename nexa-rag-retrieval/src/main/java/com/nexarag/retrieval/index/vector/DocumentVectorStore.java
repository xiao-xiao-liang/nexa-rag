package com.nexarag.retrieval.index.vector;

import com.nexarag.retrieval.model.IndexableChunk;
import com.nexarag.retrieval.model.VectorIndexSearchResult;
import com.nexarag.retrieval.model.VectorIndexWriteResult;

import java.util.List;

/**
 * 面向文档片段的向量存储边界，禁止上层直接传入或处理原始向量。
 */
public interface DocumentVectorStore {

    /**
     * 用指定文档版本的完整片段替换其已有向量记录，不影响同一文档的历史版本。
     *
     * @param documentId        文档ID
     * @param documentVersionId 文档版本ID
     * @param chunks            当前待索引片段
     * @return 每个片段的写入结果
     */
    default List<VectorIndexWriteResult> replaceDocumentVersion(Long documentId, Long documentVersionId,
                                                                List<IndexableChunk> chunks) {
        throw new UnsupportedOperationException("当前向量存储不支持按文档版本替换索引");
    }

    /**
     * 按文本查询相似片段。
     *
     * @param query 查询文本
     * @param topK  最大候选数量
     * @return 业务片段检索结果
     */
    List<VectorIndexSearchResult> search(String query, int topK);

    default List<VectorIndexSearchResult> search(String query, int topK, java.util.Set<Long> activeVersionIds) {
        return search(query, topK);
    }

    /**
     * 删除指定文档版本的全部向量记录。
     *
     * @param documentId        文档ID
     * @param documentVersionId 文档版本ID
     */
    default void deleteByDocumentVersionId(Long documentId, Long documentVersionId) {
        throw new UnsupportedOperationException("当前向量存储不支持按文档版本删除索引");
    }
}
