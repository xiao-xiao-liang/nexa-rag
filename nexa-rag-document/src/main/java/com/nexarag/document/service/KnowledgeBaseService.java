package com.nexarag.document.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexarag.common.web.PageVO;
import com.nexarag.document.model.dataobject.KnowledgeBaseDO;
import com.nexarag.document.model.dto.CreateKnowledgeBaseDTO;
import com.nexarag.document.model.dto.UpdateKnowledgeBaseDTO;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.vo.KnowledgeBaseDetailVO;
import com.nexarag.document.model.vo.KnowledgeBaseSummaryVO;

import java.util.Collection;
import java.util.Set;

/**
 * 知识库服务，管理租户范围内知识库及文档归属校验。
 */
public interface KnowledgeBaseService extends IService<KnowledgeBaseDO> {

    KnowledgeBaseDetailVO create(CreateKnowledgeBaseDTO request);

    PageVO<KnowledgeBaseSummaryVO> pageKnowledgeBases(long pageNum, long pageSize);

    KnowledgeBaseDetailVO getDetail(Long knowledgeBaseId);

    KnowledgeBaseDetailVO update(Long knowledgeBaseId, UpdateKnowledgeBaseDTO request);

    void delete(Long knowledgeBaseId);

    KnowledgeBaseDO getRequiredKnowledgeBase(Long knowledgeBaseId);

    Document getRequiredDocument(Long knowledgeBaseId, Long documentId);

    Set<Long> validateRequestedKnowledgeBases(Collection<Long> knowledgeBaseIds);

    boolean isDocumentInCurrentTenantScope(Long documentId, Set<Long> knowledgeBaseIds);
}
