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
     * 用当前文档的完整片段替换其已有向量记录。
     *
     * @param documentId 文档ID
     * @param chunks 当前待索引片段
     * @return 每个片段的写入结果
     */
    List<VectorIndexWriteResult> replaceDocument(Long documentId, List<IndexableChunk> chunks);

    /**
     * 按文本查询相似片段。
     *
     * @param query 查询文本
     * @param topK 最大候选数量
     * @return 业务片段检索结果
     */
    List<VectorIndexSearchResult> search(String query, int topK);

    /**
     * 删除指定文档的全部向量记录。
     *
     * @param documentId 文档ID
     */
    void deleteByDocumentId(Long documentId);
}
