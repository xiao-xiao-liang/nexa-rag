package com.nexarag.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexarag.common.exception.ClientException;
import com.nexarag.common.web.PageVO;
import com.nexarag.document.enums.DocumentErrorCode;
import com.nexarag.document.enums.DocumentTaskStatus;
import com.nexarag.document.enums.DocumentVersionOperationType;
import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.mapper.DocumentMapper;
import com.nexarag.document.mapper.DocumentVersionMapper;
import com.nexarag.document.mapper.DocumentVersionOperationLogMapper;
import com.nexarag.document.model.dto.DocumentVersionUploadDTO;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.model.entity.DocumentVersionOperationLogDO;
import com.nexarag.document.model.vo.DocumentVersionOperationLogVO;
import com.nexarag.document.model.vo.DocumentVersionVO;
import com.nexarag.document.service.DocumentDeleteTaskService;
import com.nexarag.document.service.DocumentVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nexarag.document.constants.DocumentConstants.*;

/**
 * 文档版本服务实现，为上传、重试和发布流程提供版本级的一致性边界。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentVersionServiceImpl extends ServiceImpl<DocumentVersionMapper, DocumentVersionDO>
        implements DocumentVersionService {

    private final DocumentMapper documentMapper;
    private final DocumentVersionMapper documentVersionMapper;
    private final DocumentVersionOperationLogMapper operationLogMapper;
    private final DocumentDeleteTaskService documentDeleteTaskService;

    /**
     * 创建下一版本并占用构建指针，防止同一文档并发构建多个版本。
     *
     * @param documentId 文档ID
     * @param upload     文件快照
     * @param processId  处理轮次ID
     * @param operatorId 操作者ID
     * @return 新建版本
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVersionDO createNextVersion(Long documentId, DocumentVersionUploadDTO upload,
                                               String processId, String operatorId) {
        validateCreateRequest(documentId, upload, processId);
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new ClientException("文档不存在，documentId=" + documentId, DocumentErrorCode.DOCUMENT_NOT_FOUND);
        }
        if (document.getBuildingVersionId() != null) {
            throw new ClientException("文档存在处理中版本，documentId=" + documentId,
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }

        // 1. 生成版本ID并通过条件更新占用唯一构建槽位。
        long documentVersionId = IdWorker.getId();
        int pointerUpdated = documentMapper.trySetBuildingVersionId(documentId, documentVersionId);
        if (pointerUpdated != 1) {
            throw new ClientException("文档存在处理中版本或状态已变化，documentId=" + documentId,
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }

        // 2. 写入不可变文件快照和初始处理状态。
        long nextRevisionNo = nextRevisionNo(documentId);
        DocumentVersionDO documentVersion = DocumentVersionDO.builder()
                .documentVersionId(documentVersionId)
                .documentId(documentId)
                .revisionNo(nextRevisionNo)
                .originalFileName(upload.originalFileName())
                .fileType(upload.fileType())
                .fileSize(upload.fileSize())
                .originalFileUrl(upload.originalFileUrl())
                .originalObjectName(upload.originalObjectName())
                .sourceType(upload.sourceType())
                .sourceUrl(upload.sourceUrl())
                .status(DocumentVersionStatus.UPLOADED)
                .processId(processId)
                .consumedTimes(0)
                .retryCount(0)
                .maxRetryCount(MAX_RETRY_COUNT)
                .cleanupRetryCount(0)
                .createBy(operatorId)
                .updateBy(operatorId)
                .build();
        documentVersionMapper.insert(documentVersion);

        // 3. 同事务记录上传审计，确保版本生命周期可追溯。
        saveOperationLog(documentId, documentVersionId, DocumentVersionOperationType.UPLOAD,
                null, operatorId, "创建文档版本，revisionNo=" + nextRevisionNo);
        log.info("创建文档版本成功，documentId={}，documentVersionId={}，revisionNo={}",
                documentId, documentVersionId, nextRevisionNo);
        return documentVersion;
    }

    /**
     * 分页查询一个文档的版本历史。
     *
     * @param documentId 文档ID
     * @param pageNum    页码
     * @param pageSize   每页数量
     * @return 文档版本分页结果
     */
    @Override
    public PageVO<DocumentVersionVO> listVersions(Long documentId, long pageNum, long pageSize) {
        long safePageNum = pageNum <= 0 ? 1 : pageNum;
        long safePageSize = normalizePageSize(pageSize);
        Page<DocumentVersionDO> page = documentVersionMapper.selectPage(Page.of(safePageNum, safePageSize),
                new LambdaQueryWrapper<DocumentVersionDO>()
                        .eq(DocumentVersionDO::getDocumentId, documentId)
                        .orderByDesc(DocumentVersionDO::getRevisionNo));
        Document document = documentMapper.selectById(documentId);
        Long activeVersionId = document == null ? null : document.getActiveVersionId();
        List<DocumentVersionVO> records = page.getRecords().stream()
                .map(documentVersion -> toVersionVO(documentVersion, activeVersionId))
                .toList();
        return new PageVO<>(records, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    @Override
    public DocumentVersionVO getVersionDetail(Long documentId, Long documentVersionId) {
        DocumentVersionDO documentVersion = getRequiredVersion(documentId, documentVersionId);
        Document document = documentMapper.selectById(documentId);
        return toVersionVO(documentVersion, document == null ? null : document.getActiveVersionId());
    }

    @Override
    public PageVO<DocumentVersionOperationLogVO> listOperationLogs(Long documentId, long pageNum, long pageSize) {
        long safePageNum = pageNum <= 0 ? 1 : pageNum;
        long safePageSize = normalizePageSize(pageSize);
        Page<DocumentVersionOperationLogDO> page = operationLogMapper.selectPage(Page.of(safePageNum, safePageSize),
                new LambdaQueryWrapper<DocumentVersionOperationLogDO>()
                        .eq(DocumentVersionOperationLogDO::getDocumentId, documentId)
                        .orderByDesc(DocumentVersionOperationLogDO::getCreateTime)
                        .orderByDesc(DocumentVersionOperationLogDO::getOperationLogId));
        List<DocumentVersionOperationLogVO> records = page.getRecords().stream().map(this::toOperationLogVO).toList();
        return new PageVO<>(records, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    /**
     * 获取指定文档归属的版本。
     *
     * @param documentId        文档ID
     * @param documentVersionId 文档版本ID
     * @return 文档版本
     */
    @Override
    public DocumentVersionDO getRequiredVersion(Long documentId, Long documentVersionId) {
        if (documentId == null || documentVersionId == null) {
            throw new ClientException("文档ID和文档版本ID不能为空", DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
        DocumentVersionDO documentVersion = documentVersionMapper.selectOne(new LambdaQueryWrapper<DocumentVersionDO>()
                .eq(DocumentVersionDO::getDocumentId, documentId)
                .eq(DocumentVersionDO::getDocumentVersionId, documentVersionId));
        if (documentVersion == null) {
            throw new ClientException("文档版本不存在或不属于文档，documentId=" + documentId
                    + "，documentVersionId=" + documentVersionId, DocumentErrorCode.DOCUMENT_NOT_FOUND);
        }
        return documentVersion;
    }

    /**
     * 查询当前生效版本，不允许回退读取文档遗留处理字段。
     *
     * @param document 文档稳定身份记录
     * @return 当前生效版本，不存在或指针失效时返回 null
     */
    @Override
    public DocumentVersionDO getActiveVersionOrNull(Document document) {
        if (document == null || document.getDocumentId() == null || document.getActiveVersionId() == null) {
            return null;
        }
        return findActiveVersions(List.of(document)).get(document.getDocumentId());
    }

    /**
     * 批量解析文档活跃版本，避免文档列表为每行单独查询版本。
     *
     * @param documents 文档稳定身份记录集合
     * @return 按文档ID索引的活跃版本
     */
    @Override
    public Map<Long, DocumentVersionDO> findActiveVersions(Collection<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return Map.of();
        }

        // 1. 收集有效的版本指针，避免无生效版本文档参与数据库查询。
        Map<Long, Document> documentsByActiveVersionId = new HashMap<>();
        for (Document document : documents) {
            if (document != null && document.getDocumentId() != null && document.getActiveVersionId() != null) {
                documentsByActiveVersionId.put(document.getActiveVersionId(), document);
            }
        }
        if (documentsByActiveVersionId.isEmpty()) {
            return Map.of();
        }

        // 2. 批量查询版本并校验版本归属，拒绝损坏指针投影为旧字段。
        Map<Long, DocumentVersionDO> activeVersions = new HashMap<>();
        for (DocumentVersionDO documentVersion : documentVersionMapper.selectList(
                new LambdaQueryWrapper<DocumentVersionDO>().in(DocumentVersionDO::getDocumentVersionId,
                        documentsByActiveVersionId.keySet()))) {
            Document document = documentsByActiveVersionId.get(documentVersion.getDocumentVersionId());
            if (document != null && document.getDocumentId().equals(documentVersion.getDocumentId())) {
                activeVersions.put(document.getDocumentId(), documentVersion);
            }
        }

        // 3. 对缺失或归属不符的版本指针进行告警，调用方统一返回无生效版本空态。
        for (Document document : documentsByActiveVersionId.values()) {
            if (!activeVersions.containsKey(document.getDocumentId())) {
                log.warn("文档活跃版本指针无效，documentId={}，activeVersionId={}",
                        document.getDocumentId(), document.getActiveVersionId());
            }
        }
        return Map.copyOf(activeVersions);
    }

    /**
     * 仅在指定版本和处理轮次仍匹配时推进至索引中状态。
     */
    @Override
    public boolean markIndexing(Long documentId, Long documentVersionId, String processId) {
        validateProcessBoundary(documentId, documentVersionId, processId);
        return documentVersionMapper.markIndexing(documentId, documentVersionId, processId) == 1;
    }

    /**
     * 将当前处理轮次的索引预热结果发布为生效版本，并记录自动发布审计。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markIndexReady(Long documentId, Long documentVersionId, String processId) {
        // 1. 先基于版本和处理轮次完成状态推进，拒绝旧消息覆盖当前处理
        validateProcessBoundary(documentId, documentVersionId, processId);
        if (documentVersionMapper.markIndexReady(documentId, documentVersionId, processId) != 1) {
            return false;
        }

        // 2. 仅允许当前构建版本切换生效指针，失败时回滚版本终态更新
        if (documentMapper.activateVersion(documentId, documentVersionId) != 1) {
            throw new ClientException("文档版本生效指针更新失败，documentId=" + documentId
                    + "，documentVersionId=" + documentVersionId, DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
        Document document = documentMapper.selectById(documentId);
        long activationGeneration = document == null || document.getActivationGeneration() == null
                ? 0L : document.getActivationGeneration();

        // 3. 自动发布必须可审计，供后续版本回退和永久删除追溯
        saveOperationLog(documentId, documentVersionId, DocumentVersionOperationType.AUTO_PUBLISH,
                activationGeneration, SYSTEM_OPERATOR, "索引预热完成后自动切换生效版本");
        log.info("文档版本索引预热完成并已自动生效，documentId={}，documentVersionId={}，activationGeneration={}",
                documentId, documentVersionId, activationGeneration);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activateReadyVersion(Long documentId, Long documentVersionId, String operatorId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new ClientException("文档不存在，documentId=" + documentId, DocumentErrorCode.DOCUMENT_NOT_FOUND);
        }
        DocumentVersionDO documentVersion = getRequiredVersion(documentId, documentVersionId);
        if (documentVersion.getStatus() != DocumentVersionStatus.INDEX_READY
                || documentVersionId.equals(document.getActiveVersionId())) {
            throw new ClientException("目标版本不可切换生效，documentVersionId=" + documentVersionId,
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
        if (documentMapper.activateReadyVersion(documentId, documentVersionId) != 1) {
            throw new ClientException("文档版本切换生效失败，documentId=" + documentId
                    + "，documentVersionId=" + documentVersionId, DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
        Document activatedDocument = documentMapper.selectById(documentId);
        long activationGeneration = activatedDocument == null || activatedDocument.getActivationGeneration() == null
                ? 0L : activatedDocument.getActivationGeneration();
        saveOperationLog(documentId, documentVersionId, DocumentVersionOperationType.ROLLBACK, activationGeneration,
                operatorId == null || operatorId.isBlank() ? SYSTEM_OPERATOR : operatorId, "切换至已预热历史版本");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVersionDO retryFailedVersion(Long documentId, Long documentVersionId, String processId,
                                                String operatorId) {
        validateProcessBoundary(documentId, documentVersionId, processId);
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new ClientException("文档不存在，documentId=" + documentId, DocumentErrorCode.DOCUMENT_NOT_FOUND);
        }
        if (document.getBuildingVersionId() != null) {
            throw new ClientException("文档存在处理中版本，documentId=" + documentId,
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
        DocumentVersionDO documentVersion = getRequiredVersion(documentId, documentVersionId);
        if (documentVersion.getStatus() != DocumentVersionStatus.FAILED) {
            throw new ClientException("仅失败版本可重试，documentVersionId=" + documentVersionId,
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
        String safeOperatorId = operatorId == null || operatorId.isBlank() ? SYSTEM_OPERATOR : operatorId;
        if (documentMapper.trySetBuildingVersionId(documentId, documentVersionId) != 1
                || documentVersionMapper.retryFailedVersion(documentId, documentVersionId, processId, safeOperatorId) != 1) {
            throw new ClientException("失败版本重新入队失败，documentVersionId=" + documentVersionId,
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
        documentVersion.setStatus(DocumentVersionStatus.QUEUED);
        documentVersion.setProcessId(processId);
        documentVersion.setFailureStage(null);
        documentVersion.setFailureReason(null);
        documentVersion.setFailureDetail(null);
        saveOperationLog(documentId, documentVersionId, DocumentVersionOperationType.RETRY, null,
                safeOperatorId, "重新提交失败版本处理");
        return documentVersion;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void requestPermanentDelete(Long documentId, Long documentVersionId, String operatorId) {
        // 1. 校验稳定文档指针，禁止删除生效版本或构建中版本。
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new ClientException("文档不存在，documentId=" + documentId, DocumentErrorCode.DOCUMENT_NOT_FOUND);
        }
        if (documentVersionId.equals(document.getActiveVersionId())) {
            throw new ClientException("不允许永久删除当前生效版本，documentVersionId=" + documentVersionId,
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
        if (documentVersionId.equals(document.getBuildingVersionId())) {
            throw new ClientException("不允许永久删除构建中版本，documentVersionId=" + documentVersionId,
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
        getRequiredVersion(documentId, documentVersionId);
        String safeOperatorId = operatorId == null || operatorId.isBlank() ? SYSTEM_OPERATOR : operatorId;

        // 2. 使用条件更新占用删除状态，避免并发请求重复创建清理任务。
        if (documentVersionMapper.markDeleting(documentId, documentVersionId, safeOperatorId) != 1) {
            throw new ClientException("目标版本当前状态不允许永久删除，documentVersionId=" + documentVersionId,
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }

        // 3. 同一事务写入索引清理任务和永久保留审计记录。
        documentDeleteTaskService.createVersionIndexCleanupTask(documentId, documentVersionId);
        saveOperationLog(documentId, documentVersionId, DocumentVersionOperationType.DELETE, null,
                safeOperatorId, "受理历史版本永久删除");
    }

    @Override
    public List<DocumentVersionDO> markAllVersionsDeleting(Long documentId, String operatorId) {
        if (documentId == null || documentId <= 0) {
            throw new ClientException("文档ID必须大于0", DocumentErrorCode.DOCUMENT_NOT_FOUND);
        }
        List<DocumentVersionDO> versions = documentVersionMapper.selectList(new LambdaQueryWrapper<DocumentVersionDO>()
                .eq(DocumentVersionDO::getDocumentId, documentId)
                .ne(DocumentVersionDO::getStatus, DocumentVersionStatus.DELETING));
        if (versions.isEmpty()) {
            return List.of();
        }
        String safeOperatorId = operatorId == null || operatorId.isBlank() ? SYSTEM_OPERATOR : operatorId;
        List<DocumentVersionDO> acceptedVersions = new java.util.ArrayList<>();
        for (DocumentVersionDO version : versions) {
            if (documentVersionMapper.markDeletingForDocument(documentId, version.getDocumentVersionId(), safeOperatorId) != 1) {
                continue;
            }
            version.setStatus(DocumentVersionStatus.DELETING);
            version.setCleanupStatus(DocumentTaskStatus.PENDING.name());
            version.setCleanupRetryCount(0);
            version.setCleanupFailureReason(null);
            acceptedVersions.add(version);
            saveOperationLog(documentId, version.getDocumentVersionId(), DocumentVersionOperationType.DELETE, null,
                    safeOperatorId, "受理整篇文档永久删除");
        }
        return List.copyOf(acceptedVersions);
    }

    /**
     * 记录指定版本的消息消费状态，避免旧消息覆盖新处理轮次。
     *
     * @param documentId        文档ID
     * @param documentVersionId 文档版本ID
     * @param processId         处理轮次ID
     * @param messageId         消息ID
     * @param consumedTimes     当前消息累计消费次数
     * @return 是否成功更新
     */
    @Override
    public boolean recordMessageConsumption(Long documentId, Long documentVersionId, String processId,
                                            String messageId, int consumedTimes) {
        if (documentId == null || documentVersionId == null || processId == null || processId.isBlank()
                || consumedTimes <= 0) {
            throw new ClientException("消息消费处理边界参数不完整", DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
        return documentVersionMapper.recordMessageConsumption(documentId, documentVersionId, processId,
                messageId, consumedTimes) == 1;
    }

    /**
     * 记录可重试异常，旧处理轮次或终态版本不允许被覆盖。
     */
    @Override
    public boolean recordRetryableFailure(Long documentId, Long documentVersionId, String processId,
                                          String failureStage, String failureReason, String failureDetail) {
        validateProcessBoundary(documentId, documentVersionId, processId);
        return documentVersionMapper.recordRetryableFailure(documentId, documentVersionId, processId,
                failureStage, failureReason, failureDetail) == 1;
    }

    /**
     * 仅在版本索引已预热且处理轮次未变化时标记消息完成。
     */
    @Override
    public boolean markMessageCompleted(Long documentId, Long documentVersionId, String processId) {
        validateProcessBoundary(documentId, documentVersionId, processId);
        return documentVersionMapper.markMessageCompleted(documentId, documentVersionId, processId) == 1;
    }

    /**
     * 标记最终失败并释放该版本占用的构建槽位。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markProcessFailed(Long documentId, Long documentVersionId, String processId,
                                     String failureStage, String failureReason, String failureDetail,
                                     int consumedTimes, String messageId, LocalDateTime failureTime) {
        validateProcessBoundary(documentId, documentVersionId, processId);
        int safeConsumedTimes = Math.max(consumedTimes, 1);
        LocalDateTime safeFailureTime = failureTime == null ? LocalDateTime.now() : failureTime;
        boolean updated = documentVersionMapper.markProcessFailed(documentId, documentVersionId, processId,
                failureStage, failureReason, failureDetail, safeConsumedTimes, messageId, safeFailureTime) == 1;
        if (updated) {
            documentMapper.clearBuildingVersionId(documentId, documentVersionId);
            log.error("文档版本处理轮次已标记为最终失败，documentId={}，documentVersionId={}，processId={}，failureStage={}",
                    documentId, documentVersionId, processId, failureStage);
        }
        return updated;
    }

    private long nextRevisionNo(Long documentId) {
        Long maxRevisionNo = documentVersionMapper.selectMaxRevisionNo(documentId);
        return (maxRevisionNo == null ? 0 : maxRevisionNo) + 1;
    }

    private void validateCreateRequest(Long documentId, DocumentVersionUploadDTO upload, String processId) {
        if (documentId == null) {
            throw new ClientException("文档ID不能为空", DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
        if (upload == null || upload.originalFileName() == null || upload.originalFileName().isBlank()
                || upload.fileType() == null) {
            throw new ClientException("文档版本文件信息不完整", DocumentErrorCode.DOCUMENT_UPLOAD_FILE_INVALID);
        }
        if (processId == null || processId.isBlank()) {
            throw new ClientException("文档版本处理批次ID不能为空", DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
    }

    private void validateProcessBoundary(Long documentId, Long documentVersionId, String processId) {
        if (documentId == null || documentVersionId == null || processId == null || processId.isBlank()) {
            throw new ClientException("文档版本处理边界参数不完整", DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
    }

    private long normalizePageSize(long pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private DocumentVersionVO toVersionVO(DocumentVersionDO documentVersion, Long activeVersionId) {
        return new DocumentVersionVO(documentVersion.getDocumentVersionId(), documentVersion.getRevisionNo(),
                documentVersion.getDocumentVersionId().equals(activeVersionId),
                documentVersion.getOriginalFileName(), documentVersion.getStatus(), documentVersion.getFailureStage(),
                documentVersion.getFailureReason(), documentVersion.getIndexReadyTime(), documentVersion.getCreateTime());
    }

    private DocumentVersionOperationLogVO toOperationLogVO(DocumentVersionOperationLogDO operationLog) {
        return new DocumentVersionOperationLogVO(operationLog.getOperationLogId(), operationLog.getDocumentVersionId(),
                operationLog.getOperationType(), operationLog.getActivationGeneration(), operationLog.getOperatorId(),
                operationLog.getOperationDetail(), operationLog.getCreateTime());
    }

    private void saveOperationLog(Long documentId, Long documentVersionId, DocumentVersionOperationType operationType,
                                  Long activationGeneration, String operatorId, String detail) {
        operationLogMapper.insert(DocumentVersionOperationLogDO.builder()
                .operationLogId(IdWorker.getId())
                .documentId(documentId)
                .documentVersionId(documentVersionId)
                .operationType(operationType)
                .activationGeneration(activationGeneration)
                .operatorId(operatorId)
                .operationDetail(detail)
                .createTime(LocalDateTime.now())
                .build());
    }
}
