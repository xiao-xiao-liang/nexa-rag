package com.nexarag.document.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexarag.common.web.PageVO;
import com.nexarag.document.model.dto.CreateDocumentRequest;
import com.nexarag.document.model.dto.ProcessDocumentRequest;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.vo.DocumentSummaryVO;
import com.nexarag.document.model.vo.DocumentDeleteVO;

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
     * @param processId 处理批次ID
     * @return 文档实体
     */
    Document submitProcess(Long documentId, ProcessDocumentRequest request, String processId);

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
     * @param processId 新的处理批次ID
     * @return 文档实体
     */
    Document retryProcess(Long documentId, String processId);

    /**
     * 记录当前处理轮次的消息消费信息。
     *
     * @param documentId    文档ID
     * @param processId     处理批次ID
     * @param messageId     RocketMQ消息ID
     * @param consumedTimes 消息消费次数
     * @return true表示当前轮次已更新，false表示旧轮次或终态消息
     */
    boolean recordMessageConsumption(Long documentId, String processId, String messageId, int consumedTimes);

    /**
     * 标记当前处理轮次消息处理完成。
     *
     * @param documentId 文档ID
     * @param processId  处理批次ID
     * @return true表示更新成功，false表示轮次或文档状态不匹配
     */
    boolean markMessageCompleted(Long documentId, String processId);

    /**
     * 将当前处理轮次标记为最终失败。
     *
     * @param documentId   文档ID
     * @param processId    处理批次ID
     * @param failureStage 失败阶段
     * @param failureReason 失败原因
     * @param failureDetail 失败详情
     * @param consumedTimes 实际执行Workflow次数
     * @param messageId 最近一次实际消费消息ID
     * @param failureTime 最终失败时间
     * @return true表示更新成功，false表示处理轮次已变化
     */
    boolean markProcessFailed(Long documentId, String processId, String failureStage,
                              String failureReason, String failureDetail, int consumedTimes,
                              String messageId, java.time.LocalDateTime failureTime);

    /**
     * 删除文档。
     *
     * @param documentId 文档ID
     * @return 删除与异步清理任务响应
     */
    DocumentDeleteVO deleteDocument(Long documentId);

    /**
     * 根据文档ID获取文档，不存在时抛出异常。
     *
     * @param documentId 文档ID
     * @return 文档实体
     */
    Document getRequiredDocument(Long documentId);

    /**
     * 将已解析文档原子推进到切分中状态。
     *
     * @param documentId 文档ID
     * @return true 表示状态更新成功，false 表示文档状态已变化
     */
    boolean markChunking(Long documentId);

    /**
     * 将切分中文档原子推进到切分完成状态。
     *
     * @param documentId 文档ID
     * @return true 表示状态更新成功，false 表示文档状态已变化
     */
    boolean markChunked(Long documentId);

    /**
     * 将当前处理轮次从切分完成推进到索引中。
     *
     * @param documentId 文档ID
     * @param processId  处理批次ID
     * @return true表示推进成功，false表示状态或轮次已变化
     */
    boolean markIndexing(Long documentId, String processId);

    /**
     * 将当前处理轮次从索引中推进到索引完成。
     *
     * @param documentId 文档ID
     * @param processId  处理批次ID
     * @return true表示推进成功，false表示状态或轮次已变化
     */
    boolean markIndexed(Long documentId, String processId);
}
