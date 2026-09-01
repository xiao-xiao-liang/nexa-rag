package com.nexarag.document.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.nexarag.document.model.bo.DocumentChunkIndexWriteBO;
import com.nexarag.document.model.bo.split.ChunkDraft;
import com.nexarag.document.model.entity.DocumentChunk;

import java.util.List;

/**
 * 文档片段服务接口。
 */
public interface DocumentChunkService extends IService<DocumentChunk> {

    /**
     * 根据文档版本ID查询片段。
     *
     * @param documentVersionId 文档版本ID
     * @return 文档版本片段列表
     */
    List<DocumentChunk> listByDocumentVersionId(Long documentVersionId);

    /**
     * 根据父片段ID批量查询子片段，并按文档内片段顺序排序。
     *
     * @param parentChunkIds 父片段ID集合
     * @return 子片段列表
     */
    List<DocumentChunk> listByParentChunkIds(List<String> parentChunkIds);

    /**
     * 分页查询指定文档版本的片段。
     *
     * @param documentVersionId 文档版本ID
     * @param pageNum           页码
     * @param pageSize          每页数量
     * @return 文档版本片段分页数据
     */
    IPage<DocumentChunk> pageByDocumentVersionId(Long documentVersionId, long pageNum, long pageSize);

    /**
     * 替换指定文档版本的片段，不影响该文档的其他历史版本。
     *
     * @param documentId        文档ID
     * @param documentVersionId 文档版本ID
     * @param drafts            片段草稿
     * @return 保存后的片段列表
     */
    List<DocumentChunk> replaceDocumentVersionChunks(Long documentId, Long documentVersionId,
                                                     List<ChunkDraft> drafts);

    /**
     * 永久删除指定文档版本的片段。
     *
     * @param documentVersionId 文档版本ID
     */
    void deleteByDocumentVersionId(Long documentVersionId);

    /**
     * 保存指定文档版本的新片段。
     *
     * @param documentId        文档ID
     * @param documentVersionId 文档版本ID
     * @param drafts            片段草稿
     * @return 保存后的片段列表
     */
    List<DocumentChunk> saveDocumentVersionChunks(Long documentId, Long documentVersionId, List<ChunkDraft> drafts);

    /**
     * 标记片段索引成功并回写索引ID。
     *
     * @param chunkId        片段ID
     * @param vectorId       向量索引ID
     * @param keywordIndexId 关键词索引ID
     */
    void markChunkIndexed(String chunkId, String vectorId, String keywordIndexId);

    /**
     * 批量标记片段索引成功并回写索引ID。
     *
     * @param chunks 待回写的片段集合
     */
    void batchMarkChunksIndexed(List<DocumentChunkIndexWriteBO> chunks);

    /**
     * 标记片段索引失败。
     *
     * @param chunkId       片段ID
     * @param failureReason 失败原因
     */
    void markChunkIndexFailed(String chunkId, String failureReason);

    /**
     * 标记指定文档版本中需要跳过索引的片段，不影响历史或其他构建中的版本。
     *
     * @param documentVersionId 文档版本ID
     */
    void markDocumentVersionSkippedChunks(Long documentVersionId);
}
