package com.nexarag.retrieval.service.impl;

import com.nexarag.common.exception.ClientException;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.DocumentErrorCode;
import com.nexarag.document.service.DocumentService;
import com.nexarag.retrieval.service.DocumentIndexCleaner;
import com.nexarag.retrieval.config.IndexConfigResolver;
import com.nexarag.retrieval.model.IndexConfigSnapshot;
import com.nexarag.retrieval.dto.res.DocumentChunkIndexResult;
import com.nexarag.retrieval.dto.res.DocumentIndexCleanupResult;
import com.nexarag.retrieval.dto.res.DocumentIndexResult;
import com.nexarag.retrieval.index.keyword.KeywordIndexClient;
import com.nexarag.retrieval.model.KeywordIndexDocument;
import com.nexarag.retrieval.dto.req.KeywordIndexWriteRequest;
import com.nexarag.retrieval.model.KeywordIndexWriteResult;
import com.nexarag.retrieval.index.vector.DocumentVectorStore;
import com.nexarag.retrieval.model.VectorIndexWriteResult;
import com.nexarag.retrieval.model.IndexableChunk;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文档索引服务实现，编排文档状态、片段查询、向量化、索引写入和结果回写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIndexServiceImpl implements DocumentIndexService {

    private final DocumentService documentService;
    private final ChunkIndexRepository chunkIndexRepository;
    private final IndexConfigResolver indexConfigResolver;
    private final DocumentVectorStore documentVectorStore;
    private final KeywordIndexClient keywordIndexClient;
    private final DocumentIndexCleaner documentIndexCleaner;
    private final SectionNavigationIndexRepository sectionNavigationIndexRepository;

    /**
     * 执行指定文档的索引写入。
     *
     * @param documentId 文档ID
     * @return 文档索引结果
     */
    @Override
    public DocumentIndexResult indexDocument(Long documentId) {
        // 1. 查询并校验文档状态
        Document document = documentService.getRequiredDocument(documentId);
        if (document.getStatus() == DocumentStatus.INDEXED) {
            return new DocumentIndexResult(documentId, true, 0, 0, 0, 0,
                    false, false, null, List.of());
        }
        validateDocumentStatus(document);

        // 2. 推进文档进入索引中状态
        if (document.getStatus() == DocumentStatus.CHUNKED &&
                !documentService.markIndexing(documentId, document.getProcessId())) {
            throw new ClientException("文档索引状态已变化，documentId=" + documentId,
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
        document.setStatus(DocumentStatus.INDEXING);
        IndexConfigSnapshot config = indexConfigResolver.resolve(document);
        boolean vectorEnabled = config.enabled() && config.vectorEnabled();
        boolean keywordEnabled = config.enabled() && config.keywordEnabled();

        // 3. 标记跳过索引片段并读取可索引片段
        chunkIndexRepository.markSkipped(documentId);
        List<IndexableChunk> chunks = chunkIndexRepository.listIndexableChunks(documentId);
        int skippedChunkCount = chunkIndexRepository.listSkippedChunks(documentId).size();
        if (chunks.isEmpty()) {
            if (vectorEnabled) {
                // 正文全部变为不可索引时也要清除旧向量，避免重处理后召回已失效内容
                documentVectorStore.replaceDocument(documentId, List.of());
            }
            if (keywordEnabled) {
                // 正文全部变为不可索引时同步清除旧关键词记录，保持双索引替换语义一致
                keywordIndexClient.deleteByDocumentId(documentId, config.keywordIndexName());
            }
            indexNavigation(documentId, config);
            markIndexed(document);
            return new DocumentIndexResult(documentId, true, skippedChunkCount, 0, skippedChunkCount, 0,
                    vectorEnabled, keywordEnabled, null, List.of());
        }

        // 4. 按配置执行索引写入并回写片段状态
        List<DocumentChunkIndexResult> chunkResults = indexChunks(documentId, chunks, config, vectorEnabled, keywordEnabled);
        int indexedChunkCount = (int) chunkResults.stream().filter(DocumentChunkIndexResult::success).count();
        int failedChunkCount = (int) chunkResults.stream().filter(result -> !result.success()).count();
        if (failedChunkCount > 0) {
            throw new ServiceException("文档索引存在片段写入失败，documentId=" + documentId,
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }

        // 5. 全部成功后推进文档到索引完成状态
        indexNavigation(documentId, config);
        markIndexed(document);
        log.info("文档索引阶段执行完成，documentId={}，indexedChunkCount={}，skippedChunkCount={}",
                documentId, indexedChunkCount, skippedChunkCount);
        return new DocumentIndexResult(documentId, true, chunks.size() + skippedChunkCount, indexedChunkCount,
                skippedChunkCount, 0, vectorEnabled, keywordEnabled, null, chunkResults);
    }

    /**
     * 清理指定文档的外部索引。
     *
     * @param documentId 文档ID
     * @return 文档索引清理结果
     */
    @Override
    public DocumentIndexCleanupResult cleanupDocumentIndex(Long documentId) {
        // 1. 委托清理器执行实际清理顺序
        return documentIndexCleaner.cleanup(documentId);
    }

    private List<DocumentChunkIndexResult> indexChunks(Long documentId, List<IndexableChunk> chunks,
                                                       IndexConfigSnapshot config,
                                                       boolean vectorEnabled,
                                                       boolean keywordEnabled) {
        if (!vectorEnabled && !keywordEnabled) {
            return markIndexedWithoutExternalIndex(chunks);
        }

        IndexWriteState vectorState = vectorEnabled ? writeVectorIndex(documentId, chunks, config) : IndexWriteState.empty();
        IndexWriteState keywordState = keywordEnabled ? writeKeywordIndex(documentId, chunks, config) : IndexWriteState.empty();
        List<DocumentChunkIndexResult> results = new ArrayList<>();
        for (IndexableChunk chunk : chunks) {
            // 1. 校验单个片段在已开启索引阶段中的写入结果，任一阶段失败都不能标记为成功
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

            // 2. 已开启的索引阶段全部成功后，回写索引ID
            chunkIndexRepository.markIndexed(chunk.chunkId(), vectorId, keywordIndexId);
            results.add(new DocumentChunkIndexResult(chunk.chunkId(), chunk.sectionId(), chunk.indexContent(),
                    true, false, vectorId, keywordIndexId, null));
        }
        return results;
    }

    private List<DocumentChunkIndexResult> markIndexedWithoutExternalIndex(List<IndexableChunk> chunks) {
        List<DocumentChunkIndexResult> results = new ArrayList<>();
        for (IndexableChunk chunk : chunks) {
            // 1. 索引禁用时仍推进片段状态，表示入库流水线已完成
            chunkIndexRepository.markIndexed(chunk.chunkId(), null, null);
            results.add(new DocumentChunkIndexResult(chunk.chunkId(), chunk.sectionId(), chunk.indexContent(),
                    true, false, null, null, null));
        }
        return results;
    }

    private IndexWriteState writeVectorIndex(Long documentId, List<IndexableChunk> chunks, IndexConfigSnapshot config) {
        List<VectorIndexWriteResult> results = documentVectorStore.replaceDocument(documentId, chunks);
        Map<String, String> ids = results.stream()
                .filter(VectorIndexWriteResult::success)
                .collect(Collectors.toMap(VectorIndexWriteResult::chunkId, VectorIndexWriteResult::vectorId));
        Map<String, String> failures = results.stream()
                .filter(result -> !result.success())
                .collect(Collectors.toMap(VectorIndexWriteResult::chunkId, VectorIndexWriteResult::failureReason));
        return new IndexWriteState(ids, failures);
    }

    private IndexWriteState writeKeywordIndex(Long documentId, List<IndexableChunk> chunks, IndexConfigSnapshot config) {
        List<KeywordIndexDocument> documents = chunks.stream()
                .map(this::toKeywordIndexDocument)
                .toList();
        List<KeywordIndexWriteResult> results = keywordIndexClient.replaceDocument(
                new KeywordIndexWriteRequest(config.keywordIndexName(), documentId, documents));
        Map<String, String> ids = results.stream()
                .filter(KeywordIndexWriteResult::success)
                .collect(Collectors.toMap(KeywordIndexWriteResult::chunkId, KeywordIndexWriteResult::keywordIndexId));
        Map<String, String> failures = results.stream()
                .filter(result -> !result.success())
                .collect(Collectors.toMap(KeywordIndexWriteResult::chunkId, KeywordIndexWriteResult::failureReason));
        return new IndexWriteState(ids, failures);
    }

    private KeywordIndexDocument toKeywordIndexDocument(IndexableChunk chunk) {
        return new KeywordIndexDocument(chunk.chunkId(), chunk.documentId(), chunk.parentChunkId(), chunk.chunkOrder(),
                chunk.sectionId(), chunk.text(), chunk.indexContent(), chunk.metadataJson());
    }

    private void validateDocumentStatus(Document document) {
        if (document.getStatus() != DocumentStatus.CHUNKED && document.getStatus() != DocumentStatus.INDEXING) {
            throw new ClientException("文档状态不允许执行索引，documentId=" + document.getDocumentId()
                    + "，status=" + document.getStatus(), DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
    }

    private void markIndexed(Document document) {
        if (!documentService.markIndexed(document.getDocumentId(), document.getProcessId())) {
            throw new ClientException("文档索引完成状态更新失败，documentId=" + document.getDocumentId(),
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
        document.setStatus(DocumentStatus.INDEXED);
    }

    private void indexNavigation(Long documentId, IndexConfigSnapshot config) {
        if (config.enabled() && config.keywordEnabled()) {
            // 1. 正文片段全部成功后再写入章节导航，异常由既有索引重试链路处理
            sectionNavigationIndexRepository.upsert(documentId);
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
