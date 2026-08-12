package com.nexarag.infra.parser;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.constants.ParsedContentTypes;
import com.nexarag.infra.constants.ParserFileTypes;
import com.nexarag.infra.config.ArtifactProcessingProperties;
import com.nexarag.infra.parser.handler.DocumentArtifactHandler;
import com.nexarag.infra.parser.handler.DocumentArtifactHandlerRegistry;
import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.model.DocumentParseRequest;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.parser.model.StagedDocumentBO;
import com.nexarag.infra.parser.service.impl.DocumentParseServiceImpl;
import com.nexarag.infra.parser.workspace.ArtifactWorkspace;
import com.nexarag.infra.parser.workspace.ArtifactWorkspaceFactory;
import com.nexarag.infra.parser.workspace.BoundedFileTransfer;
import com.nexarag.infra.storage.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档解析服务测试，验证解析器按文件类型分派。
 */
class DocumentParseServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void parseShouldUseSupportedParser() {
        DocumentArtifactHandlerRegistry registry = mock(DocumentArtifactHandlerRegistry.class);
        DocumentArtifactHandler handler = mock(DocumentArtifactHandler.class);
        FileStorageService storageService = mock(FileStorageService.class);
        ArtifactWorkspaceFactory workspaceFactory = mock(ArtifactWorkspaceFactory.class);
        ArtifactWorkspace workspace = mock(ArtifactWorkspace.class);
        ParsedArtifact expectedArtifact = artifact();
        DocumentParseRequest request = request(ParserFileTypes.PDF);
        when(registry.requiredHandler(DocumentFormat.PDF)).thenReturn(handler);
        when(storageService.load(request.originalObjectName())).thenReturn(new ByteArrayInputStream("PDF".getBytes()));
        when(workspaceFactory.create(request.documentId())).thenReturn(workspace);
        when(workspace.resolve("source.pdf")).thenReturn(tempDir.resolve("source.pdf"));
        when(handler.handle(any(DocumentArtifactDTO.class), any())).thenReturn(expectedArtifact);
        DocumentParseServiceImpl service = new DocumentParseServiceImpl(registry, storageService, workspaceFactory,
                new BoundedFileTransfer(), artifactProperties());

        ParsedArtifact result = service.parse(request);

        verify(handler).handle(eq(DocumentArtifactDTO.builder().documentId(1L).originalFileName("demo.pdf")
                .format(DocumentFormat.PDF).originalObjectName("original/demo.pdf")
                .originalFileUrl("http://127.0.0.1:9000/nexa-rag/original/demo.pdf")
                .enableOcr(true).enableImageDescription(false).build()), any());
        assertThat(result.objectKey()).isEqualTo("parsed/1/content.md");
    }

    @Test
    void parseShouldFailWhenNoParserSupportsFileType() {
        DocumentArtifactHandlerRegistry registry = mock(DocumentArtifactHandlerRegistry.class);
        DocumentParseServiceImpl service = new DocumentParseServiceImpl(registry, mock(FileStorageService.class),
                mock(ArtifactWorkspaceFactory.class), new BoundedFileTransfer(), artifactProperties());

        assertThatThrownBy(() -> service.parse(request(ParserFileTypes.UNKNOWN)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不支持的文档格式");
    }

    @Test
    void parseStagedShouldLimitConcurrentParsingByConfiguredMaximum() throws Exception {
        DocumentArtifactHandlerRegistry registry = mock(DocumentArtifactHandlerRegistry.class);
        DocumentArtifactHandler handler = mock(DocumentArtifactHandler.class);
        ArtifactProcessingProperties properties = artifactProperties();
        properties.setMaxConcurrent(1);
        DocumentParseServiceImpl service = new DocumentParseServiceImpl(registry, mock(FileStorageService.class),
                mock(ArtifactWorkspaceFactory.class), new BoundedFileTransfer(), properties);
        CountDownLatch firstInvocation = new CountDownLatch(1);
        CountDownLatch secondInvocation = new CountDownLatch(1);
        CountDownLatch releaseFirstInvocation = new CountDownLatch(1);
        AtomicInteger invocationCount = new AtomicInteger();
        when(registry.requiredHandler(DocumentFormat.PDF)).thenReturn(handler);
        when(handler.handle(any(DocumentArtifactDTO.class), any())).thenAnswer(invocation -> {
            if (invocationCount.incrementAndGet() == 1) {
                firstInvocation.countDown();
                releaseFirstInvocation.await();
            } else {
                secondInvocation.countDown();
            }
            return artifact();
        });
        StagedDocumentBO stagedDocument = new StagedDocumentBO(tempDir.resolve("source.pdf"),
                mock(ArtifactWorkspace.class));
        DocumentArtifactDTO artifact = DocumentArtifactDTO.builder().documentId(1L).format(DocumentFormat.PDF)
                .originalFileName("demo.pdf").originalObjectName("original/demo.pdf").build();

        CompletableFuture<ParsedArtifact> first = CompletableFuture.supplyAsync(
                () -> service.parseStaged(artifact, stagedDocument));
        assertThat(firstInvocation.await(1, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<ParsedArtifact> second = CompletableFuture.supplyAsync(
                () -> service.parseStaged(artifact, stagedDocument));

        assertThat(secondInvocation.await(200, TimeUnit.MILLISECONDS)).isFalse();
        releaseFirstInvocation.countDown();
        assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo(artifact());
        assertThat(second.get(1, TimeUnit.SECONDS)).isEqualTo(artifact());
    }

    private DocumentParseRequest request(String fileType) {
        return DocumentParseRequest.builder()
                .documentId(1L)
                .originalFileName("demo.pdf")
                .fileType(fileType)
                .originalObjectName("original/demo.pdf")
                .originalFileUrl("http://127.0.0.1:9000/nexa-rag/original/demo.pdf")
                .enableOcr(true)
                .enableImageDescription(false)
                .build();
    }

    private ParsedArtifact artifact() {
        return ParsedArtifact.builder()
                .contentType(ParsedContentTypes.TEXT_MARKDOWN)
                .objectKey("parsed/1/content.md")
                .metadata(Map.of("parser", "test"))
                .build();
    }

    private ArtifactProcessingProperties artifactProperties() {
        ArtifactProcessingProperties properties = new ArtifactProcessingProperties();
        properties.setMaxWorkspaceBytes(1024L);
        return properties;
    }
}
