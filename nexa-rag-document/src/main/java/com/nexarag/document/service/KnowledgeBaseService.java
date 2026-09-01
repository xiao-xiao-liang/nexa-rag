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
import java.util.Map;
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

    /**
     * 锁定当前租户内仍有效的知识库，确保文档创建不会与删除操作并发交错。
     *
     * @param knowledgeBaseId 知识库ID
     */
    void lockRequiredActiveKnowledgeBase(Long knowledgeBaseId);

    Document getRequiredDocument(Long knowledgeBaseId, Long documentId);

    Set<Long> validateRequestedKnowledgeBases(Collection<Long> knowledgeBaseIds);

    /**
     * 校验指定租户范围内的知识库，适用于脱离 HTTP 请求线程的异步任务。
     *
     * @param tenantId         可信租户ID
     * @param knowledgeBaseIds 待校验知识库ID集合；为空表示检索全部知识库
     * @return 去重后的知识库ID集合
     */
    Set<Long> validateRequestedKnowledgeBases(String tenantId, Collection<Long> knowledgeBaseIds);

    boolean isDocumentInCurrentTenantScope(Long documentId, Set<Long> knowledgeBaseIds);

    /**
     * 批量过滤当前租户和指定知识库范围内可访问的文档ID。
     *
     * @param documentIds      待校验的文档ID集合
     * @param knowledgeBaseIds 已校验的知识库范围；为空表示当前租户全部知识库
     * @return 可访问的文档ID集合
     */
    Set<Long> filterDocumentIdsInCurrentTenantScope(Collection<Long> documentIds, Set<Long> knowledgeBaseIds);

    /**
     * 批量过滤指定租户和指定知识库范围内可访问的文档ID，适用于脱离 HTTP 请求线程的异步任务。
     *
     * @param tenantId         可信租户ID
     * @param documentIds      待校验的文档ID集合
     * @param knowledgeBaseIds 已校验的知识库范围；为空表示该租户全部知识库
     * @return 可访问的文档ID集合
     */
    Set<Long> filterDocumentIdsInTenantScope(String tenantId, Collection<Long> documentIds, Set<Long> knowledgeBaseIds);

    Map<Long, Long> findActiveVersionIdsInTenantScope(String tenantId, Collection<Long> documentIds,
                                                      Set<Long> knowledgeBaseIds);

    Set<Long> listActiveVersionIdsInTenantScope(String tenantId, Set<Long> knowledgeBaseIds);
}
