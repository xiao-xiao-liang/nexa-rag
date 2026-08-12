package com.nexarag.infra.parser.mineru.extract;

import com.nexarag.infra.parser.model.ExtractedDocumentBO;
import com.nexarag.infra.parser.workspace.ArtifactWorkspace;
import com.nexarag.infra.parser.workspace.BoundedFileTransfer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MinerU ZIP 文件化提取器测试。
 */
class MinerUZipFileExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void extractShouldWriteMarkdownAndImageToWorkspaceFiles() throws Exception {
        ArtifactWorkspace workspace = mock(ArtifactWorkspace.class);
        when(workspace.resolve("mineru/result/content.md")).thenReturn(tempDir.resolve("mineru/result/content.md"));
        when(workspace.resolve("assets/images/a.png")).thenReturn(tempDir.resolve("assets/images/a.png"));
        MinerUZipFileExtractor extractor = new MinerUZipFileExtractor(new BoundedFileTransfer());

        ExtractedDocumentBO result = extractor.extract(new ByteArrayInputStream(zip(Map.of(
                "result/content.md", "# 标题\n![图片](images/a.png)",
                "result/images/a.png", "fake-image"
        ))), workspace, 1024L);

        assertThat(result.markdownPath()).content(StandardCharsets.UTF_8).contains("# 标题");
        assertThat(result.assets()).singleElement().satisfies(asset -> {
            assertThat(asset.relativePath()).isEqualTo("images/a.png");
            assertThat(asset.file()).content(StandardCharsets.UTF_8).isEqualTo("fake-image");
        });
    }

    private byte[] zip(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutputStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }
        return outputStream.toByteArray();
    }
}
