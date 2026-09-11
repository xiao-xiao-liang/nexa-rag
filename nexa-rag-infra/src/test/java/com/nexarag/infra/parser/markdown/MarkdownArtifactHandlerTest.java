package com.nexarag.infra.parser.markdown;

import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.parser.model.StagedDocumentBO;
import com.nexarag.infra.parser.publish.ArtifactPublisher;
import com.nexarag.infra.parser.workspace.ArtifactWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Markdown 资源制品处理器测试。 */
class MarkdownArtifactHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void handleShouldCollectWorkspaceAssetsAndPublishMarkdown() throws Exception {
        ArtifactPublisher publisher = mock(ArtifactPublisher.class);
        MarkdownArtifactHandler handler = new MarkdownArtifactHandler(publisher);
        Path source = tempDir.resolve("source.md");
        Path asset = tempDir.resolve("assets/image.png");
        Files.createDirectories(asset.getParent());
        Files.writeString(source, "![图片](assets/image.png)");
        Files.write(asset, new byte[]{1});
        ParsedArtifact expected = ParsedArtifact.builder().objectKey("parsed/1/content.md").build();
        when(publisher.publish(any(), any())).thenReturn(expected);

        ParsedArtifact actual = handler.handle(DocumentArtifactDTO.builder().documentId(1L)
                        .format(DocumentFormat.MARKDOWN).originalFileName("source.md").build(),
                new StagedDocumentBO(source, mock(ArtifactWorkspace.class)));

        assertThat(handler.supportedFormats()).isEqualTo(Set.of(DocumentFormat.MARKDOWN));
        assertThat(actual).isSameAs(expected);
        org.mockito.Mockito.verify(publisher).publish(any(), org.mockito.ArgumentMatchers.argThat(document ->
                document.assets().size() == 1 && document.assets().getFirst().relativePath().equals("assets/image.png")));
    }
}
