package com.nexarag.document.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.nexarag.common.web.PageVO;
import com.nexarag.document.model.dto.CreateDocumentRequest;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentChunk;
import com.nexarag.document.model.vo.*;

/**
 * 文档服务接口，负责稳定文档身份、当前生效版本投影和删除操作。
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
     * 在指定知识库中创建文档记录。
     *
     * @param knowledgeBaseId 知识库ID
     * @param request         文档创建请求
     * @return 文档实体
     */
    Document createDocument(Long knowledgeBaseId, CreateDocumentRequest request);

    /**
     * 分页查询文档摘要列表。
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 文档摘要分页数据
     */
    PageVO<DocumentSummaryVO> pageDocuments(long pageNum, long pageSize);

    /**
     * 分页查询指定知识库内的文档摘要。
     *
     * @param knowledgeBaseId 知识库ID
     * @param pageNum         页码
     * @param pageSize        每页数量
     * @return 文档摘要分页数据
     */
    PageVO<DocumentSummaryVO> pageDocuments(Long knowledgeBaseId, long pageNum, long pageSize);

    /**
     * 查询文档详情，并仅投影当前生效版本。
     *
     * @param documentId 文档ID
     * @return 文档详情响应
     */
    DocumentDetailVO getDocumentDetail(Long documentId);

    /**
     * 查询文档处理状态，并仅投影当前生效版本。
     *
     * @param documentId 文档ID
     * @return 文档处理状态响应
     */
    DocumentProcessStatusVO getProcessStatus(Long documentId);

    /**
     * 分页查询当前生效版本的片段。
     *
     * @param documentId 文档ID
     * @param pageNum    页码
     * @param pageSize   每页数量
     * @return 当前版本片段分页数据
     */
    IPage<DocumentChunk> pageActiveVersionChunks(Long documentId, long pageNum, long pageSize);

    /**
     * 查询文档诊断概览，包含基础信息、处理配置快照与片段状态统计。
     *
     * @param documentId 文档ID
     * @return 文档诊断概览
     */
    DocumentOverviewVO getOverview(Long documentId);

    /**
     * 删除文档。
     *
     * @param documentId 文档ID
     * @return 删除与异步清理任务响应
     */
    DocumentDeleteVO deleteDocument(Long documentId, String operator);

    /**
     * 根据文档ID获取文档，不存在时抛出异常。
     *
     * @param documentId 文档ID
     * @return 文档实体
     */
    Document getRequiredDocument(Long documentId);

}
