package com.nexarag.infra.parser.handler;

import com.nexarag.infra.parser.converter.DocumentConverter;
import com.nexarag.infra.parser.converter.DocumentConverterRegistry;
import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.model.ExtractedDocumentBO;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.parser.model.StagedDocumentBO;
import com.nexarag.infra.parser.publish.ArtifactPublisher;
import com.nexarag.infra.parser.workspace.ArtifactWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 转换型文档制品处理器测试。
 */
class ConvertingDocumentArtifactHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void handleShouldStageOriginalFileConvertAndPublishArtifact() {
        ArtifactWorkspace workspace = mock(ArtifactWorkspace.class);
        DocumentConverterRegistry converterRegistry = mock(DocumentConverterRegistry.class);
        DocumentConverter converter = mock(DocumentConverter.class);
        ArtifactPublisher publisher = mock(ArtifactPublisher.class);
        Path stagedSource = tempDir.resolve("source.docx");
        DocumentArtifactDTO artifactDTO = DocumentArtifactDTO.builder().documentId(1L)
                .originalFileName("示例.docx").originalObjectName("original/example.docx")
                .format(DocumentFormat.WORD).build();
        ExtractedDocumentBO extractedDocumentBO = mock(ExtractedDocumentBO.class);
        ParsedArtifact expectedArtifact = ParsedArtifact.builder().objectKey("parsed/1/content.md").build();
        when(converterRegistry.requiredConverter(DocumentFormat.WORD)).thenReturn(converter);
        when(converter.convert(artifactDTO, stagedSource, workspace)).thenReturn(extractedDocumentBO);
        when(publisher.publish(artifactDTO, extractedDocumentBO)).thenReturn(expectedArtifact);

        ConvertingDocumentArtifactHandler handler = new ConvertingDocumentArtifactHandler(converterRegistry, publisher);

        assertThat(handler.handle(artifactDTO, new StagedDocumentBO(stagedSource, workspace))).isSameAs(expectedArtifact);
        verify(converter).convert(artifactDTO, stagedSource, workspace);
        verify(publisher).publish(artifactDTO, extractedDocumentBO);
    }
}
