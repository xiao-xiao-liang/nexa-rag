package com.nexarag.retrieval.repository;

import com.nexarag.document.model.bo.DocumentChunkIndexWriteBO;
import com.nexarag.document.model.entity.DocumentChunk;
import com.nexarag.retrieval.model.DocumentVersionChunkIndexContext;
import com.nexarag.retrieval.model.IndexableChunk;

import java.util.List;

/**
 * 文档片段索引仓储，封装 retrieval 模块对 document chunk 的查询与回写边界。
 */
public interface ChunkIndexRepository {

    /**
     * 一次读取指定版本的全部片段，并拆分待索引与跳过索引集合。
     */
    DocumentVersionChunkIndexContext loadIndexContext(Long documentId, Long documentVersionId);

    /**
     * 查询指定文档版本中需要写入索引的片段。
     *
     * @param documentId        文档ID
     * @param documentVersionId 文档版本ID
     * @return 可索引片段列表
     */
    List<IndexableChunk> listIndexableChunks(Long documentId, Long documentVersionId);

    /**
     * 查询指定文档版本中跳过索引的片段。
     */
    List<DocumentChunk> listSkippedChunks(Long documentId, Long documentVersionId);

    /**
     * 标记指定文档版本中的跳过索引片段。
     *
     * @param documentId        文档ID
     * @param documentVersionId 文档版本ID
     */
    void markSkipped(Long documentId, Long documentVersionId);

    /**
     * 标记片段索引成功。
     *
     * @param chunkId        片段ID
     * @param vectorId       向量索引ID
     * @param keywordIndexId 关键词索引ID
     */
    void markIndexed(String chunkId, String vectorId, String keywordIndexId);

    /**
     * 批量标记片段索引成功。
     */
    void batchMarkIndexed(List<DocumentChunkIndexWriteBO> chunks);

    /**
     * 标记片段索引失败。
     *
     * @param chunkId       片段ID
     * @param failureReason 失败原因
     */
    void markFailed(String chunkId, String failureReason);

}
