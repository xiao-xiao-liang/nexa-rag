package com.nexarag.document.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexarag.common.web.PageVO;
import com.nexarag.document.model.dto.DocumentVersionUploadDTO;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.model.vo.DocumentVersionVO;
import com.nexarag.document.model.vo.DocumentVersionOperationLogVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 文档版本服务，负责版本创建、状态流转与当前版本指针的数据一致性。
 */
public interface DocumentVersionService extends IService<DocumentVersionDO> {

    /**
     * 创建文档的下一版本，并原子占用构建指针。
     *
     * @param documentId 文档ID
     * @param upload 文件快照
     * @param processId 处理轮次ID
     * @param operatorId 操作者ID
     * @return 新建版本
     */
    DocumentVersionDO createNextVersion(Long documentId, DocumentVersionUploadDTO upload,
                                        String processId, String operatorId);

    /**
     * 分页查询一个文档的版本历史，按版本号倒序返回。
     *
     * @param documentId 文档ID
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 文档版本分页结果
     */
    PageVO<DocumentVersionVO> listVersions(Long documentId, long pageNum, long pageSize);

    /**
     * 查询指定版本的展示信息。
     */
    DocumentVersionVO getVersionDetail(Long documentId, Long documentVersionId);

    /**
     * 分页查询永久保留的版本操作审计。
     */
    PageVO<DocumentVersionOperationLogVO> listOperationLogs(Long documentId, long pageNum, long pageSize);

    /**
     * 获取属于指定文档的版本，不存在或不属于该文档时抛出异常。
     *
     * @param documentId 文档ID
     * @param documentVersionId 文档版本ID
     * @return 文档版本
     */
    DocumentVersionDO getRequiredVersion(Long documentId, Long documentVersionId);

    /**
     * 查询文档当前生效版本；缺少或失效的生效指针返回空。
     *
     * @param document 文档稳定身份记录
     * @return 当前生效版本，不存在时返回 null
     */
    DocumentVersionDO getActiveVersionOrNull(Document document);

    /**
     * 批量解析文档当前生效版本，避免文档列表出现 N+1 查询。
     *
     * @param documents 文档稳定身份记录集合
     * @return 以文档ID为键的当前生效版本映射
     */
    Map<Long, DocumentVersionDO> findActiveVersions(Collection<Document> documents);

    /**
     * 仅在指定版本仍属于指定处理轮次且已完成切分时，推进至索引中状态。
     *
     * @param documentId 文档ID
     * @param documentVersionId 文档版本ID
     * @param processId 处理轮次ID
     * @return 是否成功推进
     */
    boolean markIndexing(Long documentId, Long documentVersionId, String processId);

    /**
     * 将已完成索引预热的版本设为当前生效版本，并释放该文档的构建槽位。
     *
     * @param documentId 文档ID
     * @param documentVersionId 文档版本ID
     * @param processId 处理轮次ID
     * @return 是否成功推进并发布
     */
    boolean markIndexReady(Long documentId, Long documentVersionId, String processId);

    /** 将非当前、已完成索引预热的历史版本切换为生效版本。 */
    void activateReadyVersion(Long documentId, Long documentVersionId, String operatorId);

    /** 将失败版本以新的处理批次重新入队。 */
    DocumentVersionDO retryFailedVersion(Long documentId, Long documentVersionId, String processId, String operatorId);

    /**
     * 受理历史版本永久删除，并创建版本级外部索引清理任务。
     */
    void requestPermanentDelete(Long documentId, Long documentVersionId, String operatorId);

    /**
     * 将整篇文档下尚未删除的版本统一标记为永久删除中。
     *
     * @param documentId 文档ID
     * @param operatorId 操作者ID
     * @return 本次成功占用删除状态的版本快照
     */
    List<DocumentVersionDO> markAllVersionsDeleting(Long documentId, String operatorId);

    /**
     * 记录指定处理轮次的消息消费状态；当版本已完成、失败或处理轮次失效时返回 false。
     *
     * @param documentId 文档ID
     * @param documentVersionId 文档版本ID
     * @param processId 处理轮次ID
     * @param messageId 消息ID
     * @param consumedTimes 当前消息累计消费次数
     * @return 是否成功更新该处理边界的状态
     */
    boolean recordMessageConsumption(Long documentId, Long documentVersionId, String processId,
                                     String messageId, int consumedTimes);

    /**
     * 记录当前版本处理轮次的可重试异常。
     *
     * @param documentId 文档ID
     * @param documentVersionId 文档版本ID
     * @param processId 处理轮次ID
     * @param failureStage 失败阶段
     * @param failureReason 失败原因
     * @param failureDetail 失败详情
     * @return 是否成功更新该处理边界的状态
     */
    boolean recordRetryableFailure(Long documentId, Long documentVersionId, String processId,
                                   String failureStage, String failureReason, String failureDetail);

    /**
     * 标记指定版本、指定处理轮次的消息处理完成。
     *
     * @param documentId 文档ID
     * @param documentVersionId 文档版本ID
     * @param processId 处理轮次ID
     * @return 是否成功更新消息完成状态
     */
    boolean markMessageCompleted(Long documentId, Long documentVersionId, String processId);

    /**
     * 将指定版本、指定处理轮次标记为最终失败。
     *
     * @param documentId 文档ID
     * @param documentVersionId 文档版本ID
     * @param processId 处理轮次ID
     * @param failureStage 失败阶段
     * @param failureReason 失败原因
     * @param failureDetail 失败详情
     * @param consumedTimes 实际消费次数
     * @param messageId 最近一次消息ID
     * @param failureTime 最终失败时间
     * @return 是否成功标记最终失败
     */
    boolean markProcessFailed(Long documentId, Long documentVersionId, String processId,
                              String failureStage, String failureReason, String failureDetail,
                              int consumedTimes, String messageId, java.time.LocalDateTime failureTime);
}
