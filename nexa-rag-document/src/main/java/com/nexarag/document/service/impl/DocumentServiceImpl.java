package com.nexarag.document.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.exception.ClientException;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.enums.ExternalDocumentSourceType;
import com.nexarag.common.web.PageVO;
import com.nexarag.document.converter.DocumentConverter;
import com.nexarag.document.model.dto.CreateDocumentRequest;
import com.nexarag.document.model.dto.ProcessDocumentRequest;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentChunk;
import com.nexarag.document.enums.ChunkStatus;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.DocumentPipelineMessageStatus;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.enums.DocumentErrorCode;
import com.nexarag.document.mapper.DocumentMapper;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.document.service.DocumentDeleteTaskService;
import com.nexarag.document.enums.DocumentTaskStatus;
import com.nexarag.document.model.vo.DocumentDeleteVO;
import com.nexarag.document.model.vo.DocumentChunkStatisticsVO;
import com.nexarag.document.model.vo.DocumentOverviewVO;
import com.nexarag.document.model.vo.DocumentSummaryVO;
import com.nexarag.infra.config.DocumentPipelineMessagingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档服务实现类，负责文档记录创建、处理提交、自动重试控制和逻辑删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {

    private static final String QUEUE_STAGE_PIPELINE = "PIPELINE";
    private static final long DEFAULT_PAGE_SIZE = 20;
    private static final long MAX_PAGE_SIZE = 100;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DocumentPipelineMessagingProperties messagingProperties;
    private final DocumentChunkService documentChunkService;
    private final DocumentDeleteTaskService deleteTaskService;

    @Override
    public Document createDocument(CreateDocumentRequest request) {
        FileType fileType = FileType.fromFileName(request.originalFileName());
        if (fileType == FileType.UNKNOWN) {
            throw new ClientException("不支持的文档类型，fileName=" + request.originalFileName(),
                    DocumentErrorCode.DOCUMENT_FILE_TYPE_UNSUPPORTED);
        }

        // 1. 构建文档实体，初始化处理和重试状态
        Document document = Document.builder()
                .documentId(IdWorker.getId())
                .title(request.title())
                .description(request.description())
                .originalFileName(request.originalFileName())
                .originalObjectName(request.originalObjectName())
                .originalFileUrl(request.originalFileUrl())
                .fileSize(request.fileSize())
                .sourceType(request.sourceType() == null ? ExternalDocumentSourceType.LOCAL : request.sourceType())
                .sourceUrl(request.sourceUrl())
                .fileType(fileType)
                .status(DocumentStatus.UPLOADED)
                .retryCount(0)
                .maxRetryCount(messagingProperties.getMaxReconsumeTimes())
                .cleanupRetryCount(0)
                .build();

        // 2. 保存文档记录
        this.save(document);
        log.info("创建文档记录成功，documentId={}，fileType={}，status={}",
                document.getDocumentId(), document.getFileType(), document.getStatus());
        return document;
    }

    @Override
    public PageVO<DocumentSummaryVO> pageDocuments(long pageNum, long pageSize) {
        long safePageNum = pageNum <= 0 ? 1 : pageNum;
        long safePageSize = normalizePageSize(pageSize);

        // 1. 使用分页对象限制单次查询规模
        IPage<Document> page = queryDocumentPage(safePageNum, safePageSize);

        // 2. 转换为文档摘要分页响应
        List<DocumentSummaryVO> records = page.getRecords().stream()
                .map(DocumentConverter::toSummaryVO)
                .toList();
        return new PageVO<>(records, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    /**
     * 分页查询文档实体。
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 文档实体分页数据
     */
    protected IPage<Document> queryDocumentPage(long pageNum, long pageSize) {
        Page<Document> page = Page.of(pageNum, pageSize);
        return this.lambdaQuery()
                .orderByDesc(Document::getCreateTime)
                .page(page);
    }

    @Override
    public DocumentOverviewVO getOverview(Long documentId) {
        // 1. 查询文档记录，不存在时抛出异常
        Document document = getRequiredDocument(documentId);

        // 2. 按状态统计文档片段数量
        DocumentChunkStatisticsVO statistics = new DocumentChunkStatisticsVO(
                countChunks(documentId, null),
                countChunks(documentId, ChunkStatus.INDEXED),
                countChunks(documentId, ChunkStatus.FAILED),
                countChunks(documentId, ChunkStatus.SKIP_INDEX),
                countChunks(documentId, ChunkStatus.PENDING_INDEX));

        // 3. 组装诊断概览响应
        return DocumentConverter.toOverviewVO(document, statistics);
    }

    /**
     * 统计指定文档在给定状态下的片段数量，状态为空时统计全部片段。
     *
     * @param documentId 文档ID
     * @param status     片段状态
     * @return 片段数量
     */
    private long countChunks(Long documentId, ChunkStatus status) {
        LambdaQueryWrapper<DocumentChunk> wrapper = new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, documentId);
        if (status != null) {
            wrapper.eq(DocumentChunk::getStatus, status);
        }
        return documentChunkService.count(wrapper);
    }

    @Override
    public Document submitProcess(Long documentId, ProcessDocumentRequest request, String processId) {
        validateProcessId(processId);
        Document document = getRequiredDocument(documentId);
        DocumentStatus oldStatus = document.getStatus();
        if (!oldStatus.canTransferTo(DocumentStatus.QUEUED)) {
            throw new ClientException("文档状态不允许提交处理，documentId=" + documentId + "，status=" + oldStatus,
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }

        // 1. 更新处理配置快照和排队状态
        Document updatedDocument = document.toBuilder()
                .processConfigJson(serializeProcessConfig(request))
                .status(DocumentStatus.QUEUED)
                .queueStage(QUEUE_STAGE_PIPELINE)
                .queueTime(LocalDateTime.now())
                .processId(processId)
                .messageStatus(DocumentPipelineMessageStatus.PENDING_PUBLISH)
                .consumedTimes(0)
                .lastMessageId(null)
                .retryCount(0)
                .maxRetryCount(messagingProperties.getMaxReconsumeTimes())
                .lastRetryTime(null)
                .failureStage(null)
                .failureReason(null)
                .failureDetail(null)
                .build();

        // 2. 使用原状态作为条件，避免并发重复提交
        if (documentStatusUpdateFailed(updatedDocument, oldStatus)) {
            throw new ClientException("文档状态已变化，请刷新后重试，文档ID" + documentId, DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
        log.info("文档提交处理成功，文档ID：{}，原状态：{} -> 当前状态：{}", documentId, oldStatus, updatedDocument.getStatus());
        return document;
    }

    @Override
    public Document recordProcessFailure(Long documentId, String failureStage, String failureReason, String failureDetail) {
        Document document = getRequiredDocument(documentId);
        DocumentStatus oldStatus = document.getStatus();
        int retryCount = document.getRetryCount() == null ? 0 : document.getRetryCount();
        int maxRetryCount = normalizeMaxRetryCount(document.getMaxRetryCount());
        LocalDateTime now = LocalDateTime.now();

        // 1. 记录最近一次失败信息，便于前端展示和排查问题
        document.setFailureStage(failureStage);
        document.setFailureReason(failureReason);
        document.setFailureDetail(failureDetail);

        // 2. 未达到最大自动重试次数时，重新进入处理队列
        if (retryCount < maxRetryCount) {
            document.setRetryCount(retryCount + 1);
            document.setLastRetryTime(now);
            document.setStatus(DocumentStatus.QUEUED);
            document.setQueueStage(QUEUE_STAGE_PIPELINE);
            document.setQueueTime(now);
            document.setProcessEndTime(null);
            if (documentFailureUpdateFailed(document, oldStatus)) {
                throw new ClientException("文档状态已变化，请刷新后重试，documentId=" + documentId,
                        DocumentErrorCode.DOCUMENT_STATUS_INVALID);
            }
            log.warn("文档处理失败，已进入自动重试队列，documentId={}，failureStage={}，retryCount={}，maxRetryCount={}",
                    documentId, failureStage, document.getRetryCount(), maxRetryCount);
            return document;
        }

        // 3. 已达到最大自动重试次数时，标记为最终失败
        document.setRetryCount(retryCount);
        document.setStatus(DocumentStatus.FAILED);
        document.setProcessEndTime(now);
        if (documentFailureUpdateFailed(document, oldStatus)) {
            throw new ClientException("文档状态已变化，请刷新后重试，documentId=" + documentId,
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
        log.error("文档处理失败且自动重试已达上限，documentId={}，failureStage={}，retryCount={}，maxRetryCount={}",
                documentId, failureStage, retryCount, maxRetryCount);
        return document;
    }

    @Override
    public Document retryProcess(Long documentId, String processId) {
        validateProcessId(processId);
        Document document = getRequiredDocument(documentId);
        DocumentStatus oldStatus = document.getStatus();
        if (oldStatus != DocumentStatus.FAILED) {
            throw new ClientException("只有失败文档允许人工重试，documentId=" + documentId + "，status=" + oldStatus,
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }

        // 1. 人工重试代表开启新一轮处理，重置本轮自动重试计数
        document.setRetryCount(0);
        document.setMaxRetryCount(messagingProperties.getMaxReconsumeTimes());
        document.setLastRetryTime(null);
        document.setProcessEndTime(null);
        document.setFailureStage(null);
        document.setFailureReason(null);
        document.setFailureDetail(null);

        // 2. 重新进入排队状态，后续失败仍由自动重试逻辑接管
        document.setStatus(DocumentStatus.QUEUED);
        document.setQueueStage(QUEUE_STAGE_PIPELINE);
        document.setQueueTime(LocalDateTime.now());
        document.setProcessId(processId);
        document.setMessageStatus(DocumentPipelineMessageStatus.PENDING_PUBLISH);
        document.setConsumedTimes(0);
        document.setLastMessageId(null);
        if (documentRetryUpdateFailed(document, oldStatus)) {
            throw new ClientException("文档状态已变化，请刷新后重试，documentId=" + documentId,
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
        log.warn("文档人工重试已重新入队，documentId={}，oldStatus={}，newStatus={}",
                documentId, oldStatus, document.getStatus());
        return document;
    }

    @Override
    public boolean recordMessageConsumption(Long documentId, String processId, String messageId, int consumedTimes) {
        DocumentPipelineMessageStatus messageStatus = consumedTimes > 1
                ? DocumentPipelineMessageStatus.RETRYING
                : DocumentPipelineMessageStatus.PROCESSING;
        int retryCount = Math.max(consumedTimes - 1, 0);
        LocalDateTime retryTime = retryCount > 0 ? LocalDateTime.now() : null;

        // 1. 仅更新当前处理轮次且尚未进入终态的文档
        return this.lambdaUpdate()
                .eq(Document::getDocumentId, documentId)
                .eq(Document::getProcessId, processId)
                .notIn(Document::getStatus, DocumentStatus.INDEXED, DocumentStatus.FAILED)
                .set(Document::getMessageStatus, messageStatus)
                .set(Document::getConsumedTimes, consumedTimes)
                .set(Document::getRetryCount, retryCount)
                .set(retryCount > 0, Document::getLastRetryTime, retryTime)
                .set(Document::getLastMessageId, messageId)
                .update();
    }

    @Override
    public boolean markMessageCompleted(Long documentId, String processId) {
        // 1. 仅允许当前处理轮次且已完成索引的文档标记消息完成
        return this.lambdaUpdate()
                .eq(Document::getDocumentId, documentId)
                .eq(Document::getProcessId, processId)
                .eq(Document::getStatus, DocumentStatus.INDEXED)
                .set(Document::getMessageStatus, DocumentPipelineMessageStatus.COMPLETED)
                .update();
    }

    @Override
    public boolean markProcessFailed(Long documentId, String processId, String failureStage,
                                     String failureReason, String failureDetail, int consumedTimes,
                                     String messageId, LocalDateTime failureTime) {
        int safeConsumedTimes = Math.max(consumedTimes, 1);
        int retryCount = Math.max(safeConsumedTimes - 1, 0);
        LocalDateTime now = failureTime == null ? LocalDateTime.now() : failureTime;

        // 1. 仅允许当前处理轮次进入最终失败，避免旧消息覆盖人工重试状态
        var updateChain = this.lambdaUpdate()
                .eq(Document::getDocumentId, documentId)
                .eq(Document::getProcessId, processId)
                .notIn(Document::getStatus, DocumentStatus.INDEXED, DocumentStatus.FAILED)
                .set(Document::getStatus, DocumentStatus.FAILED)
                .set(Document::getMessageStatus, DocumentPipelineMessageStatus.FAILED)
                .set(Document::getFailureStage, failureStage)
                .set(Document::getFailureReason, failureReason)
                .set(Document::getFailureDetail, failureDetail)
                .set(Document::getConsumedTimes, safeConsumedTimes)
                .set(Document::getRetryCount, retryCount)
                .set(Document::getLastMessageId, messageId)
                .set(retryCount > 0, Document::getLastRetryTime, now)
                .set(Document::getProcessEndTime, now);
        boolean updated = updateChain.update();
        if (updated) {
            log.error("文档处理轮次已标记为最终失败，documentId={}，processId={}，failureStage={}",
                    documentId, processId, failureStage);
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentDeleteVO deleteDocument(Long documentId) {
        Document document = getRequiredDocument(documentId);

        // 1. 通过统一逻辑删除入口写入删除标记和删除时间
        boolean deleted = logicDeleteDocument(documentId);
        if (!deleted) {
            return new DocumentDeleteVO(documentId, false, null, null);
        }

        // 2. 在同一事务内逻辑删除片段，失败时文档删除会一并回滚
        documentChunkService.deleteByDocumentId(documentId);

        // 3. 写入对象存储和索引清理任务；事务提交后才会执行外部副作用
        deleteTaskService.createStorageCleanupTask(document);
        Long cleanupOutboxId = deleteTaskService.createIndexCleanupTask(documentId);
        log.info("删除文档及片段并创建对象存储和索引清理任务完成，documentId={}，cleanupOutboxId={}",
                documentId, cleanupOutboxId);
        return new DocumentDeleteVO(documentId, true, cleanupOutboxId, DocumentTaskStatus.PENDING);
    }

    @Override
    public Document getRequiredDocument(Long documentId) {
        Document document = this.getById(documentId);
        if (document == null) {
            throw new ClientException("文档不存在，documentId=" + documentId, DocumentErrorCode.DOCUMENT_NOT_FOUND);
        }
        return document;
    }

    @Override
    public boolean markChunking(Long documentId) {
        // 1. 仅允许已解析文档进入切分中状态，避免多个 Worker 重复执行切分
        return this.lambdaUpdate()
                .eq(Document::getDocumentId, documentId)
                .eq(Document::getStatus, DocumentStatus.PARSED)
                .set(Document::getStatus, DocumentStatus.CHUNKING)
                .update();
    }

    @Override
    public boolean markChunked(Long documentId) {
        // 1. 仅允许切分中文档进入完成状态，并清理历史失败信息
        return this.lambdaUpdate()
                .eq(Document::getDocumentId, documentId)
                .eq(Document::getStatus, DocumentStatus.CHUNKING)
                .set(Document::getStatus, DocumentStatus.CHUNKED)
                .set(Document::getFailureStage, null)
                .set(Document::getFailureReason, null)
                .set(Document::getFailureDetail, null)
                .set(Document::getProcessEndTime, LocalDateTime.now())
                .update();
    }

    @Override
    public boolean markIndexing(Long documentId, String processId) {
        // 1. 使用处理轮次和当前状态条件抢占索引阶段
        return this.lambdaUpdate()
                .eq(Document::getDocumentId, documentId)
                .eq(Document::getProcessId, processId)
                .eq(Document::getStatus, DocumentStatus.CHUNKED)
                .set(Document::getStatus, DocumentStatus.INDEXING)
                .update();
    }

    @Override
    public boolean markIndexed(Long documentId, String processId) {
        // 1. 仅允许当前处理轮次从索引中进入索引完成
        return this.lambdaUpdate()
                .eq(Document::getDocumentId, documentId)
                .eq(Document::getProcessId, processId)
                .eq(Document::getStatus, DocumentStatus.INDEXING)
                .set(Document::getStatus, DocumentStatus.INDEXED)
                .set(Document::getProcessEndTime, LocalDateTime.now())
                .update();
    }

    /**
     * 条件更新文档处理提交状态。
     *
     * @param document  当前文档实体
     * @param oldStatus 更新前状态
     * @return true 表示更新失败，false 表示更新成功
     */
    protected boolean documentStatusUpdateFailed(Document document, DocumentStatus oldStatus) {
        // 1. 使用 documentId 和旧状态作为条件，避免并发请求重复推进状态
        return !this.lambdaUpdate()
                .eq(Document::getDocumentId, document.getDocumentId())
                .eq(Document::getStatus, oldStatus)
                .set(Document::getStatus, document.getStatus())
                .set(Document::getQueueStage, document.getQueueStage())
                .set(Document::getQueueTime, document.getQueueTime())
                .set(Document::getProcessConfigJson, document.getProcessConfigJson())
                .set(Document::getRetryCount, document.getRetryCount())
                .set(Document::getLastRetryTime, document.getLastRetryTime())
                .set(Document::getProcessId, document.getProcessId())
                .set(Document::getMessageStatus, document.getMessageStatus())
                .set(Document::getConsumedTimes, document.getConsumedTimes())
                .set(Document::getLastMessageId, document.getLastMessageId())
                .set(Document::getFailureStage, document.getFailureStage())
                .set(Document::getFailureReason, document.getFailureReason())
                .set(Document::getFailureDetail, document.getFailureDetail())
                .update();
    }

    /**
     * 条件更新文档人工重试状态。
     *
     * @param document  当前文档实体
     * @param oldStatus 更新前状态
     * @return true 表示更新失败，false 表示更新成功
     */
    protected boolean documentRetryUpdateFailed(Document document, DocumentStatus oldStatus) {
        // 1. 使用 documentId 和旧状态作为条件，避免重复人工重试覆盖最新状态
        return !this.lambdaUpdate()
                .eq(Document::getDocumentId, document.getDocumentId())
                .eq(Document::getStatus, oldStatus)
                .set(Document::getStatus, document.getStatus())
                .set(Document::getQueueStage, document.getQueueStage())
                .set(Document::getQueueTime, document.getQueueTime())
                .set(Document::getRetryCount, document.getRetryCount())
                .set(Document::getLastRetryTime, document.getLastRetryTime())
                .set(Document::getFailureStage, document.getFailureStage())
                .set(Document::getFailureReason, document.getFailureReason())
                .set(Document::getFailureDetail, document.getFailureDetail())
                .set(Document::getProcessEndTime, document.getProcessEndTime())
                .set(Document::getProcessId, document.getProcessId())
                .set(Document::getMessageStatus, document.getMessageStatus())
                .set(Document::getConsumedTimes, document.getConsumedTimes())
                .set(Document::getLastMessageId, document.getLastMessageId())
                .update();
    }

    /**
     * 条件更新文档失败处理状态。
     *
     * @param document  当前文档实体
     * @param oldStatus 更新前状态
     * @return true 表示更新失败，false 表示更新成功
     */
    protected boolean documentFailureUpdateFailed(Document document, DocumentStatus oldStatus) {
        // 1. 使用 documentId 和旧状态作为条件，避免并发失败处理覆盖最新状态
        return !this.lambdaUpdate()
                .eq(Document::getDocumentId, document.getDocumentId())
                .eq(Document::getStatus, oldStatus)
                .set(Document::getStatus, document.getStatus())
                .set(Document::getQueueStage, document.getQueueStage())
                .set(Document::getQueueTime, document.getQueueTime())
                .set(Document::getRetryCount, document.getRetryCount())
                .set(Document::getLastRetryTime, document.getLastRetryTime())
                .set(Document::getFailureStage, document.getFailureStage())
                .set(Document::getFailureReason, document.getFailureReason())
                .set(Document::getFailureDetail, document.getFailureDetail())
                .set(Document::getProcessEndTime, document.getProcessEndTime())
                .update();
    }

    /**
     * 逻辑删除文档并写入删除时间。
     *
     * @param documentId 文档ID
     * @return true 表示删除成功，false 表示未删除
     */
    protected boolean logicDeleteDocument(Long documentId) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 显式写入删除标记和删除时间，避免 removeById 无法填充 deleteTime
        return this.lambdaUpdate()
                .eq(Document::getDocumentId, documentId)
                .eq(Document::getDelFlag, 0)
                .set(Document::getDelFlag, 1)
                .set(Document::getDeleteTime, now)
                .update();
    }

    private long normalizePageSize(long pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private int normalizeMaxRetryCount(Integer maxRetryCount) {
        if (maxRetryCount == null) {
            return messagingProperties.getMaxReconsumeTimes();
        }
        return Math.max(0, maxRetryCount);
    }

    private String serializeProcessConfig(ProcessDocumentRequest request) {
        if (request == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new ServiceException("序列化文档处理配置失败", exception,
                    DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
    }

    private void validateProcessId(String processId) {
        if (processId == null || processId.isBlank()) {
            throw new ClientException("文档处理批次ID不能为空", DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
    }
}
