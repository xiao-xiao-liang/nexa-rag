package com.nexarag.infra.source;

import com.nexarag.infra.enums.ExternalDocumentSourceType;
import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.parser.model.StagedDocumentBO;
import com.nexarag.infra.parser.service.DocumentParseService;
import com.nexarag.infra.parser.workspace.ArtifactWorkspace;
import com.nexarag.infra.parser.workspace.ArtifactWorkspaceFactory;
import com.nexarag.infra.source.model.SourceArtifactBO;
import com.nexarag.infra.source.model.SourceReadRequestDTO;
import com.nexarag.infra.source.model.SourceReadResultBO;
import com.nexarag.infra.storage.ObjectNameResolver;
import com.nexarag.infra.storage.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * 外部来源读取服务测试。
 */
class ExternalDocumentSourceServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void readAndPersistShouldStreamSourceFileAndReuseItForParsing() throws Exception {
        ExternalDocumentSourceReader reader = mock(ExternalDocumentSourceReader.class);
        FileStorageService storageService = mock(FileStorageService.class);
        ObjectNameResolver objectNameResolver = mock(ObjectNameResolver.class);
        DocumentParseService documentParseService = mock(DocumentParseService.class);
        ArtifactWorkspaceFactory workspaceFactory = mock(ArtifactWorkspaceFactory.class);
        ArtifactWorkspace workspace = mock(ArtifactWorkspace.class);
        SourceReadRequestDTO request = new SourceReadRequestDTO(1001L, ExternalDocumentSourceType.FEISHU,
                "https://tenant.feishu.cn/docx/abc");
        Path sourcePath = tempDir.resolve("source.docx");
        Files.writeString(sourcePath, "DOCX");
        when(reader.supports(ExternalDocumentSourceType.FEISHU)).thenReturn(true);
        when(workspaceFactory.create(1001L)).thenReturn(workspace);
        when(reader.read(request, workspace)).thenReturn(new SourceReadResultBO(sourcePath,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", DocumentFormat.WORD,
                "source.docx", "标题", "abc", "rev-1", Map.<String, Object>of("platform", "feishu")));
        when(objectNameResolver.resolveSourceSnapshotObjectName(1001L, ".docx"))
                .thenReturn("source-snapshots/1001/source.docx");
        ParsedArtifact parsedArtifact = ParsedArtifact.builder().objectKey("parsed/1001/content.md")
                .contentType("text/markdown").metadata(Map.of("parser", "pandoc")).build();
        when(documentParseService.parseStaged(any(), any())).thenReturn(parsedArtifact);

        ExternalDocumentSourceService service = new ExternalDocumentSourceServiceImpl(
                List.of(reader), storageService, objectNameResolver, workspaceFactory, documentParseService);
        SourceArtifactBO artifact = service.readAndPersist(request);

        assertThat(artifact.parsedArtifact()).isSameAs(parsedArtifact);
        assertThat(artifact.sourceSnapshotObjectName()).isEqualTo("source-snapshots/1001/source.docx");
        verify(storageService).saveAs(eq("source-snapshots/1001/source.docx"), any(), anyLong(),
                eq("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        verify(documentParseService).parseStaged(eq(DocumentArtifactDTO.builder().documentId(1001L)
                .originalFileName("source.docx").format(DocumentFormat.WORD)
                .originalObjectName("source-snapshots/1001/source.docx").build()),
                eq(new StagedDocumentBO(sourcePath, workspace)));
        verify(workspace).close();
    }
}
