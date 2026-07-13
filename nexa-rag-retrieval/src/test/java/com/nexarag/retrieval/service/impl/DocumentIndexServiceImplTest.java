package com.nexarag.retrieval.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.document.dto.IndexConfigRequest;
import com.nexarag.document.dto.ProcessDocumentRequest;
import com.nexarag.document.entity.Document;
import com.nexarag.document.entity.DocumentChunk;
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
import com.nexarag.retrieval.dto.req.VectorIndexWriteRequest;
import com.nexarag.retrieval.index.keyword.KeywordIndexClient;
import com.nexarag.retrieval.index.vector.VectorIndexClient;
import com.nexarag.retrieval.repository.ChunkIndexRepositoryImpl;
import com.nexarag.retrieval.service.EmbeddingService;
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
        assertThat(fixture.chunks.getFirst().getVectorId()).isEqualTo("mock-vector-1-chunk-1");
        assertThat(fixture.chunks.getFirst().getKeywordIndexId()).isEqualTo("mock-keyword-1-chunk-1");
        assertThat(fixture.chunks.get(1).getStatus()).isEqualTo(ChunkStatus.SKIP_INDEX);
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
    void indexDocumentShouldReturnSuccessWhenDocumentAlreadyIndexed() {
        Fixture fixture = new Fixture(DocumentStatus.INDEXED, null, chunks());

        DocumentIndexResult result = fixture.service.indexDocument(1L);

        assertThat(result.success()).isTrue();
        assertThat(result.indexedChunkCount()).isEqualTo(0);
        verify(fixture.documentService, never()).updateById(any(Document.class));
    }

    @Test
    void indexDocumentShouldPropagateEmbeddingExceptionWithoutRequeueing() {
        Fixture fixture = new Fixture(DocumentStatus.CHUNKED, null, chunks(),
                (chunks, config) -> { throw new IllegalStateException("模型服务暂时不可用"); });

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
                .text("测试文本")
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

        private Fixture(DocumentStatus status, String processConfigJson, List<DocumentChunk> chunks) {
            this(status, processConfigJson, chunks, new StubEmbeddingService());
        }

        private Fixture(DocumentStatus status, String processConfigJson, List<DocumentChunk> chunks,
                        EmbeddingService embeddingService) {
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
            RetrievalProperties retrievalProperties = new RetrievalProperties();
            retrievalProperties.getKeyword().setType("elasticsearch");
            this.service = new DocumentIndexServiceImpl(documentService,
                    new ChunkIndexRepositoryImpl(documentChunkService),
                    new IndexConfigResolver(objectMapper, retrievalProperties),
                    embeddingService,
                    new StubVectorIndexClient(),
                    new StubKeywordIndexClient(),
                    cleaner);
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
     * 测试用 Embedding 服务，生成确定性向量，避免依赖真实模型服务。
     */
    private static class StubEmbeddingService implements EmbeddingService {

        @Override
        public List<ChunkEmbedding> embed(List<IndexableChunk> chunks,
                                          IndexConfigSnapshot config) {
            return chunks.stream()
                    .map(chunk -> new ChunkEmbedding(chunk.chunkId(), new float[]{1.0F, 0.0F}, "test", null))
                    .toList();
        }
    }

    /**
     * 测试用向量索引客户端，返回稳定向量索引ID。
     */
    private static class StubVectorIndexClient implements VectorIndexClient {

        @Override
        public List<VectorIndexWriteResult> upsert(VectorIndexWriteRequest request) {
            return request.documents().stream()
                    .map(document -> new VectorIndexWriteResult(document.chunkId(),
                            "mock-vector-" + request.documentId() + "-" + document.chunkId(), true, null))
                    .toList();
        }

        @Override
        public int deleteByDocumentId(Long documentId) {
            return 0;
        }
    }

    /**
     * 测试用关键词索引客户端，返回稳定关键词索引ID。
     */
    private static class StubKeywordIndexClient implements KeywordIndexClient {

        @Override
        public List<KeywordIndexWriteResult> upsert(KeywordIndexWriteRequest request) {
            return request.documents().stream()
                    .map(document -> new KeywordIndexWriteResult(document.chunkId(),
                            "mock-keyword-" + request.documentId() + "-" + document.chunkId(), true, null))
                    .toList();
        }

        @Override
        public int deleteByDocumentId(Long documentId) {
            return 0;
        }
    }
}
