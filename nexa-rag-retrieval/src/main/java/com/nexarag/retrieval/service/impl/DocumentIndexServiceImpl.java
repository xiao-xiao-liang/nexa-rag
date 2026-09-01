package com.nexarag.retrieval.service.impl;

import com.nexarag.common.exception.ClientException;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.enums.DocumentErrorCode;
import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.model.bo.DocumentChunkIndexWriteBO;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.service.DocumentVersionService;
import com.nexarag.retrieval.config.IndexConfigResolver;
import com.nexarag.retrieval.dto.req.KeywordIndexWriteRequest;
import com.nexarag.retrieval.dto.res.DocumentChunkIndexResult;
import com.nexarag.retrieval.dto.res.DocumentIndexResult;
import com.nexarag.retrieval.index.keyword.KeywordIndexClient;
import com.nexarag.retrieval.index.vector.DocumentVectorStore;
import com.nexarag.retrieval.model.*;
import com.nexarag.retrieval.repository.ChunkIndexRepository;
import com.nexarag.retrieval.repository.SectionNavigationIndexRepository;
import com.nexarag.retrieval.service.DocumentIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档索引服务实现，编排文档状态、片段查询、向量化、索引写入和结果回写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIndexServiceImpl implements DocumentIndexService {

    private final DocumentService documentService;
    private final DocumentVersionService documentVersionService;
    private final ChunkIndexRepository chunkIndexRepository;
    private final IndexConfigResolver indexConfigResolver;
    private final DocumentVectorStore documentVectorStore;
    private final KeywordIndexClient keywordIndexClient;
    private final SectionNavigationIndexRepository sectionNavigationIndexRepository;

    /**
     * 执行指定文档版本的索引写入，所有状态更新均受版本和处理轮次约束。
     *
     * @param documentId        文档ID
     * @param documentVersionId 文档版本ID
     * @return 文档索引结果
     */
    @Override
    public DocumentIndexResult indexDocument(Long documentId, Long documentVersionId) {
        // 1. 查询稳定文档与版本快照，索引配置和处理状态以版本数据为准
        documentService.getRequiredDocument(documentId);
        DocumentVersionDO documentVersion = documentVersionService.getRequiredVersion(documentId, documentVersionId);
        if (documentVersion.getStatus() == DocumentVersionStatus.INDEX_READY) {
            return new DocumentIndexResult(documentId, true, 0, 0, 0, 0,
                    false, false, null, List.of());
        }
        validateDocumentVersionStatus(documentVersion);
        if (documentVersion.getStatus() == DocumentVersionStatus.CHUNKED
                && !documentVersionService.markIndexing(documentId, documentVersionId, documentVersion.getProcessId())) {
            throw new ClientException("文档版本索引状态已变化，documentId=" + documentId
                    + "，documentVersionId=" + documentVersionId, DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }

        // 2. 仅处理当前版本片段，并按版本配置快照决定双索引写入策略
        IndexConfigSnapshot config = indexConfigResolver.resolve(documentVersion);
        boolean vectorEnabled = config.enabled() && config.vectorEnabled();
        boolean keywordEnabled = config.enabled() && config.keywordEnabled();
        chunkIndexRepository.markSkipped(documentId, documentVersionId);
        DocumentVersionChunkIndexContext indexContext = chunkIndexRepository.loadIndexContext(documentId, documentVersionId);
        List<IndexableChunk> chunks = indexContext.indexableChunks();
        int skippedChunkCount = indexContext.skippedChunkCount();
        if (chunks.isEmpty()) {
            if (vectorEnabled) {
                documentVectorStore.replaceDocumentVersion(documentId, documentVersionId, List.of());
            }
            if (keywordEnabled) {
                keywordIndexClient.deleteByDocumentVersionId(documentId, documentVersionId, config.keywordIndexName());
            }
            indexNavigation(documentId, documentVersionId, config);
            markVersionIndexReady(documentVersion);
            return new DocumentIndexResult(documentId, true, skippedChunkCount, 0, skippedChunkCount, 0,
                    vectorEnabled, keywordEnabled, null, List.of());
        }

        // 3. 外部索引写入与片段回写均以当前文档版本为边界
        List<DocumentChunkIndexResult> chunkResults = indexVersionChunks(documentId, documentVersionId, chunks,
                config, vectorEnabled, keywordEnabled);
        int indexedChunkCount = (int) chunkResults.stream().filter(DocumentChunkIndexResult::success).count();
        int failedChunkCount = (int) chunkResults.stream().filter(result -> !result.success()).count();
        if (failedChunkCount > 0) {
            throw new ServiceException("文档版本索引存在片段写入失败，documentId=" + documentId
                    + "，documentVersionId=" + documentVersionId, DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }

        // 4. 章节导航就绪后才将该版本原子发布为生效版本
        indexNavigation(documentId, documentVersionId, config);
        markVersionIndexReady(documentVersion);
        log.info("文档版本索引阶段执行完成，documentId={}，documentVersionId={}，indexedChunkCount={}，skippedChunkCount={}",
                documentId, documentVersionId, indexedChunkCount, skippedChunkCount);
        return new DocumentIndexResult(documentId, true, chunks.size() + skippedChunkCount, indexedChunkCount,
                skippedChunkCount, 0, vectorEnabled, keywordEnabled, null, chunkResults);
    }

    @Override
    public DocumentIndexResult rebuildDocumentVersionIndex(Long documentId, Long documentVersionId) {
        // 1. 仅允许回填已完成预热的历史版本，禁止启动或改变任何处理状态。
        documentService.getRequiredDocument(documentId);
        DocumentVersionDO documentVersion = documentVersionService.getRequiredVersion(documentId, documentVersionId);
        if (documentVersion.getStatus() != DocumentVersionStatus.INDEX_READY) {
            throw new ClientException("仅索引就绪版本允许回填索引元数据，documentId=" + documentId
                    + "，documentVersionId=" + documentVersionId, DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }

        // 2. 使用版本快照的配置重写双索引，替换语义仅限于目标版本。
        IndexConfigSnapshot config = indexConfigResolver.resolve(documentVersion);
        boolean vectorEnabled = config.enabled() && config.vectorEnabled();
        boolean keywordEnabled = config.enabled() && config.keywordEnabled();
        chunkIndexRepository.markSkipped(documentId, documentVersionId);
        DocumentVersionChunkIndexContext indexContext = chunkIndexRepository.loadIndexContext(documentId, documentVersionId);
        List<IndexableChunk> chunks = indexContext.indexableChunks();
        int skippedChunkCount = indexContext.skippedChunkCount();
        if (chunks.isEmpty()) {
            if (vectorEnabled) {
                documentVectorStore.replaceDocumentVersion(documentId, documentVersionId, List.of());
            }
            if (keywordEnabled) {
                keywordIndexClient.deleteByDocumentVersionId(documentId, documentVersionId, config.keywordIndexName());
            }
            indexNavigation(documentId, documentVersionId, config);
            return new DocumentIndexResult(documentId, true, skippedChunkCount, 0, skippedChunkCount, 0,
                    vectorEnabled, keywordEnabled, null, List.of());
        }
        List<DocumentChunkIndexResult> chunkResults = indexVersionChunks(documentId, documentVersionId, chunks,
                config, vectorEnabled, keywordEnabled);
        int failedChunkCount = (int) chunkResults.stream().filter(result -> !result.success()).count();
        if (failedChunkCount > 0) {
            throw new ServiceException("文档版本索引元数据回填失败，documentId=" + documentId
                    + "，documentVersionId=" + documentVersionId, DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
        indexNavigation(documentId, documentVersionId, config);
        int indexedChunkCount = (int) chunkResults.stream().filter(DocumentChunkIndexResult::success).count();
        log.info("文档版本索引元数据回填完成，documentId={}，documentVersionId={}，indexedChunkCount={}",
                documentId, documentVersionId, indexedChunkCount);
        return new DocumentIndexResult(documentId, true, chunks.size() + skippedChunkCount, indexedChunkCount,
                skippedChunkCount, 0, vectorEnabled, keywordEnabled, null, chunkResults);
    }

    private List<DocumentChunkIndexResult> markIndexedWithoutExternalIndex(List<IndexableChunk> chunks) {
        List<DocumentChunkIndexResult> results = new ArrayList<>();
        List<DocumentChunkIndexWriteBO> indexedChunks = new ArrayList<>();
        for (IndexableChunk chunk : chunks) {
            // 1. 索引禁用时仍推进片段状态，表示入库流水线已完成
            indexedChunks.add(new DocumentChunkIndexWriteBO(chunk.chunkId(), null, null));
            results.add(new DocumentChunkIndexResult(chunk.chunkId(), chunk.sectionId(), chunk.indexContent(),
                    true, false, null, null, null));
        }
        chunkIndexRepository.batchMarkIndexed(indexedChunks);
        return results;
    }

    private List<DocumentChunkIndexResult> indexVersionChunks(Long documentId, Long documentVersionId,
                                                              List<IndexableChunk> chunks, IndexConfigSnapshot config,
                                                              boolean vectorEnabled, boolean keywordEnabled) {
        if (!vectorEnabled && !keywordEnabled) {
            return markIndexedWithoutExternalIndex(chunks);
        }
        IndexWriteState vectorState = vectorEnabled
                ? writeVersionVectorIndex(documentId, documentVersionId, chunks) : IndexWriteState.empty();
        IndexWriteState keywordState = keywordEnabled
                ? writeVersionKeywordIndex(documentId, documentVersionId, chunks, config) : IndexWriteState.empty();
        List<DocumentChunkIndexResult> results = new ArrayList<>();
        List<DocumentChunkIndexWriteBO> indexedChunks = new ArrayList<>();
        for (IndexableChunk chunk : chunks) {
            String vectorId = vectorState.ids().get(chunk.chunkId());
            String keywordIndexId = keywordState.ids().get(chunk.chunkId());
            String failureReason = resolveFailureReason(chunk.chunkId(), vectorEnabled, keywordEnabled,
                    vectorState, keywordState);
            if (failureReason != null) {
                chunkIndexRepository.markFailed(chunk.chunkId(), failureReason);
                results.add(new DocumentChunkIndexResult(chunk.chunkId(), chunk.sectionId(), chunk.indexContent(),
                        false, false, vectorId, keywordIndexId, failureReason));
                continue;
            }
            indexedChunks.add(new DocumentChunkIndexWriteBO(chunk.chunkId(), vectorId, keywordIndexId));
            results.add(new DocumentChunkIndexResult(chunk.chunkId(), chunk.sectionId(), chunk.indexContent(),
                    true, false, vectorId, keywordIndexId, null));
        }
        chunkIndexRepository.batchMarkIndexed(indexedChunks);
        return results;
    }

    private IndexWriteState writeVersionVectorIndex(Long documentId, Long documentVersionId,
                                                    List<IndexableChunk> chunks) {
        return toIndexWriteState(documentVectorStore.replaceDocumentVersion(documentId, documentVersionId, chunks));
    }

    private IndexWriteState writeVersionKeywordIndex(Long documentId, Long documentVersionId,
                                                     List<IndexableChunk> chunks, IndexConfigSnapshot config) {
        List<KeywordIndexDocument> documents = chunks.stream().map(this::toKeywordIndexDocument).toList();
        return toKeywordIndexWriteState(keywordIndexClient.replaceDocumentVersion(new KeywordIndexWriteRequest(
                config.keywordIndexName(), documentId, documentVersionId, documents)));
    }

    private KeywordIndexDocument toKeywordIndexDocument(IndexableChunk chunk) {
        return new KeywordIndexDocument(chunk.chunkId(), chunk.documentId(), chunk.documentVersionId(), chunk.parentChunkId(), chunk.chunkOrder(),
                chunk.sectionId(), chunk.text(), chunk.indexContent(), chunk.metadataJson());
    }

    private IndexWriteState toIndexWriteState(List<VectorIndexWriteResult> results) {
        Map<String, String> ids = results.stream().filter(VectorIndexWriteResult::success)
                .collect(Collectors.toMap(VectorIndexWriteResult::chunkId, VectorIndexWriteResult::vectorId));
        Map<String, String> failures = results.stream().filter(result -> !result.success())
                .collect(Collectors.toMap(VectorIndexWriteResult::chunkId, VectorIndexWriteResult::failureReason));
        return new IndexWriteState(ids, failures);
    }

    private IndexWriteState toKeywordIndexWriteState(List<KeywordIndexWriteResult> results) {
        Map<String, String> ids = results.stream().filter(KeywordIndexWriteResult::success)
                .collect(Collectors.toMap(KeywordIndexWriteResult::chunkId, KeywordIndexWriteResult::keywordIndexId));
        Map<String, String> failures = results.stream().filter(result -> !result.success())
                .collect(Collectors.toMap(KeywordIndexWriteResult::chunkId, KeywordIndexWriteResult::failureReason));
        return new IndexWriteState(ids, failures);
    }

    private void validateDocumentVersionStatus(DocumentVersionDO documentVersion) {
        if (documentVersion.getStatus() != DocumentVersionStatus.CHUNKED
                && documentVersion.getStatus() != DocumentVersionStatus.INDEXING) {
            throw new ClientException("文档版本状态不允许执行索引，documentId=" + documentVersion.getDocumentId()
                    + "，documentVersionId=" + documentVersion.getDocumentVersionId()
                    + "，status=" + documentVersion.getStatus(), DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
    }

    private void markVersionIndexReady(DocumentVersionDO documentVersion) {
        if (!documentVersionService.markIndexReady(documentVersion.getDocumentId(), documentVersion.getDocumentVersionId(),
                documentVersion.getProcessId())) {
            throw new ClientException("文档版本索引完成状态更新失败，documentId=" + documentVersion.getDocumentId()
                    + "，documentVersionId=" + documentVersion.getDocumentVersionId(),
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
        documentVersion.setStatus(DocumentVersionStatus.INDEX_READY);
    }

    private void indexNavigation(Long documentId, Long documentVersionId, IndexConfigSnapshot config) {
        if (config.enabled() && config.keywordEnabled()) {
            sectionNavigationIndexRepository.upsert(documentId, documentVersionId);
        }
    }

    private String resolveFailureReason(String chunkId, boolean vectorEnabled, boolean keywordEnabled,
                                        IndexWriteState vectorState, IndexWriteState keywordState) {
        if (vectorEnabled && !vectorState.ids().containsKey(chunkId)) {
            return vectorState.failures().getOrDefault(chunkId, "向量索引未返回写入结果");
        }
        if (keywordEnabled && !keywordState.ids().containsKey(chunkId)) {
            return keywordState.failures().getOrDefault(chunkId, "关键词索引未返回写入结果");
        }
        return null;
    }

    private record IndexWriteState(Map<String, String> ids, Map<String, String> failures) {

        private static IndexWriteState empty() {
            return new IndexWriteState(new HashMap<>(), new HashMap<>());
        }
    }
}
