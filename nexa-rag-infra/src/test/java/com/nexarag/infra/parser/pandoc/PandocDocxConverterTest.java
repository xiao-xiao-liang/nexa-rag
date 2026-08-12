package com.nexarag.infra.parser.pandoc;

import com.nexarag.infra.config.ArtifactProcessingProperties;
import com.nexarag.infra.config.PandocProperties;
import com.nexarag.infra.messaging.document.DocumentPipelineNonRetryableException;
import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.workspace.ArtifactWorkspace;
import com.nexarag.infra.parser.workspace.ArtifactWorkspaceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Pandoc DOCX 转换器测试。 */
class PandocDocxConverterTest {

    @TempDir
    Path tempDir;

    @Test
    void convertShouldReturnMarkdownAndExtractedAssetsInsideWorkspace() throws Exception {
        ArtifactProcessingProperties artifactProperties = artifactProperties(1024L);
        PandocProcessRunner runner = mock(PandocProcessRunner.class);
        doAnswer(invocation -> {
            writePandocOutput(invocation.getArgument(0), invocation.getArgument(1), "# 标题", "image.png",
                    new byte[]{1, 2, 3});
            return null;
        }).when(runner).run(any(), any());
        PandocDocxConverter converter = new PandocDocxConverter(new PandocProperties(), runner, artifactProperties);

        try (ArtifactWorkspace workspace = new ArtifactWorkspaceFactory(artifactProperties).create(1L)) {
            Path source = workspace.resolve("source.docx");
            Files.writeString(source, "docx");

            var result = converter.convert(artifact(), source, workspace);

            assertThat(result.markdownPath()).isRegularFile();
            assertThat(Files.readString(result.markdownPath())).contains("# 标题");
            assertThat(result.assets()).singleElement().satisfies(asset -> {
                assertThat(asset.relativePath()).isEqualTo("assets/media/image.png");
                assertThat(asset.file()).isRegularFile();
            });
            verify(runner).run(argThat(command -> command.contains("--extract-media=assets")), eq(workspace.root()));
        }
    }

    @Test
    void convertShouldRejectOutputExceedingWorkspaceLimit() throws Exception {
        ArtifactProcessingProperties artifactProperties = artifactProperties(8L);
        PandocProcessRunner runner = mock(PandocProcessRunner.class);
        doAnswer(invocation -> {
            writePandocOutput(invocation.getArgument(0), invocation.getArgument(1), "超过上限的Markdown内容", null,
                    null);
            return null;
        }).when(runner).run(any(), any());
        PandocDocxConverter converter = new PandocDocxConverter(new PandocProperties(), runner, artifactProperties);

        try (ArtifactWorkspace workspace = new ArtifactWorkspaceFactory(artifactProperties).create(1L)) {
            Path source = workspace.resolve("source.docx");
            Files.writeString(source, "docx");

            assertThatThrownBy(() -> converter.convert(artifact(), source, workspace))
                    .isInstanceOf(DocumentPipelineNonRetryableException.class)
                    .hasMessageContaining("Markdown");
        }
    }

    private ArtifactProcessingProperties artifactProperties(long maxWorkspaceBytes) {
        ArtifactProcessingProperties properties = new ArtifactProcessingProperties();
        properties.setTempRoot(tempDir);
        properties.setMaxWorkspaceBytes(maxWorkspaceBytes);
        return properties;
    }

    private DocumentArtifactDTO artifact() {
        return DocumentArtifactDTO.builder()
                .documentId(1L)
                .format(DocumentFormat.WORD)
                .originalFileName("demo.docx")
                .originalObjectName("original/demo.docx")
                .build();
    }

    private void writePandocOutput(List<String> command, Path workDirectory, String markdown, String mediaName,
                                   byte[] mediaContent) throws Exception {
        Path markdownPath = Path.of(command.stream()
                .filter(argument -> argument.startsWith("--output="))
                .findFirst()
                .orElseThrow()
                .substring("--output=".length()));
        Files.writeString(markdownPath, markdown);
        if (mediaName != null) {
            Path assetsDirectory = Path.of(command.stream()
                    .filter(argument -> argument.startsWith("--extract-media="))
                    .findFirst()
                    .orElseThrow()
                    .substring("--extract-media=".length()));
            if (!assetsDirectory.isAbsolute()) {
                assetsDirectory = workDirectory.resolve(assetsDirectory);
            }
            Path mediaFile = assetsDirectory.resolve("media").resolve(mediaName);
            Files.createDirectories(mediaFile.getParent());
            Files.write(mediaFile, mediaContent);
        }
    }
}
