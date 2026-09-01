package com.nexarag.retrieval.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.document.model.dto.IndexConfigRequest;
import com.nexarag.document.model.dto.ProcessDocumentRequest;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentChunk;
import com.nexarag.document.enums.ChunkStatus;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.service.DocumentVersionService;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.retrieval.model.*;
import com.nexarag.retrieval.config.IndexConfigResolver;
import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.dto.res.DocumentIndexResult;
import com.nexarag.retrieval.dto.req.KeywordIndexWriteRequest;
import com.nexarag.retrieval.index.keyword.KeywordIndexClient;
import com.nexarag.retrieval.index.vector.DocumentVectorStore;
import com.nexarag.retrieval.repository.ChunkIndexRepositoryImpl;
import com.nexarag.retrieval.repository.SectionNavigationIndexRepository;
import com.nexarag.retrieval.service.DocumentIndexService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档索引应用服务测试。
 */
class DocumentIndexServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void indexDocumentVersionShouldKeepHistoricalIndexesAndActivateOnlyAfterVersionIndexReady() {
        Fixture fixture = new Fixture(chunks());
        DocumentVersionDO version = DocumentVersionDO.builder()
                .documentId(1L)
                .documentVersionId(2L)
                .processId("process-1")
                .status(DocumentVersionStatus.CHUNKED)
                .build();
        fixture.chunks.forEach(chunk -> chunk.setDocumentVersionId(2L));
        when(fixture.documentVersionService.getRequiredVersion(1L, 2L)).thenReturn(version);
        when(fixture.documentVersionService.markIndexing(1L, 2L, "process-1")).thenReturn(true);
        when(fixture.documentVersionService.markIndexReady(1L, 2L, "process-1")).thenReturn(true);

        DocumentIndexResult result = fixture.service.indexDocument(1L, 2L);

        assertThat(result.success()).isTrue();
        assertThat(fixture.documentVectorStore.lastChunks).allMatch(chunk -> chunk.documentVersionId().equals(2L));
        verify(fixture.documentVersionService).markIndexReady(1L, 2L, "process-1");
    }

    @Test
    void rebuildDocumentVersionIndexShouldRewriteReadyVersionWithoutChangingItsStatus() {
        Fixture fixture = new Fixture(chunks());
        DocumentVersionDO version = DocumentVersionDO.builder()
                .documentId(1L)
                .documentVersionId(2L)
                .processId("process-1")
                .status(DocumentVersionStatus.INDEX_READY)
                .build();
        fixture.chunks.forEach(chunk -> chunk.setDocumentVersionId(2L));
        when(fixture.documentVersionService.getRequiredVersion(1L, 2L)).thenReturn(version);

        DocumentIndexResult result = fixture.service.rebuildDocumentVersionIndex(1L, 2L);

        assertThat(result.success()).isTrue();
        assertThat(fixture.documentVectorStore.lastChunks).allMatch(chunk -> chunk.documentVersionId().equals(2L));
        verify(fixture.documentVersionService, never()).markIndexing(1L, 2L, "process-1");
        verify(fixture.documentVersionService, never()).markIndexReady(1L, 2L, "process-1");
    }

    private List<DocumentChunk> chunks() {
        List<DocumentChunk> chunks = new ArrayList<>();
        chunks.add(DocumentChunk.builder()
                .chunkId("chunk-1")
                .documentId(1L)
                .chunkOrder(0)
                .sectionId(11L)
                .text("测试文本")
                .indexContent("标题路径 > 测试文本")
                .status(ChunkStatus.PENDING_INDEX)
                .skipIndex(0)
                .build());
        chunks.add(DocumentChunk.builder()
                .chunkId("chunk-parent")
                .documentId(1L)
                .chunkOrder(1)
                .text("父片段")
                .status(ChunkStatus.SKIP_INDEX)
                .skipIndex(1)
                .build());
        return chunks;
    }

    private class Fixture {

        private final Document document;
        private final List<DocumentChunk> chunks;
        private final DocumentService documentService;
        private final DocumentVersionService documentVersionService;
        private final DocumentIndexService service;
        private final StubDocumentVectorStore documentVectorStore;
        private final StubKeywordIndexClient keywordIndexClient;
        private final SectionNavigationIndexRepository navigationIndexRepository;

        private Fixture(List<DocumentChunk> chunks) {
            this(chunks, new StubDocumentVectorStore());
        }

        private Fixture(List<DocumentChunk> chunks, DocumentVectorStore documentVectorStore) {
            this.document = Document.builder()
                    .documentId(1L)
                    .build();
            this.chunks = chunks;
            this.documentService = mock(DocumentService.class);
            this.documentVersionService = mock(DocumentVersionService.class);
            DocumentChunkService documentChunkService = mock(DocumentChunkService.class);
            when(documentService.getRequiredDocument(1L)).thenReturn(document);
            when(documentChunkService.listByDocumentVersionId(2L)).thenReturn(chunks);
            doAnswer(invocation -> {
                markChunkIndexed(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2));
                return null;
            }).when(documentChunkService).markChunkIndexed(any(), any(), any());
            doAnswer(invocation -> {
                markChunkFailed(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }).when(documentChunkService).markChunkIndexFailed(any(), any());
            doAnswer(invocation -> {
                markSkipped();
                return null;
            }).when(documentChunkService).markDocumentVersionSkippedChunks(eq(2L));
            this.navigationIndexRepository = mock(SectionNavigationIndexRepository.class);
            this.keywordIndexClient = new StubKeywordIndexClient();
            RetrievalProperties retrievalProperties = new RetrievalProperties();
            retrievalProperties.getKeyword().setType("elasticsearch");
            this.documentVectorStore = documentVectorStore instanceof StubDocumentVectorStore stub ? stub : null;
            this.service = new DocumentIndexServiceImpl(documentService,
                    documentVersionService,
                    new ChunkIndexRepositoryImpl(documentChunkService),
                    new IndexConfigResolver(objectMapper, retrievalProperties),
                    documentVectorStore,
                    keywordIndexClient,
                    navigationIndexRepository);
        }

        private void markChunkIndexed(String chunkId, String vectorId, String keywordIndexId) {
            chunks.stream()
                    .filter(chunk -> chunk.getChunkId().equals(chunkId))
                    .findFirst()
                    .ifPresent(chunk -> {
                        chunk.setStatus(ChunkStatus.INDEXED);
                        chunk.setVectorId(vectorId);
                        chunk.setKeywordIndexId(keywordIndexId);
                        chunk.setFailureReason(null);
                    });
        }

        private void markChunkFailed(String chunkId, String failureReason) {
            chunks.stream()
                    .filter(chunk -> chunk.getChunkId().equals(chunkId))
                    .findFirst()
                    .ifPresent(chunk -> {
                        chunk.setStatus(ChunkStatus.FAILED);
                        chunk.setFailureReason(failureReason);
                    });
        }

        private void markSkipped() {
            chunks.stream()
                    .filter(chunk -> Integer.valueOf(1).equals(chunk.getSkipIndex()))
                    .forEach(chunk -> chunk.setStatus(ChunkStatus.SKIP_INDEX));
        }
    }

    /**
     * 测试用文档向量存储，返回稳定向量索引ID。
     */
    private static class StubDocumentVectorStore implements DocumentVectorStore {

        private List<IndexableChunk> lastChunks;

        @Override
        public List<VectorIndexWriteResult> replaceDocumentVersion(Long documentId, Long documentVersionId,
                                                                    List<IndexableChunk> chunks) {
            lastChunks = chunks;
            return chunks.stream()
                    .map(chunk -> new VectorIndexWriteResult(chunk.chunkId(), chunk.chunkId(), true, null))
                    .toList();
        }

        @Override
        public List<VectorIndexSearchResult> search(String query, int topK) {
            return List.of();
        }

        @Override
        public void deleteByDocumentVersionId(Long documentId, Long documentVersionId) {
        }
    }

    /**
     * 模拟向量存储异常，验证索引任务不会吞掉失败。
     */
    private static class FailingDocumentVectorStore implements DocumentVectorStore {

        @Override
        public List<VectorIndexWriteResult> replaceDocumentVersion(Long documentId, Long documentVersionId,
                                                                    List<IndexableChunk> chunks) {
            throw new IllegalStateException("模型服务暂时不可用");
        }

        @Override
        public List<VectorIndexSearchResult> search(String query, int topK) {
            return List.of();
        }

        @Override
        public void deleteByDocumentVersionId(Long documentId, Long documentVersionId) {
        }
    }

    /**
     * 测试用关键词索引客户端，返回稳定关键词索引ID。
     */
    private static class StubKeywordIndexClient implements KeywordIndexClient {

        private final List<String> operations = new ArrayList<>();

        @Override
        public List<KeywordIndexWriteResult> upsert(KeywordIndexWriteRequest request) {
            operations.add("upsert:" + request.documentId());
            return request.documents().stream()
                    .map(document -> new KeywordIndexWriteResult(document.chunkId(),
                            "mock-keyword-" + request.documentId() + "-" + document.chunkId(), true, null))
                    .toList();
        }

        @Override
        public int deleteByDocumentVersionId(Long documentId, Long documentVersionId, String indexName) {
            operations.add("delete:" + documentId + ":" + documentVersionId + ":" + indexName);
            return 0;
        }

        private List<String> operations() {
            return operations;
        }
    }
}
