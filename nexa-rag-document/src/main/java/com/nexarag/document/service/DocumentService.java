package com.nexarag.document.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexarag.document.dto.CreateDocumentRequest;
import com.nexarag.document.dto.ProcessDocumentRequest;
import com.nexarag.document.entity.Document;

/**
 * 文档服务接口。
 */
public interface DocumentService extends IService<Document> {

    /**
     * 创建文档记录。
     *
     * @param request 创建文档请求
     * @return 文档实体
     */
    Document createDocument(CreateDocumentRequest request);

    /**
     * 提交文档处理。
     *
     * @param documentId 文档ID
     * @param request    文档处理请求
     * @return 文档实体
     */
    Document submitProcess(Long documentId, ProcessDocumentRequest request);

    /**
     * 提交文档重试。
     *
     * @param documentId 文档ID
     * @return 文档实体
     */
    Document retryProcess(Long documentId);

    /**
     * 根据文档ID获取文档，不存在时抛出异常。
     *
     * @param documentId 文档ID
     * @return 文档实体
     */
    Document getRequiredDocument(Long documentId);
}
