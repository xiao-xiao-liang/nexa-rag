package com.nexarag.retrieval.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.document.model.dto.IndexConfigRequest;
import com.nexarag.document.model.dto.ProcessDocumentRequest;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentChunk;
import com.nexarag.document.enums.ChunkStatus;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.document.service.DocumentService;
import com.nexarag.retrieval.model.*;
import com.nexarag.retrieval.service.DocumentIndexCleaner;
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
    void indexDocumentShouldWriteMockIndexesAndMarkDocumentIndexed() {
        Fixture fixture = new Fixture(DocumentStatus.CHUNKED, null, chunks());

        DocumentIndexResult result = fixture.service.indexDocument(1L);

        assertThat(result.success()).isTrue();
        assertThat(result.indexedChunkCount()).isEqualTo(1);
        assertThat(result.skippedChunkCount()).isEqualTo(1);
        assertThat(fixture.document.getStatus()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(fixture.chunks.getFirst().getStatus()).isEqualTo(ChunkStatus.INDEXED);
        assertThat(fixture.chunks.getFirst().getVectorId()).isEqualTo("chunk-1");
        assertThat(fixture.chunks.getFirst().getKeywordIndexId()).isEqualTo("mock-keyword-1-chunk-1");
        assertThat(fixture.chunks.get(1).getStatus()).isEqualTo(ChunkStatus.SKIP_INDEX);
        assertThat(result.chunks().getFirst().sectionId()).isEqualTo(11L);
        assertThat(result.chunks().getFirst().indexContent()).isEqualTo("标题路径 > 测试文本");
        assertThat(fixture.documentVectorStore.lastChunks)
                .extracting(IndexableChunk::chunkId)
                .containsExactly("chunk-1");
        assertThat(fixture.documentVectorStore.lastChunks.getFirst().text()).isEqualTo("测试文本");
        assertThat(fixture.documentVectorStore.lastChunks.getFirst().indexContent())
                .isEqualTo("标题路径 > 测试文本");
        verify(fixture.navigationIndexRepository).upsert(1L);
    }

    @Test
    void indexDocumentShouldSkipExternalIndexWhenIndexDisabled() throws Exception {
        ProcessDocumentRequest request = new ProcessDocumentRequest(null, null,
                new IndexConfigRequest(false, true, true));
        Fixture fixture = new Fixture(DocumentStatus.CHUNKED, objectMapper.writeValueAsString(request), chunks());

        DocumentIndexResult result = fixture.service.indexDocument(1L);

        assertThat(result.success()).isTrue();
        assertThat(result.vectorEnabled()).isFalse();
        assertThat(result.keywordEnabled()).isFalse();
        assertThat(fixture.chunks.getFirst().getStatus()).isEqualTo(ChunkStatus.INDEXED);
        assertThat(fixture.chunks.getFirst().getVectorId()).isNull();
        assertThat(fixture.chunks.getFirst().getKeywordIndexId()).isNull();
    }

    @Test
    void indexDocumentShouldClearVectorsAndWriteNavigationForTitleOnlyDocument() {
        Fixture fixture = new Fixture(DocumentStatus.CHUNKED, null, List.of());

        DocumentIndexResult result = fixture.service.indexDocument(1L);

        assertThat(result.indexedChunkCount()).isZero();
        assertThat(fixture.documentVectorStore.lastChunks).isEmpty();
        assertThat(fixture.keywordIndexClient.operations())
                .containsExactly("delete:1:nexa_document_chunk");
        verify(fixture.navigationIndexRepository).upsert(1L);
    }

    @Test
    void indexDocumentShouldReplaceKeywordIndexBeforeWritingNewChunks() {
        Fixture fixture = new Fixture(DocumentStatus.CHUNKED, null, chunks());

        fixture.service.indexDocument(1L);

        assertThat(fixture.keywordIndexClient.operations())
                .containsExactly("delete:1:nexa_document_chunk", "upsert:1");
    }

    @Test
    void indexDocumentShouldNotWriteNavigationWhenKeywordIndexDisabled() throws Exception {
        ProcessDocumentRequest request = new ProcessDocumentRequest(null, null,
                new IndexConfigRequest(true, true, false));
        Fixture fixture = new Fixture(DocumentStatus.CHUNKED, objectMapper.writeValueAsString(request), chunks());

        fixture.service.indexDocument(1L);

        verify(fixture.navigationIndexRepository, never()).upsert(1L);
    }

    @Test
    void indexDocumentShouldReturnSuccessWhenDocumentAlreadyIndexed() {
        Fixture fixture = new Fixture(DocumentStatus.INDEXED, null, chunks());

        DocumentIndexResult result = fixture.service.indexDocument(1L);

        assertThat(result.success()).isTrue();
        assertThat(result.indexedChunkCount()).isEqualTo(0);
        verify(fixture.documentService, never()).updateById(any(Document.class));
    }

    @Test
    void indexDocumentShouldPropagateVectorStoreExceptionWithoutRequeueing() {
        Fixture fixture = new Fixture(DocumentStatus.CHUNKED, null, chunks(), new FailingDocumentVectorStore());

        assertThatThrownBy(() -> fixture.service.indexDocument(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("模型服务暂时不可用");
        verify(fixture.documentService, never()).recordProcessFailure(any(), any(), any(), any());
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
        private final DocumentIndexService service;
        private final StubDocumentVectorStore documentVectorStore;
        private final StubKeywordIndexClient keywordIndexClient;
        private final SectionNavigationIndexRepository navigationIndexRepository;

        private Fixture(DocumentStatus status, String processConfigJson, List<DocumentChunk> chunks) {
            this(status, processConfigJson, chunks, new StubDocumentVectorStore());
        }

        private Fixture(DocumentStatus status, String processConfigJson, List<DocumentChunk> chunks,
                        DocumentVectorStore documentVectorStore) {
            this.document = Document.builder()
                    .documentId(1L)
                    .processId("process-1")
                    .status(status)
                    .processConfigJson(processConfigJson)
                    .retryCount(0)
                    .maxRetryCount(3)
                    .build();
            this.chunks = chunks;
            this.documentService = mock(DocumentService.class);
            DocumentChunkService documentChunkService = mock(DocumentChunkService.class);
            when(documentService.getRequiredDocument(1L)).thenReturn(document);
            when(documentService.updateById(any(Document.class))).thenReturn(true);
            when(documentService.markIndexing(1L, "process-1")).thenReturn(true);
            when(documentService.markIndexed(1L, "process-1")).thenReturn(true);
            when(documentChunkService.listByDocumentId(1L)).thenReturn(chunks);
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
            }).when(documentChunkService).markDocumentSkippedChunks(eq(1L));
            DocumentIndexCleaner cleaner = mock(DocumentIndexCleaner.class);
            this.navigationIndexRepository = mock(SectionNavigationIndexRepository.class);
            this.keywordIndexClient = new StubKeywordIndexClient();
            RetrievalProperties retrievalProperties = new RetrievalProperties();
            retrievalProperties.getKeyword().setType("elasticsearch");
            this.documentVectorStore = documentVectorStore instanceof StubDocumentVectorStore stub ? stub : null;
            this.service = new DocumentIndexServiceImpl(documentService,
                    new ChunkIndexRepositoryImpl(documentChunkService),
                    new IndexConfigResolver(objectMapper, retrievalProperties),
                    documentVectorStore,
                    keywordIndexClient,
                    cleaner,
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
        public List<VectorIndexWriteResult> replaceDocument(Long documentId, List<IndexableChunk> chunks) {
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
        public void deleteByDocumentId(Long documentId) {
        }
    }

    /**
     * 模拟向量存储异常，验证索引任务不会吞掉失败。
     */
    private static class FailingDocumentVectorStore implements DocumentVectorStore {

        @Override
        public List<VectorIndexWriteResult> replaceDocument(Long documentId, List<IndexableChunk> chunks) {
            throw new IllegalStateException("模型服务暂时不可用");
        }

        @Override
        public List<VectorIndexSearchResult> search(String query, int topK) {
            return List.of();
        }

        @Override
        public void deleteByDocumentId(Long documentId) {
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
        public int deleteByDocumentId(Long documentId) {
            operations.add("delete:" + documentId + ":default");
            return 0;
        }

        @Override
        public int deleteByDocumentId(Long documentId, String indexName) {
            operations.add("delete:" + documentId + ":" + indexName);
            return 0;
        }

        private List<String> operations() {
            return operations;
        }
    }
}
