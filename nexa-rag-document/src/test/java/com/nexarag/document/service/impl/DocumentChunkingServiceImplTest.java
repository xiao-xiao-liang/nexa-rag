package com.nexarag.document.service.impl;

import com.nexarag.document.model.dto.SplitConfigRequest;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.service.DocumentSplitContextBuilder;
import com.nexarag.document.model.bo.split.ChunkDraft;
import com.nexarag.document.model.bo.split.DocumentSplitContext;
import com.nexarag.document.model.bo.split.DocumentSplitResult;
import com.nexarag.document.model.bo.split.DocumentSectionDraft;
import com.nexarag.document.splitter.DocumentSplitter;
import com.nexarag.document.splitter.DocumentSplitterFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档切分阶段服务测试。
 */
class DocumentChunkingServiceImplTest {

    @Test
    void chunkShouldTransferParsedDocumentToChunkedAndSaveDrafts() {
        DocumentService documentService = mock(DocumentService.class);
        DocumentSplitContextBuilder contextBuilder = mock(DocumentSplitContextBuilder.class);
        DocumentSplitterFactory splitterFactory = mock(DocumentSplitterFactory.class);
        DocumentChunkService documentChunkService = mock(DocumentChunkService.class);
        DocumentChunkPersistenceService chunkPersistenceService = mock(DocumentChunkPersistenceService.class);
        DocumentSplitter splitter = mock(DocumentSplitter.class);
        Document document = Document.builder()
                .documentId(1L)
                .fileType(FileType.MARKDOWN)
                .status(DocumentStatus.PARSED)
                .build();
        SplitConfigRequest splitConfig = new SplitConfigRequest(SplitStrategy.PARENT_MARKDOWN, 1000, 100);
        DocumentSplitContext context = new DocumentSplitContext(1L, "测试", "demo.md", FileType.MARKDOWN,
                "original/demo.md", null, "parsed/demo.md", null, "text/markdown", "# title", null, splitConfig);
        List<ChunkDraft> drafts = List.of(new ChunkDraft("chunk_1", null, 11L, "# title", "标题 > # title", null,
                Map.of(), false));
        DocumentSplitResult splitResult = new DocumentSplitResult(List.of(
                new DocumentSectionDraft(11L, null, "标题", List.of("标题"), 1, 1, 2)), drafts, true);

        when(documentService.getRequiredDocument(1L)).thenReturn(document);
        when(documentService.markChunking(1L)).thenReturn(true);
        when(contextBuilder.build(document)).thenReturn(context);
        when(splitterFactory.getRequired(SplitStrategy.PARENT_MARKDOWN)).thenReturn(splitter);
        when(splitter.split(context)).thenReturn(splitResult);
        DocumentChunkingServiceImpl service = new DocumentChunkingServiceImpl(documentService, contextBuilder,
                splitterFactory, documentChunkService, chunkPersistenceService);

        int count = service.chunk(1L);

        assertThat(count).isEqualTo(1);
        verify(documentService).markChunking(1L);
        verify(chunkPersistenceService).replaceDocumentStructure(1L, splitResult);
    }
}
