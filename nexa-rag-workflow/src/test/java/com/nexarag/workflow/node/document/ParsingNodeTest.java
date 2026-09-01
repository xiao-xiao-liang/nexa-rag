package com.nexarag.workflow.node.document;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.document.model.dto.ParseConfigRequest;
import com.nexarag.document.model.dto.ProcessDocumentRequest;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.service.DocumentVersionService;
import com.nexarag.document.service.DocumentService;
import com.nexarag.infra.parser.model.DocumentParseRequest;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.parser.service.DocumentParseService;
import com.nexarag.infra.storage.service.FileStorageService;
import com.nexarag.infra.enums.ExternalDocumentSourceType;
import com.nexarag.infra.source.ExternalDocumentSourceService;
import com.nexarag.infra.source.model.SourceArtifactBO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.nexarag.workflow.constants.DocumentIngestionNodeConstants.CHUNKING_NODE;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.DOCUMENT_ID;
import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.DOCUMENT_VERSION_ID;
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
        DocumentVersionService documentVersionService = mock(DocumentVersionService.class);
        DocumentParseService parseService = mock(DocumentParseService.class);
        ExternalDocumentSourceService sourceService = mock(ExternalDocumentSourceService.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        DocumentVersionDO documentVersion = buildQueuedDocument();
        when(documentVersionService.getRequiredVersion(1001L, 2001L)).thenReturn(documentVersion);
        when(documentVersionService.updateById(any(DocumentVersionDO.class))).thenReturn(true);
        when(parseService.parse(any(DocumentParseRequest.class))).thenReturn(ParsedArtifact.builder()
                .contentType("text/markdown")
                .objectKey("parsed/1001/demo.md")
                .metadata(Map.of("structureArtifacts", List.of(Map.of(
                        "type", "MINERU_MIDDLE_JSON",
                        "objectKey", "parsed/1001/structure/mineru-middle.json",
                        "contentType", "application/json",
                        "size", 256L))))
                .build());
        when(fileStorageService.resolveUrl("parsed/1001/demo.md"))
                .thenReturn("http://127.0.0.1/parsed/1001/demo.md");

        ParsingNode node = new ParsingNode(documentService, documentVersionService, parseService, sourceService, fileStorageService, objectMapper);
        Map<String, Object> result = node.apply(new OverAllState(Map.of(DOCUMENT_ID, 1001L, DOCUMENT_VERSION_ID, 2001L)));

        assertThat(documentVersion.getStatus()).isEqualTo(DocumentVersionStatus.PARSED);
        assertThat(documentVersion.getParsedObjectName()).isEqualTo("parsed/1001/demo.md");
        assertThat(documentVersion.getParsedFileUrl()).isEqualTo("http://127.0.0.1/parsed/1001/demo.md");
        assertThat(objectMapper.readTree(documentVersion.getParsedMetadataJson())
                .at("/structureArtifacts/0/objectKey"))
                .hasToString("\"parsed/1001/structure/mineru-middle.json\"");
        assertThat(result).containsEntry(ROUTE_TARGET, CHUNKING_NODE);
        verify(parseService).parse(argThat(request -> Boolean.TRUE.equals(request.enableOcr())
                && Boolean.FALSE.equals(request.enableImageDescription())));
    }

    @Test
    void applyShouldPropagateParseFailureToMessageConsumer() throws Exception {
        DocumentService documentService = mock(DocumentService.class);
        DocumentVersionService documentVersionService = mock(DocumentVersionService.class);
        DocumentParseService parseService = mock(DocumentParseService.class);
        ExternalDocumentSourceService sourceService = mock(ExternalDocumentSourceService.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        DocumentVersionDO documentVersion = buildQueuedDocument();
        when(documentVersionService.getRequiredVersion(1001L, 2001L)).thenReturn(documentVersion);
        when(documentVersionService.updateById(any(DocumentVersionDO.class))).thenReturn(true);
        IllegalStateException failure = new IllegalStateException("解析失败");
        when(parseService.parse(any(DocumentParseRequest.class))).thenThrow(failure);

        ParsingNode node = new ParsingNode(documentService, documentVersionService, parseService, sourceService, fileStorageService, objectMapper);

        assertThatThrownBy(() -> node.apply(new OverAllState(Map.of(DOCUMENT_ID, 1001L, DOCUMENT_VERSION_ID, 2001L))))
                .isSameAs(failure);
    }

    @Test
    void applyShouldSkipParseWhenDocumentAlreadyParsed() throws Exception {
        DocumentService documentService = mock(DocumentService.class);
        DocumentVersionService documentVersionService = mock(DocumentVersionService.class);
        DocumentParseService parseService = mock(DocumentParseService.class);
        ExternalDocumentSourceService sourceService = mock(ExternalDocumentSourceService.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        DocumentVersionDO documentVersion = DocumentVersionDO.builder()
                .documentId(1001L)
                .documentVersionId(2001L)
                .status(DocumentVersionStatus.PARSED)
                .build();
        when(documentVersionService.getRequiredVersion(1001L, 2001L)).thenReturn(documentVersion);

        ParsingNode node = new ParsingNode(documentService, documentVersionService, parseService, sourceService, fileStorageService, objectMapper);
        Map<String, Object> result = node.apply(new OverAllState(Map.of(DOCUMENT_ID, 1001L, DOCUMENT_VERSION_ID, 2001L)));

        assertThat(result).containsEntry(ROUTE_TARGET, CHUNKING_NODE);
        verify(parseService, never()).parse(any(DocumentParseRequest.class));
    }

    @Test
    void applyShouldReadExternalSourceInsteadOfFileParser() throws Exception {
        DocumentService documentService = mock(DocumentService.class);
        DocumentVersionService documentVersionService = mock(DocumentVersionService.class);
        DocumentParseService parseService = mock(DocumentParseService.class);
        ExternalDocumentSourceService sourceService = mock(ExternalDocumentSourceService.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        DocumentVersionDO documentVersion = buildQueuedDocument();
        documentVersion.setSourceType(ExternalDocumentSourceType.YUQUE);
        documentVersion.setSourceUrl("https://www.yuque.com/a/b");
        when(documentVersionService.getRequiredVersion(1001L, 2001L)).thenReturn(documentVersion);
        when(documentVersionService.updateById(any(DocumentVersionDO.class))).thenReturn(true);
        Document document = Document.builder().documentId(1001L).title("外部文档").build();
        when(documentService.getRequiredDocument(1001L)).thenReturn(document);
        ParsedArtifact artifact = ParsedArtifact.builder().contentType("text/markdown")
                .objectKey("parsed/1001/content.md").build();
        when(sourceService.readAndPersist(any())).thenReturn(new SourceArtifactBO(artifact, "语雀标题", null, Map.of()));
        when(fileStorageService.resolveUrl(artifact.objectKey())).thenReturn("http://127.0.0.1/parsed/1001/content.md");

        ParsingNode node = new ParsingNode(documentService, documentVersionService, parseService, sourceService, fileStorageService, objectMapper);
        node.apply(new OverAllState(Map.of(DOCUMENT_ID, 1001L, DOCUMENT_VERSION_ID, 2001L)));

        verify(sourceService).readAndPersist(any());
        verify(parseService, never()).parse(any());
        assertThat(documentVersion.getStatus()).isEqualTo(DocumentVersionStatus.PARSED);
    }

    private DocumentVersionDO buildQueuedDocument() throws Exception {
        ProcessDocumentRequest processRequest = new ProcessDocumentRequest(null,
                new ParseConfigRequest(true, false), null);
        return DocumentVersionDO.builder()
                .documentId(1001L)
                .documentVersionId(2001L)
                .status(DocumentVersionStatus.QUEUED)
                .originalFileName("demo.docx")
                .fileType(FileType.WORD)
                .originalObjectName("original/1001/demo.docx")
                .originalFileUrl("http://127.0.0.1/demo.docx")
                .processConfigJson(objectMapper.writeValueAsString(processRequest))
                .build();
    }
}
