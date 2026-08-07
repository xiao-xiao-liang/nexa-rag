package com.nexarag.retrieval.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexarag.document.model.entity.DocumentChunk;
import com.nexarag.document.mapper.DocumentChunkMapper;
import com.nexarag.document.mapper.DocumentSectionMapper;
import com.nexarag.retrieval.model.SectionContentChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 章节正文仓储实现。只按导航给出的文档ID和章节树范围查询，不基于片段序号推断相邻内容。
 */
@Component
@RequiredArgsConstructor
public class SectionContentRepositoryImpl implements SectionContentRepository {

    private final DocumentSectionMapper documentSectionMapper;
    private final DocumentChunkMapper documentChunkMapper;

    /**
     * 查询根章节及其后代章节中的可用正文片段。
     *
     * @param documentId 文档ID
     * @param rootSectionId 根章节ID
     * @param limit 返回上限
     * @return 原始正文片段
     */
    @Override
    public List<SectionContentChunk> listBySectionScope(Long documentId, Long rootSectionId, int limit) {
        if (documentId == null || rootSectionId == null || limit <= 0) {
            return List.of();
        }

        // 1. 计算根章节及其后代章节，章节树是唯一的范围扩展依据
        Set<Long> sectionIds = new LinkedHashSet<>();
        sectionIds.add(rootSectionId);
        sectionIds.addAll(documentSectionMapper.selectDescendantSectionIds(documentId, rootSectionId));
        if (sectionIds.isEmpty()) {
            return List.of();
        }

        // 2. 严格按文档和章节范围查询正文，不按 chunkOrder 推断相邻片段
        List<DocumentChunk> chunks = documentChunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, documentId)
                .in(DocumentChunk::getSectionId, sectionIds)
                .and(wrapper -> wrapper.eq(DocumentChunk::getSkipIndex, 0)
                        .or()
                        .isNull(DocumentChunk::getSkipIndex)));

        // 3. 过滤空正文和重复片段，并保留数据库原始文本
        List<SectionContentChunk> result = new ArrayList<>();
        Set<String> chunkIds = new LinkedHashSet<>();
        for (DocumentChunk chunk : chunks) {
            if (result.size() >= limit) {
                break;
            }
            if (chunk == null || !StringUtils.hasText(chunk.getChunkId()) || !StringUtils.hasText(chunk.getText())
                    || !chunkIds.add(chunk.getChunkId())) {
                continue;
            }
            result.add(new SectionContentChunk(chunk.getChunkId(), chunk.getDocumentId(), chunk.getSectionId(),
                    chunk.getText(), chunk.getTokenCount()));
        }
        return result;
    }
}
