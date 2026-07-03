package com.nexarag.document.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexarag.document.entity.DocumentChunk;

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
}
