package com.nexarag.document.service.impl;

import com.nexarag.document.dto.SplitConfigRequest;
import com.nexarag.document.entity.Document;
import com.nexarag.document.entity.DocumentChunk;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.service.DocumentSplitContextBuilder;
import com.nexarag.document.splitter.ChunkDraft;
import com.nexarag.document.splitter.DocumentSplitContext;
import com.nexarag.document.splitter.DocumentSplitter;
import com.nexarag.document.splitter.DocumentSplitterFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
        DocumentSplitter splitter = mock(DocumentSplitter.class);
        Document document = Document.builder()
                .documentId(1L)
                .fileType(FileType.MARKDOWN)
                .status(DocumentStatus.PARSED)
                .build();
        SplitConfigRequest splitConfig = new SplitConfigRequest(SplitStrategy.PARENT_MARKDOWN, 1000, 100);
        DocumentSplitContext context = new DocumentSplitContext(1L, "测试", "demo.md", FileType.MARKDOWN,
                "original/demo.md", null, "parsed/demo.md", null, "text/markdown", "# title", null, splitConfig);
        List<ChunkDraft> drafts = List.of(new ChunkDraft("chunk_1", null, "# title", null, Map.of(), false));

        when(documentService.getRequiredDocument(1L)).thenReturn(document);
        when(documentService.updateById(any(Document.class))).thenReturn(true);
        when(contextBuilder.build(document)).thenReturn(context);
        when(splitterFactory.getRequired(SplitStrategy.PARENT_MARKDOWN)).thenReturn(splitter);
        when(splitter.split(context)).thenReturn(drafts);
        when(documentChunkService.replaceDocumentChunks(1L, drafts)).thenReturn(List.of(DocumentChunk.builder().build()));
        DocumentChunkingServiceImpl service = new DocumentChunkingServiceImpl(documentService, contextBuilder,
                splitterFactory, documentChunkService);

        int count = service.chunk(1L);

        assertThat(count).isEqualTo(1);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.CHUNKED);
        verify(documentChunkService).replaceDocumentChunks(1L, drafts);
    }
}
