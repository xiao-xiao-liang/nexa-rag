package com.nexarag.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexarag.common.exception.ClientException;
import com.nexarag.common.web.PageVO;
import com.nexarag.document.converter.DocumentConverter;
import com.nexarag.document.enums.ChunkStatus;
import com.nexarag.document.enums.DocumentErrorCode;
import com.nexarag.document.enums.DocumentTaskStatus;
import com.nexarag.document.mapper.DocumentMapper;
import com.nexarag.document.model.dto.CreateDocumentRequest;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentChunk;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.model.vo.*;
import com.nexarag.document.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.nexarag.document.constants.DocumentConstants.DEFAULT_PAGE_SIZE;
import static com.nexarag.document.constants.DocumentConstants.MAX_PAGE_SIZE;

/**
 * 文档服务实现类，负责稳定文档身份、当前生效版本投影和逻辑删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {

    private final DocumentChunkService documentChunkService;
    private final DocumentDeleteTaskService deleteTaskService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentVersionService documentVersionService;

    @Override
    public Document createDocument(CreateDocumentRequest request) {
        return createDocument(null, request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Document createDocument(Long knowledgeBaseId, CreateDocumentRequest request) {
        if (knowledgeBaseId != null) {
            // 1. 锁定有效知识库，避免与删除操作并发写入孤立文档。
            knowledgeBaseService.lockRequiredActiveKnowledgeBase(knowledgeBaseId);
        }
        // 2. 构建稳定文档身份；文件和处理生命周期仅属于 document_version。
        Document document = Document.builder()
                .documentId(IdWorker.getId())
                .knowledgeBaseId(knowledgeBaseId)
                .title(request.title())
                .description(request.description())
                .activationGeneration(0L)
                .build();

        // 3. 保存文档记录
        this.save(document);
        log.info("创建文档稳定身份成功，documentId={}，knowledgeBaseId={}",
                document.getDocumentId(), knowledgeBaseId);
        return document;
    }

    @Override
    public PageVO<DocumentSummaryVO> pageDocuments(long pageNum, long pageSize) {
        long safePageNum = pageNum <= 0 ? 1 : pageNum;
        long safePageSize = normalizePageSize(pageSize);

        // 1. 使用分页对象限制单次查询规模
        IPage<Document> page = queryDocumentPage(safePageNum, safePageSize);

        // 2. 批量解析当前生效版本，避免列表查询出现 N+1。
        Map<Long, DocumentVersionDO> activeVersions = documentVersionService.findActiveVersions(page.getRecords());
        List<DocumentSummaryVO> records = page.getRecords().stream()
                .map(document -> DocumentConverter.toSummaryVO(document, activeVersions.get(document.getDocumentId())))
                .toList();
        return new PageVO<>(records, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    @Override
    public PageVO<DocumentSummaryVO> pageDocuments(Long knowledgeBaseId, long pageNum, long pageSize) {
        long safePageNum = pageNum <= 0 ? 1 : pageNum;
        long safePageSize = normalizePageSize(pageSize);

        // 1. 仅查询指定知识库内的文档，避免跨知识库混合展示
        IPage<Document> page = this.lambdaQuery()
                .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(Document::getCreateTime)
                .page(Page.of(safePageNum, safePageSize));

        // 2. 批量解析当前生效版本，避免列表查询出现 N+1。
        Map<Long, DocumentVersionDO> activeVersions = documentVersionService.findActiveVersions(page.getRecords());
        List<DocumentSummaryVO> records = page.getRecords().stream()
                .map(document -> DocumentConverter.toSummaryVO(document, activeVersions.get(document.getDocumentId())))
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
    public DocumentDetailVO getDocumentDetail(Long documentId) {
        // 1. 查询稳定文档身份和当前生效版本。
        Document document = getRequiredDocument(documentId);
        DocumentVersionDO activeVersion = documentVersionService.getActiveVersionOrNull(document);

        // 2. 仅以当前生效版本构造详情，缺失指针返回兼容空态。
        return DocumentConverter.toDetailVO(document, activeVersion);
    }

    @Override
    public DocumentProcessStatusVO getProcessStatus(Long documentId) {
        // 1. 查询稳定文档身份和当前生效版本。
        Document document = getRequiredDocument(documentId);
        DocumentVersionDO activeVersion = documentVersionService.getActiveVersionOrNull(document);

        // 2. 构造当前生效版本处理状态，不暴露构建中版本进度。
        return DocumentConverter.toProcessStatusVO(document, activeVersion);
    }

    @Override
    public IPage<DocumentChunk> pageActiveVersionChunks(Long documentId, long pageNum, long pageSize) {
        long safePageNum = pageNum <= 0 ? 1 : pageNum;
        long safePageSize = normalizePageSize(pageSize);

        // 1. 查询当前生效版本；没有活跃版本时禁止回退扫描历史片段。
        DocumentVersionDO activeVersion = documentVersionService.getActiveVersionOrNull(getRequiredDocument(documentId));
        if (activeVersion == null) {
            return Page.of(safePageNum, safePageSize);
        }

        // 2. 仅查询当前生效版本的片段。
        return documentChunkService.pageByDocumentVersionId(activeVersion.getDocumentVersionId(), safePageNum,
                safePageSize);
    }

    @Override
    public DocumentOverviewVO getOverview(Long documentId) {
        // 1. 查询文档记录，不存在时抛出异常
        Document document = getRequiredDocument(documentId);

        // 2. 仅按当前生效版本统计片段；无活跃版本返回零统计。
        DocumentVersionDO activeVersion = documentVersionService.getActiveVersionOrNull(document);
        DocumentChunkStatisticsVO statistics = activeVersion == null
                ? new DocumentChunkStatisticsVO(0, 0, 0, 0, 0)
                : new DocumentChunkStatisticsVO(
                countChunks(activeVersion.getDocumentVersionId(), null),
                countChunks(activeVersion.getDocumentVersionId(), ChunkStatus.INDEXED),
                countChunks(activeVersion.getDocumentVersionId(), ChunkStatus.FAILED),
                countChunks(activeVersion.getDocumentVersionId(), ChunkStatus.SKIP_INDEX),
                countChunks(activeVersion.getDocumentVersionId(), ChunkStatus.PENDING_INDEX));

        // 3. 组装诊断概览响应
        return DocumentConverter.toOverviewVO(document, activeVersion, statistics);
    }

    /**
     * 统计指定文档在给定状态下的片段数量，状态为空时统计全部片段。
     *
     * @param documentVersionId 文档版本ID
     * @param status            片段状态
     * @return 片段数量
     */
    private long countChunks(Long documentVersionId, ChunkStatus status) {
        LambdaQueryWrapper<DocumentChunk> wrapper = new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentVersionId, documentVersionId);
        if (status != null) {
            wrapper.eq(DocumentChunk::getStatus, status);
        }
        return documentChunkService.count(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentDeleteVO deleteDocument(Long documentId, String operator) {
        Document document = getRequiredDocument(documentId);

        // 1. 通过统一逻辑删除入口写入删除标记和删除时间
        boolean deleted = logicDeleteDocument(documentId);
        if (!deleted) {
            return new DocumentDeleteVO(documentId, false, null, null);
        }

        // 2. 以版本为边界占用删除状态，阻止仍在处理中的版本继续推进。
        List<DocumentVersionDO> deletingVersions = documentVersionService.markAllVersionsDeleting(documentId, operator);

        // 3. 每个版本仅创建索引清理任务；索引消费者完成后再按同一版本快照派生对象清理任务。
        Long cleanupOutboxId = null;
        for (DocumentVersionDO version : deletingVersions) {
            Long versionIndexCleanupOutboxId = deleteTaskService.createVersionIndexCleanupTask(documentId,
                    version.getDocumentVersionId());
            if (cleanupOutboxId == null) {
                cleanupOutboxId = versionIndexCleanupOutboxId;
            }
        }
        log.info("删除文档并创建版本级索引清理任务完成，documentId={}，versionCount={}，cleanupOutboxId={}",
                documentId, deletingVersions.size(), cleanupOutboxId);
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

}
