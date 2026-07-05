package com.nexarag.document.service;

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
}
