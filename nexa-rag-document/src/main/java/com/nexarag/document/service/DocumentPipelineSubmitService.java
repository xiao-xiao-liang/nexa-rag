package com.nexarag.document.service;

import com.nexarag.document.model.dto.CreateDocumentRequest;
import com.nexarag.document.model.dto.DocumentVersionUploadDTO;
import com.nexarag.document.model.dto.ProcessDocumentRequest;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.model.vo.DocumentProcessStatusVO;

/**
 * 文档流水线提交服务，负责在同一事务内推进文档状态并写入Outbox消息。
 */
public interface DocumentPipelineSubmitService {

    /**
     * 创建文档并提交处理流水线。
     *
     * @param knowledgeBaseId 知识库ID
     * @param createRequest   文档创建请求
     * @param processRequest  文档处理请求
     * @return 已进入排队状态的首个文档版本
     */
    DocumentVersionDO createAndSubmit(Long knowledgeBaseId, CreateDocumentRequest createRequest,
                                      ProcessDocumentRequest processRequest, String operator);

    /**
     * 为已有文档创建并提交一个新的文件版本。
     *
     * @param documentId     文档ID
     * @param upload         新文件快照
     * @param processRequest 处理配置
     * @return 已入队的文档版本
     */
    DocumentVersionDO createVersionAndSubmit(Long documentId, DocumentVersionUploadDTO upload,
                                             ProcessDocumentRequest processRequest, String operator);

    /**
     * 重新提交失败版本。
     */
    DocumentVersionDO retryVersion(Long documentId, Long documentVersionId, String operator);

    /**
     * 提交已有文档处理。
     *
     * @param documentId 文档ID
     * @param request    文档处理请求
     * @return 文档处理状态
     */
    DocumentProcessStatusVO submitProcess(Long documentId, ProcessDocumentRequest request);

    /**
     * 人工重试失败文档。
     *
     * @param documentId 文档ID
     * @return 文档处理状态
     */
    DocumentProcessStatusVO retryProcess(Long documentId);
}
