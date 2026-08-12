package com.nexarag.infra.parser.mineru;

import com.nexarag.infra.config.ArtifactProcessingProperties;
import com.nexarag.infra.parser.mineru.client.MinerUClient;
import com.nexarag.infra.parser.mineru.extract.MinerUZipFileExtractor;
import com.nexarag.infra.parser.mineru.ratelimit.MinerUParseLimiter;
import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.model.ExtractedDocumentBO;
import com.nexarag.infra.parser.model.MinerUParseCommand;
import com.nexarag.infra.parser.model.MinerUParseResponse;
import com.nexarag.infra.parser.workspace.ArtifactWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MinerU PDF 转换器测试。
 */
class MinerUPdfConverterTest {

    @TempDir
    Path tempDir;

    @Test
    void convertShouldSendStagedPdfToMinerUAndReturnFileBackedResult() throws Exception {
        MinerUClient client = mock(MinerUClient.class);
        MinerUZipFileExtractor extractor = mock(MinerUZipFileExtractor.class);
        ArtifactWorkspace workspace = mock(ArtifactWorkspace.class);
        MinerUParseLimiter limiter = immediateLimiter();
        Path source = tempDir.resolve("source.pdf");
        Files.writeString(source, "PDF");
        ExtractedDocumentBO expected = mock(ExtractedDocumentBO.class);
        when(client.parse(any(MinerUParseCommand.class))).thenReturn(MinerUParseResponse.builder()
                .zipInputStream(new ByteArrayInputStream("zip".getBytes())).metadata(Map.of("client", "stub")).build());
        when(extractor.extract(any(), eq(workspace), eq(1024L))).thenReturn(expected);
        ArtifactProcessingProperties properties = new ArtifactProcessingProperties();
        properties.setMaxWorkspaceBytes(1024L);
        MinerUPdfConverter converter = new MinerUPdfConverter(client, extractor, limiter, properties);
        DocumentArtifactDTO artifactDTO = DocumentArtifactDTO.builder().documentId(1L).originalFileName("demo.pdf")
                .format(DocumentFormat.PDF).enableOcr(true).build();

        assertThat(converter.convert(artifactDTO, source, workspace)).isSameAs(expected);
        verify(client).parse(any(MinerUParseCommand.class));
        verify(extractor).extract(any(), eq(workspace), eq(1024L));
    }

    private MinerUParseLimiter immediateLimiter() {
        return new MinerUParseLimiter() {
            @Override
            public <T> T execute(Long documentId, Supplier<T> action) {
                return action.get();
            }
        };
    }
}
