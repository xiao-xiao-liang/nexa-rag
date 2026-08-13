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
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
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
        assertThat(result.structureArtifacts()).isEmpty();
    }

    @Test
    void extractShouldKeepOnlyKnownStructureJsonArtifacts() throws Exception {
        ArtifactWorkspace workspace = mock(ArtifactWorkspace.class);
        when(workspace.resolve("mineru/result/content.md")).thenReturn(tempDir.resolve("mineru/result/content.md"));
        when(workspace.resolve("structure/mineru-middle.json")).thenReturn(tempDir.resolve("structure/mineru-middle.json"));
        when(workspace.resolve("structure/mineru-content-list.json"))
                .thenReturn(tempDir.resolve("structure/mineru-content-list.json"));
        MinerUZipFileExtractor extractor = new MinerUZipFileExtractor(new BoundedFileTransfer());

        ExtractedDocumentBO result = extractor.extract(new ByteArrayInputStream(zip(Map.of(
                "result/content.md", "# 标题",
                "result/middle.json", "{\"pdf_info\":[]}",
                "result/content_list.json", "[]",
                "result/ignored.json", "{}"
        ))), workspace, 1024L);

        assertThat(result.structureArtifacts()).extracting(artifact -> artifact.relativePath())
                .containsExactlyInAnyOrder("mineru-middle.json", "mineru-content-list.json");
    }

    @Test
    void extractShouldRecognizeOfficialPrefixedStructureJsonArtifacts() throws Exception {
        ArtifactWorkspace workspace = mock(ArtifactWorkspace.class);
        when(workspace.resolve("mineru/result/full.md")).thenReturn(tempDir.resolve("mineru/result/full.md"));
        when(workspace.resolve("structure/mineru-middle.json"))
                .thenReturn(tempDir.resolve("structure/mineru-middle.json"));
        when(workspace.resolve("structure/mineru-content-list.json"))
                .thenReturn(tempDir.resolve("structure/mineru-content-list.json"));
        when(workspace.resolve("structure/mineru-content-list-v2.json"))
                .thenReturn(tempDir.resolve("structure/mineru-content-list-v2.json"));
        MinerUZipFileExtractor extractor = new MinerUZipFileExtractor(new BoundedFileTransfer());

        ExtractedDocumentBO result = extractor.extract(new ByteArrayInputStream(zip(Map.of(
                "result/full.md", "# 标题",
                "result/Java集合_middle.json", "{\"pdf_info\":[]}",
                "result/Java集合_content_list.json", "[]",
                "result/Java集合_content_list_v2.json", "[]"
        ))), workspace, 1024L);

        assertThat(result.structureArtifacts()).extracting(artifact -> artifact.relativePath())
                .containsExactlyInAnyOrder("mineru-middle.json", "mineru-content-list.json",
                        "mineru-content-list-v2.json");
    }

    @Test
    void extractShouldRecognizeOfficialLayoutJsonAndRecordJsonEntries() throws Exception {
        ArtifactWorkspace workspace = mock(ArtifactWorkspace.class);
        when(workspace.resolve("mineru/result/full.md")).thenReturn(tempDir.resolve("mineru/result/full.md"));
        when(workspace.resolve("structure/mineru-middle.json"))
                .thenReturn(tempDir.resolve("structure/mineru-middle.json"));
        MinerUZipFileExtractor extractor = new MinerUZipFileExtractor(new BoundedFileTransfer());

        ExtractedDocumentBO result = extractor.extract(new ByteArrayInputStream(zip(Map.of(
                "result/full.md", "# 标题",
                "result/layout.json", "{\"pdf_info\":[]}",
                "result/unknown.json", "{}"
        ))), workspace, 1024L);

        assertThat(result.structureArtifacts()).extracting(artifact -> artifact.relativePath())
                .containsExactly("mineru-middle.json");
        Object zipJsonEntries = result.metadata().get("zipJsonEntries");
        assertThat(zipJsonEntries).asInstanceOf(LIST)
                .containsExactlyInAnyOrder("result/layout.json", "result/unknown.json");
        assertThat(result.metadata())
                .containsEntry("zipJsonEntryCount", 2)
                .containsEntry("zipJsonEntriesTruncated", false);
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
