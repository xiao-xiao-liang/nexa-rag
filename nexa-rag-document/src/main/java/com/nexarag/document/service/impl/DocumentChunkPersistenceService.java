package com.nexarag.document.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.enums.DocumentErrorCode;
import com.nexarag.document.mapper.DocumentSectionMapper;
import com.nexarag.document.model.bo.split.DocumentSectionDraft;
import com.nexarag.document.model.bo.split.DocumentSplitResult;
import com.nexarag.document.model.entity.DocumentSectionDO;
import com.nexarag.document.service.DocumentChunkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * 文档切分结果持久化服务，负责在短事务内替换片段并完成文档状态流转。
 */
@Service
@RequiredArgsConstructor
public class DocumentChunkPersistenceService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DocumentChunkService documentChunkService;
    private final DocumentSectionMapper documentSectionMapper;

    /**
     * 原子替换指定文档版本的章节和正文片段。
     *
     * @param documentId        文档ID
     * @param documentVersionId 文档版本ID
     * @param splitResult       包含章节和片段的切分结果
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceDocumentVersionStructure(Long documentId, Long documentVersionId, DocumentSplitResult splitResult) {
        if (documentId == null || documentVersionId == null || splitResult == null || splitResult.chunks().isEmpty()) {
            throw new ServiceException("文档版本切分结果不能为空，documentId=" + documentId + "，documentVersionId=" + documentVersionId);
        }
        validateSectionReferences(splitResult);
        documentChunkService.deleteByDocumentVersionId(documentVersionId);
        documentSectionMapper.physicalDeleteByDocumentVersionId(documentVersionId);
        splitResult.sections().forEach(section -> documentSectionMapper.insert(toSection(documentId, documentVersionId, section)));
        documentChunkService.saveDocumentVersionChunks(documentId, documentVersionId, splitResult.chunks());
    }

    private void validateSectionReferences(DocumentSplitResult splitResult) {
        Set<Long> sectionIds = new HashSet<>();
        for (DocumentSectionDraft section : splitResult.sections()) {
            if (section == null || section.sectionId() == null || !sectionIds.add(section.sectionId())) {
                throw new IllegalArgumentException("文档章节草稿不合法");
            }
        }
        splitResult.chunks().forEach(chunk -> {
            if (chunk != null && chunk.sectionId() != null && !sectionIds.contains(chunk.sectionId())) {
                throw new IllegalArgumentException("文档片段引用的章节不存在，sectionId=" + chunk.sectionId());
            }
        });
    }

    private DocumentSectionDO toSection(Long documentId, Long documentVersionId, DocumentSectionDraft section) {
        try {
            return DocumentSectionDO.builder()
                    .sectionId(section.sectionId())
                    .documentId(documentId)
                    .documentVersionId(documentVersionId)
                    .parentSectionId(section.parentSectionId())
                    .title(section.title())
                    .headingPathJson(OBJECT_MAPPER.writeValueAsString(section.headingPath()))
                    .headingLevel(section.headingLevel())
                    .startLine(section.startLine())
                    .endLine(section.endLine())
                    .build();
        } catch (JsonProcessingException exception) {
            throw new ServiceException("序列化文档章节标题路径失败", exception,
                    DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
    }

}
