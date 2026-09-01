package com.nexarag.document.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.constants.DocumentIndexingConstants;
import com.nexarag.document.enums.ChunkStatus;
import com.nexarag.document.enums.DocumentErrorCode;
import com.nexarag.document.mapper.DocumentChunkMapper;
import com.nexarag.document.model.bo.DocumentChunkIndexWriteBO;
import com.nexarag.document.model.bo.split.ChunkDraft;
import com.nexarag.document.model.entity.DocumentChunk;
import com.nexarag.document.service.DocumentChunkService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private static final long DEFAULT_PAGE_SIZE = 20;
    private static final long MAX_PAGE_SIZE = 100;

    @Override
    public List<DocumentChunk> listByDocumentVersionId(Long documentVersionId) {
        return this.lambdaQuery()
                .eq(DocumentChunk::getDocumentVersionId, documentVersionId)
                .orderByAsc(DocumentChunk::getChunkOrder)
                .list();
    }

    @Override
    public List<DocumentChunk> listByParentChunkIds(List<String> parentChunkIds) {
        if (parentChunkIds == null || parentChunkIds.isEmpty()) {
            return List.of();
        }
        // 1. 批量读取同一批父片段的子项，避免检索扩展阶段出现 N+1 查询
        return this.lambdaQuery()
                .in(DocumentChunk::getParentChunkId, parentChunkIds)
                .orderByAsc(DocumentChunk::getDocumentId)
                .orderByAsc(DocumentChunk::getChunkOrder)
                .list();
    }

    @Override
    public IPage<DocumentChunk> pageByDocumentVersionId(Long documentVersionId, long pageNum, long pageSize) {
        long safePageNum = pageNum <= 0 ? 1 : pageNum;
        long safePageSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        Page<DocumentChunk> page = Page.of(safePageNum, safePageSize);
        return this.lambdaQuery()
                .eq(DocumentChunk::getDocumentVersionId, documentVersionId)
                .orderByAsc(DocumentChunk::getChunkOrder)
                .page(page);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public List<DocumentChunk> replaceDocumentVersionChunks(Long documentId, Long documentVersionId,
                                                            List<ChunkDraft> drafts) {
        if (documentId == null || documentVersionId == null || drafts == null || drafts.isEmpty()) {
            throw new ServiceException("文档版本片段不能为空，documentId=" + documentId
                    + "，documentVersionId=" + documentVersionId, DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
        deleteByDocumentVersionId(documentVersionId);
        return saveDocumentVersionChunks(documentId, documentVersionId, drafts);
    }

    @Override
    public void deleteByDocumentVersionId(Long documentVersionId) {
        // 历史版本永久删除不得受逻辑删除插件影响，避免残留正文数据。
        baseMapper.physicalDeleteByDocumentVersionId(documentVersionId);
    }

    @Override
    public List<DocumentChunk> saveDocumentVersionChunks(Long documentId, Long documentVersionId,
                                                         List<ChunkDraft> drafts) {
        if (documentVersionId == null) {
            throw new ServiceException("文档版本ID不能为空，documentId=" + documentId,
                    DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
        return saveChunks(documentId, documentVersionId, drafts);
    }

    private List<DocumentChunk> saveChunks(Long documentId, Long documentVersionId, List<ChunkDraft> drafts) {
        if (documentId == null || drafts == null || drafts.isEmpty()) {
            throw new ServiceException("文档片段不能为空，documentId=" + documentId, DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            chunks.add(toChunk(documentId, documentVersionId, drafts.get(i), i));
        }
        boolean saved = this.saveBatch(chunks);
        if (!saved) {
            throw new ServiceException("保存文档片段失败，documentId=" + documentId);
        }
        return chunks;
    }

    /**
     * 标记片段索引成功并回写索引ID。
     *
     * @param chunkId        片段ID
     * @param vectorId       向量索引ID
     * @param keywordIndexId 关键词索引ID
     */
    @Override
    public void markChunkIndexed(String chunkId, String vectorId, String keywordIndexId) {
        // 1. 回写索引ID并清空失败原因
        this.lambdaUpdate()
                .eq(DocumentChunk::getChunkId, chunkId)
                .set(DocumentChunk::getStatus, ChunkStatus.INDEXED)
                .set(DocumentChunk::getVectorId, vectorId)
                .set(DocumentChunk::getKeywordIndexId, keywordIndexId)
                .set(DocumentChunk::getFailureReason, null)
                .update();
    }

    /**
     * 批量标记片段索引成功，控制单条 SQL 的参数数量。
     *
     * @param chunks 待回写的片段集合
     */
    @Override
    public void batchMarkChunksIndexed(List<DocumentChunkIndexWriteBO> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        // 1. 按固定上限拆分，避免超长 CASE WHEN 语句占用连接和解析时间。
        for (int start = 0; start < chunks.size(); start += DocumentIndexingConstants.INDEX_STATUS_UPDATE_BATCH_SIZE) {
            int end = Math.min(start + DocumentIndexingConstants.INDEX_STATUS_UPDATE_BATCH_SIZE, chunks.size());
            baseMapper.batchMarkIndexed(chunks.subList(start, end));
        }
    }

    /**
     * 标记片段索引失败。
     *
     * @param chunkId       片段ID
     * @param failureReason 失败原因
     */
    @Override
    public void markChunkIndexFailed(String chunkId, String failureReason) {
        // 1. 标记失败状态并保存失败原因
        this.lambdaUpdate()
                .eq(DocumentChunk::getChunkId, chunkId)
                .set(DocumentChunk::getStatus, ChunkStatus.FAILED)
                .set(DocumentChunk::getFailureReason, failureReason)
                .update();
    }

    /**
     * 标记指定文档版本中需要跳过索引的片段。
     *
     * @param documentVersionId 文档版本ID
     */
    @Override
    public void markDocumentVersionSkippedChunks(Long documentVersionId) {
        // 1. 将当前版本的跳过片段稳定标记为 SKIP_INDEX，防止覆盖其他版本状态
        this.lambdaUpdate()
                .eq(DocumentChunk::getDocumentVersionId, documentVersionId)
                .eq(DocumentChunk::getSkipIndex, 1)
                .set(DocumentChunk::getStatus, ChunkStatus.SKIP_INDEX)
                .update();
    }

    private DocumentChunk toChunk(Long documentId, Long documentVersionId, ChunkDraft draft, int order) {
        if (draft == null || !StringUtils.hasText(draft.chunkId()) || !StringUtils.hasText(draft.text())) {
            throw new ServiceException("文档片段草稿不合法，documentId=" + documentId,
                    DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
        return DocumentChunk.builder()
                .chunkId(draft.chunkId())
                .documentId(documentId)
                .documentVersionId(documentVersionId)
                .chunkOrder(order)
                .parentChunkId(draft.parentChunkId())
                .sectionId(draft.sectionId())
                .text(draft.text())
                .indexContent(requireIndexContent(documentId, draft.indexContent()))
                .metadataJson(toMetadataJson(draft.metadata()))
                .tokenCount(draft.tokenCount())
                .status(draft.skipIndex() ? ChunkStatus.SKIP_INDEX : ChunkStatus.PENDING_INDEX)
                .skipIndex(draft.skipIndex() ? 1 : 0)
                .build();
    }

    private String requireIndexContent(Long documentId, String indexContent) {
        if (!StringUtils.hasText(indexContent)) {
            throw new IllegalArgumentException("文档片段索引内容不能为空，documentId=" + documentId);
        }
        return indexContent;
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
