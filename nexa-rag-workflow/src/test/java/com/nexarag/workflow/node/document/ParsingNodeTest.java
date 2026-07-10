package com.nexarag.workflow.node.document;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.dto.ParseConfigRequest;
import com.nexarag.document.dto.ProcessDocumentRequest;
import com.nexarag.document.entity.Document;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.service.DocumentService;
import com.nexarag.infra.parser.model.DocumentParseRequest;
import com.nexarag.infra.parser.model.DocumentParseResult;
import com.nexarag.infra.parser.service.DocumentParseService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.CHUNKING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.DOCUMENT_ID;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.ROUTE_TARGET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 解析节点测试，验证解析编排、失败重试和幂等短路行为。
 */
class ParsingNodeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void applyShouldParseQueuedDocumentAndRouteToChunking() throws Exception {
        DocumentService documentService = mock(DocumentService.class);
        DocumentParseService parseService = mock(DocumentParseService.class);
        Document document = buildQueuedDocument();
        when(documentService.getRequiredDocument(1001L)).thenReturn(document);
        when(documentService.updateById(any(Document.class))).thenReturn(true);
        when(parseService.parse(any(DocumentParseRequest.class))).thenReturn(DocumentParseResult.builder()
                .contentType("text/markdown")
                .parsedObjectName("parsed/1001/demo.md")
                .parsedFileUrl("http://127.0.0.1/parsed/1001/demo.md")
                .build());

        ParsingNode node = new ParsingNode(documentService, parseService, objectMapper);
        Map<String, Object> result = node.apply(new OverAllState(Map.of(DOCUMENT_ID, 1001L)));

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PARSED);
        assertThat(document.getParsedObjectName()).isEqualTo("parsed/1001/demo.md");
        assertThat(document.getParsedFileUrl()).isEqualTo("http://127.0.0.1/parsed/1001/demo.md");
        assertThat(result).containsEntry(ROUTE_TARGET, CHUNKING_NODE);
        verify(parseService).parse(argThat(request -> Boolean.TRUE.equals(request.enableOcr())
                && Boolean.FALSE.equals(request.enableImageDescription())));
    }

    @Test
    void applyShouldThrowWhenParseFailureNeedsRetry() throws Exception {
        DocumentService documentService = mock(DocumentService.class);
        DocumentParseService parseService = mock(DocumentParseService.class);
        Document document = buildQueuedDocument();
        when(documentService.getRequiredDocument(1001L)).thenReturn(document);
        when(documentService.updateById(any(Document.class))).thenReturn(true);
        when(parseService.parse(any(DocumentParseRequest.class))).thenThrow(new IllegalStateException("解析失败"));
        when(documentService.recordProcessFailure(1001L, "PARSING", "文档解析失败", "解析失败"))
                .thenReturn(Document.builder().documentId(1001L).status(DocumentStatus.QUEUED).build());

        ParsingNode node = new ParsingNode(documentService, parseService, objectMapper);

        assertThatThrownBy(() -> node.apply(new OverAllState(Map.of(DOCUMENT_ID, 1001L))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("文档解析失败");
    }

    @Test
    void applyShouldEndWhenParseFailureExhausted() throws Exception {
        DocumentService documentService = mock(DocumentService.class);
        DocumentParseService parseService = mock(DocumentParseService.class);
        Document document = buildQueuedDocument();
        when(documentService.getRequiredDocument(1001L)).thenReturn(document);
        when(documentService.updateById(any(Document.class))).thenReturn(true);
        when(parseService.parse(any(DocumentParseRequest.class))).thenThrow(new IllegalStateException("解析失败"));
        when(documentService.recordProcessFailure(1001L, "PARSING", "文档解析失败", "解析失败"))
                .thenReturn(Document.builder().documentId(1001L).status(DocumentStatus.FAILED).build());

        ParsingNode node = new ParsingNode(documentService, parseService, objectMapper);
        Map<String, Object> result = node.apply(new OverAllState(Map.of(DOCUMENT_ID, 1001L)));

        assertThat(result).containsEntry(ROUTE_TARGET, END);
    }

    @Test
    void applyShouldSkipParseWhenDocumentAlreadyParsed() throws Exception {
        DocumentService documentService = mock(DocumentService.class);
        DocumentParseService parseService = mock(DocumentParseService.class);
        Document document = Document.builder()
                .documentId(1001L)
                .status(DocumentStatus.PARSED)
                .build();
        when(documentService.getRequiredDocument(1001L)).thenReturn(document);

        ParsingNode node = new ParsingNode(documentService, parseService, objectMapper);
        Map<String, Object> result = node.apply(new OverAllState(Map.of(DOCUMENT_ID, 1001L)));

        assertThat(result).containsEntry(ROUTE_TARGET, CHUNKING_NODE);
        verify(parseService, never()).parse(any(DocumentParseRequest.class));
    }

    private Document buildQueuedDocument() throws Exception {
        ProcessDocumentRequest processRequest = new ProcessDocumentRequest(null,
                new ParseConfigRequest(true, false), null);
        return Document.builder()
                .documentId(1001L)
                .status(DocumentStatus.QUEUED)
                .originalFileName("demo.docx")
                .fileType(FileType.WORD)
                .originalObjectName("original/1001/demo.docx")
                .originalFileUrl("http://127.0.0.1/demo.docx")
                .processConfigJson(objectMapper.writeValueAsString(processRequest))
                .build();
    }
}
