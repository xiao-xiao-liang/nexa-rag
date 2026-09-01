package com.nexarag.retrieval.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.mapper.DocumentSectionMapper;
import com.nexarag.document.model.entity.DocumentSectionDO;
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
import java.util.Set;

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
     * 写入指定文档版本的章节标题和路径导航索引。
     */
    @Override
    public void upsert(Long documentId, Long documentVersionId) {
        if (documentId == null || documentVersionId == null) {
            return;
        }

        // 1. 先清理当前版本导航记录，保留同一文档的历史版本索引
        keywordIndexClient.deleteByDocumentVersionId(documentId, documentVersionId,
                retrievalProperties.getKeyword().getNavigationIndexName());

        // 2. 只读取当前版本的稳定章节结构
        List<SectionNavigationDocument> navigationDocuments = documentSectionMapper.selectList(
                        new LambdaQueryWrapper<DocumentSectionDO>()
                                .eq(DocumentSectionDO::getDocumentId, documentId)
                                .eq(DocumentSectionDO::getDocumentVersionId, documentVersionId)
                                .orderByAsc(DocumentSectionDO::getStartLine)
                                .orderByAsc(DocumentSectionDO::getSectionId))
                .stream()
                .map(this::toNavigationDocument)
                .filter(document -> StringUtils.hasText(document.indexContent()))
                .toList();
        if (navigationDocuments.isEmpty()) {
            return;
        }

        // 3. 章节导航和正文片段均写入版本元数据，供后续生效版本过滤
        List<KeywordIndexWriteResult> results = keywordIndexClient.upsert(new KeywordIndexWriteRequest(
                retrievalProperties.getKeyword().getNavigationIndexName(), documentId, documentVersionId,
                navigationDocuments.stream().map(document -> toKeywordDocument(document, documentVersionId)).toList()));
        if (results == null || results.size() != navigationDocuments.size()
                || results.stream().anyMatch(result -> !result.success())) {
            throw new ServiceException("章节导航索引写入失败，documentId=" + documentId
                    + "，documentVersionId=" + documentVersionId);
        }
    }

    @Override
    public int deleteByDocumentVersionId(Long documentId, Long documentVersionId) {
        return keywordIndexClient.deleteByDocumentVersionId(documentId, documentVersionId,
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
    public List<SectionNavigationHit> search(String query, int limit, Set<Long> activeVersionIds) {
        if (!StringUtils.hasText(query) || limit <= 0 || activeVersionIds == null || activeVersionIds.isEmpty()) {
            return List.of();
        }

        // 1. 仅查询当前生效版本的独立导航索引，结果不直接进入正文证据或对话回答流程
        return keywordIndexClient.search(new KeywordIndexSearchRequest(
                        retrievalProperties.getKeyword().getNavigationIndexName(), query, limit, activeVersionIds))
                .stream()
                .filter(result -> result.sectionId() != null && result.documentId() != null
                        && activeVersionIds.contains(result.documentVersionId()))
                .map(result -> new SectionNavigationHit(result.sectionId(), result.documentId(), result.documentVersionId(), result.score(),
                        NAVIGATION_CHANNEL))
                .toList();
    }

    private SectionNavigationDocument toNavigationDocument(DocumentSectionDO section) {
        return new SectionNavigationDocument(section.getSectionId(), section.getDocumentId(), section.getParentSectionId(),
                section.getTitle(), parseHeadingPath(section), section.getHeadingLevel());
    }

    private KeywordIndexDocument toKeywordDocument(SectionNavigationDocument document, Long documentVersionId) {
        return new KeywordIndexDocument(SECTION_INDEX_ID_PREFIX + document.sectionId(), document.documentId(),
                documentVersionId, document.parentSectionId() == null ? null : String.valueOf(document.parentSectionId()),
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
