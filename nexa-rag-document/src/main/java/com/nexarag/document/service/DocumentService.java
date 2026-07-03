package com.nexarag.document.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexarag.document.dto.CreateDocumentRequest;
import com.nexarag.document.dto.ProcessDocumentRequest;
import com.nexarag.document.entity.Document;
import com.nexarag.document.vo.DocumentSummaryVO;
import com.nexarag.document.vo.PageVO;

/**
 * 文档服务接口，负责文档记录、处理状态和删除状态的业务操作。
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
     * 分页查询文档摘要列表。
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 文档摘要分页数据
     */
    PageVO<DocumentSummaryVO> pageDocuments(long pageNum, long pageSize);

    /**
     * 提交文档处理。
     *
     * @param documentId 文档ID
     * @param request    文档处理请求
     * @return 文档实体
     */
    Document submitProcess(Long documentId, ProcessDocumentRequest request);

    /**
     * 记录文档处理失败，并由系统自动决定重新排队或最终失败。
     *
     * @param documentId    文档ID
     * @param failureStage  失败阶段
     * @param failureReason 失败原因
     * @param failureDetail 失败详情
     * @return 文档实体
     */
    Document recordProcessFailure(Long documentId, String failureStage, String failureReason, String failureDetail);

    /**
     * 人工重试失败文档，通常用于自动重试耗尽后由用户重新入队。
     *
     * @param documentId 文档ID
     * @return 文档实体
     */
    Document retryProcess(Long documentId);

    /**
     * 删除文档。
     *
     * @param documentId 文档ID
     * @return true 表示删除成功，false 表示未删除
     */
    boolean deleteDocument(Long documentId);

    /**
     * 根据文档ID获取文档，不存在时抛出异常。
     *
     * @param documentId 文档ID
     * @return 文档实体
     */
    Document getRequiredDocument(Long documentId);
}
