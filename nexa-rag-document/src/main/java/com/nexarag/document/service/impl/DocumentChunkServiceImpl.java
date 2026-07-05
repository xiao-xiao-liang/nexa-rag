package com.nexarag.document.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.entity.DocumentChunk;
import com.nexarag.document.enums.ChunkStatus;
import com.nexarag.document.error.DocumentErrorCode;
import com.nexarag.document.mapper.DocumentChunkMapper;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.document.splitter.ChunkDraft;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文档片段服务实现类，负责文档片段查询和持久化。
 */
@Service
public class DocumentChunkServiceImpl extends ServiceImpl<DocumentChunkMapper, DocumentChunk>
        implements DocumentChunkService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public List<DocumentChunk> listByDocumentId(Long documentId) {
        // 1. 使用 lambdaQuery 按文档ID和片段顺序查询
        return this.lambdaQuery()
                .eq(DocumentChunk::getDocumentId, documentId)
                .orderByAsc(DocumentChunk::getChunkOrder)
                .list();
    }

    @Override
    public List<DocumentChunk> replaceDocumentChunks(Long documentId, List<ChunkDraft> drafts) {
        if (documentId == null || drafts == null || drafts.isEmpty()) {
            throw new ServiceException("文档片段不能为空，documentId=" + documentId,
                    DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }

        // 1. 先逻辑删除旧片段，再保存新片段
        this.lambdaUpdate()
                .eq(DocumentChunk::getDocumentId, documentId)
                .remove();

        List<DocumentChunk> chunks = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            chunks.add(toChunk(documentId, drafts.get(i), i));
        }
        boolean saved = this.saveBatch(chunks);
        if (!saved) {
            throw new ServiceException("保存文档片段失败，documentId=" + documentId);
        }
        return chunks;
    }

    @Override
    public long countByDocumentId(Long documentId) {
        return this.lambdaQuery()
                .eq(DocumentChunk::getDocumentId, documentId)
                .count();
    }

    private DocumentChunk toChunk(Long documentId, ChunkDraft draft, int order) {
        if (draft == null || !StringUtils.hasText(draft.chunkId()) || !StringUtils.hasText(draft.text())) {
            throw new ServiceException("文档片段草稿不合法，documentId=" + documentId,
                    DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
        return DocumentChunk.builder()
                .chunkId(draft.chunkId())
                .documentId(documentId)
                .chunkOrder(order)
                .parentChunkId(draft.parentChunkId())
                .text(draft.text())
                .metadataJson(toMetadataJson(draft.metadata()))
                .tokenCount(draft.tokenCount())
                .status(draft.skipIndex() ? ChunkStatus.SKIP_INDEX : ChunkStatus.PENDING_INDEX)
                .skipIndex(draft.skipIndex() ? 1 : 0)
                .build();
    }

    private String toMetadataJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new ServiceException("序列化文档片段元数据失败", exception,
                    DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
    }
}
