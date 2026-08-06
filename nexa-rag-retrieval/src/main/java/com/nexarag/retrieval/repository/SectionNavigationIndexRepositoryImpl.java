package com.nexarag.retrieval.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.entity.DocumentSectionDO;
import com.nexarag.document.mapper.DocumentSectionMapper;
import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.dto.req.KeywordIndexSearchRequest;
import com.nexarag.retrieval.dto.req.KeywordIndexWriteRequest;
import com.nexarag.retrieval.index.keyword.KeywordIndexClient;
import com.nexarag.retrieval.model.KeywordIndexDocument;
import com.nexarag.retrieval.model.KeywordIndexWriteResult;
import com.nexarag.retrieval.model.SectionNavigationDocument;
import com.nexarag.retrieval.model.SectionNavigationHit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 章节导航索引仓储实现，仅读写章节标题和路径，严格与正文片段证据索引隔离。
 */
@Component
@RequiredArgsConstructor
public class SectionNavigationIndexRepositoryImpl implements SectionNavigationIndexRepository {

    private static final String NAVIGATION_CHANNEL = "KEYWORD";
    private static final String SECTION_INDEX_ID_PREFIX = "section-";

    private final DocumentSectionMapper documentSectionMapper;
    private final KeywordIndexClient keywordIndexClient;
    private final RetrievalProperties retrievalProperties;
    private final ObjectMapper objectMapper;

    /**
     * 写入指定文档的章节标题和路径导航索引。
     *
     * @param documentId 文档ID
     */
    @Override
    public void upsert(Long documentId) {
        if (documentId == null) {
            return;
        }

        // 1. 先清理该文档的历史导航记录，避免章节结构重建后残留已删除标题
        deleteByDocumentId(documentId);

        // 2. 查询已持久化章节，确保标题索引只基于数据库中的稳定结构
        List<SectionNavigationDocument> navigationDocuments = documentSectionMapper.selectList(
                        new LambdaQueryWrapper<DocumentSectionDO>()
                                .eq(DocumentSectionDO::getDocumentId, documentId)
                                .orderByAsc(DocumentSectionDO::getStartLine)
                                .orderByAsc(DocumentSectionDO::getSectionId))
                .stream()
                .map(this::toNavigationDocument)
                .filter(document -> StringUtils.hasText(document.indexContent()))
                .toList();
        if (navigationDocuments.isEmpty()) {
            return;
        }

        // 3. 写入独立关键词索引，正文片段索引不接收纯标题章节
        List<KeywordIndexWriteResult> results = keywordIndexClient.upsert(new KeywordIndexWriteRequest(
                retrievalProperties.getKeyword().getNavigationIndexName(), documentId,
                navigationDocuments.stream().map(this::toKeywordDocument).toList()));

        // 4. 任何章节未写入都抛出异常，由既有文档索引重试链路处理
        if (results == null || results.size() != navigationDocuments.size()
                || results.stream().anyMatch(result -> !result.success())) {
            throw new ServiceException("章节导航索引写入失败，documentId=" + documentId);
        }
    }

    /**
     * 删除指定文档的章节导航索引。
     *
     * @param documentId 文档ID
     * @return 删除数量
     */
    @Override
    public int deleteByDocumentId(Long documentId) {
        return keywordIndexClient.deleteByDocumentId(documentId,
                retrievalProperties.getKeyword().getNavigationIndexName());
    }

    /**
     * 查询章节导航命中范围。
     *
     * @param query 查询文本
     * @param limit 返回数量
     * @return 章节导航命中列表
     */
    @Override
    public List<SectionNavigationHit> search(String query, int limit) {
        if (!StringUtils.hasText(query) || limit <= 0) {
            return List.of();
        }

        // 1. 仅查询独立导航索引，结果不接入正文证据或对话回答流程
        return keywordIndexClient.search(new KeywordIndexSearchRequest(
                        retrievalProperties.getKeyword().getNavigationIndexName(), query, limit))
                .stream()
                .filter(result -> result.sectionId() != null && result.documentId() != null)
                .map(result -> new SectionNavigationHit(result.sectionId(), result.documentId(), result.score(),
                        NAVIGATION_CHANNEL))
                .toList();
    }

    private SectionNavigationDocument toNavigationDocument(DocumentSectionDO section) {
        return new SectionNavigationDocument(section.getSectionId(), section.getDocumentId(), section.getParentSectionId(),
                section.getTitle(), parseHeadingPath(section), section.getHeadingLevel());
    }

    private KeywordIndexDocument toKeywordDocument(SectionNavigationDocument document) {
        return new KeywordIndexDocument(SECTION_INDEX_ID_PREFIX + document.sectionId(), document.documentId(),
                document.parentSectionId() == null ? null : String.valueOf(document.parentSectionId()),
                document.headingLevel(), document.sectionId(), document.title(), document.indexContent(), null);
    }

    private String parseHeadingPath(DocumentSectionDO section) {
        if (!StringUtils.hasText(section.getHeadingPathJson())) {
            return "";
        }
        try {
            List<String> headings = objectMapper.readValue(section.getHeadingPathJson(), new TypeReference<>() {
            });
            return String.join(" > ", headings);
        } catch (Exception exception) {
            throw new ServiceException("解析章节标题路径失败，sectionId=" + section.getSectionId(), exception,
                    BaseErrorCode.SERVICE_ERROR);
        }
    }
}
