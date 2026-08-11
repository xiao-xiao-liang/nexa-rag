package com.nexarag.infra.source;

import com.nexarag.infra.enums.ExternalDocumentSourceType;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.source.model.SourceArtifactBO;
import com.nexarag.infra.source.model.SourceReadRequestDTO;
import com.nexarag.infra.source.model.SourceReadResultBO;
import com.nexarag.infra.storage.ObjectNameResolver;
import com.nexarag.infra.storage.service.FileStorageService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 外部来源读取服务测试。
 */
class ExternalDocumentSourceServiceImplTest {

    @Test
    void readAndPersistShouldSaveSnapshotAndNormalizedMarkdown() {
        ExternalDocumentSourceReader reader = mock(ExternalDocumentSourceReader.class);
        FileStorageService storageService = mock(FileStorageService.class);
        ObjectNameResolver objectNameResolver = mock(ObjectNameResolver.class);
        SourceReadRequestDTO request = new SourceReadRequestDTO(1001L, ExternalDocumentSourceType.FEISHU_DOCX,
                "https://tenant.feishu.cn/docx/abc");
        when(reader.supports(ExternalDocumentSourceType.FEISHU_DOCX)).thenReturn(true);
        when(reader.read(request)).thenReturn(new SourceReadResultBO(
                "{\"blocks\":[]}".getBytes(StandardCharsets.UTF_8), "application/json", "# 标题", "标题",
                "abc", "rev-1", Map.of("platform", "feishu")));
        when(objectNameResolver.resolveSourceSnapshotObjectName(1001L, ".json"))
                .thenReturn("source-snapshots/1001/source.json");
        when(objectNameResolver.resolveParsedObjectName(1001L, "source.md", ".md"))
                .thenReturn("parsed/1001/content.md");

        ExternalDocumentSourceService service = new ExternalDocumentSourceServiceImpl(
                List.of(reader), storageService, objectNameResolver);
        SourceArtifactBO artifact = service.readAndPersist(request);

        assertThat(artifact.parsedArtifact()).isEqualTo(ParsedArtifact.builder()
                .objectKey("parsed/1001/content.md").contentType("text/markdown")
                .metadata(Map.of("platform", "feishu")).build());
        assertThat(artifact.sourceSnapshotObjectName()).isEqualTo("source-snapshots/1001/source.json");
        org.mockito.Mockito.verify(storageService).saveAs(eq("source-snapshots/1001/source.json"), any(), anyLong(),
                eq("application/json"));
        org.mockito.Mockito.verify(storageService).saveAs(eq("parsed/1001/content.md"), any(), anyLong(),
                eq("text/markdown"));
    }
}
