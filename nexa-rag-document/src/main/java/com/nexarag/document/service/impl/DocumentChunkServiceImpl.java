package com.nexarag.document.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexarag.document.entity.DocumentChunk;
import com.nexarag.document.mapper.DocumentChunkMapper;
import com.nexarag.document.service.DocumentChunkService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文档片段服务实现类，负责文档片段查询和持久化。
 */
@Service
public class DocumentChunkServiceImpl extends ServiceImpl<DocumentChunkMapper, DocumentChunk>
        implements DocumentChunkService {

    @Override
    public List<DocumentChunk> listByDocumentId(Long documentId) {
        // 1. 使用 lambdaQuery 按文档ID和片段顺序查询
        return this.lambdaQuery()
                .eq(DocumentChunk::getDocumentId, documentId)
                .orderByAsc(DocumentChunk::getChunkOrder)
                .list();
    }
}
