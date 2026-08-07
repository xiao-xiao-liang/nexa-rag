package com.nexarag.document.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.nexarag.document.entity.DocumentChunk;
import com.nexarag.document.splitter.ChunkDraft;

import java.util.List;

/**
 * 文档片段服务接口。
 */
public interface DocumentChunkService extends IService<DocumentChunk> {

    /**
     * 根据文档ID查询片段。
     *
     * @param documentId 文档ID
     * @return 文档片段列表
     */
    List<DocumentChunk> listByDocumentId(Long documentId);

    /**
     * 分页查询指定文档的片段。
     *
     * @param documentId 文档ID
     * @param pageNum    页码
     * @param pageSize   每页数量
     * @return 文档片段分页数据
     */
    IPage<DocumentChunk> pageByDocumentId(Long documentId, long pageNum, long pageSize);

    /**
     * 替换指定文档的片段。
     *
     * @param documentId 文档ID
     * @param drafts     片段草稿
     * @return 保存后的片段列表
     */
    List<DocumentChunk> replaceDocumentChunks(Long documentId, List<ChunkDraft> drafts);

    /**
     * 统计指定文档的片段数量。
     *
     * @param documentId 文档ID
     * @return 片段数量
     */
    long countByDocumentId(Long documentId);

    /**
     * 标记片段索引成功并回写索引ID。
     *
     * @param chunkId        片段ID
     * @param vectorId       向量索引ID
     * @param keywordIndexId 关键词索引ID
     */
    void markChunkIndexed(String chunkId, String vectorId, String keywordIndexId);

    /**
     * 标记片段索引失败。
     *
     * @param chunkId        片段ID
     * @param failureReason 失败原因
     */
    void markChunkIndexFailed(String chunkId, String failureReason);

    /**
     * 标记指定文档中需要跳过索引的片段。
     *
     * @param documentId 文档ID
     */
    void markDocumentSkippedChunks(Long documentId);
}
