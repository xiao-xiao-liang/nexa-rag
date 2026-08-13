package com.nexarag.infra.parser.publish;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.model.ExtractedDocumentBO;
import com.nexarag.infra.parser.model.ExtractedStructureArtifactBO;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.storage.ObjectNameResolver;
import com.nexarag.infra.storage.StoredFile;
import com.nexarag.infra.storage.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 发布器失败补偿测试。 */
class ArtifactPublisherTest {
    @TempDir Path tempDir;

    @Test
    void publishShouldDeleteDocumentPrefixWhenMarkdownUploadFails() throws Exception {
        FileStorageService storageService = mock(FileStorageService.class);
        doThrow(new ServiceException("上传失败")).when(storageService)
                .saveAs(anyString(), any(), anyLong(), anyString());
        ArtifactPublisher publisher = new ArtifactPublisher(storageService, new ObjectNameResolver(),
                new MarkdownAssetFileRewriter());
        Path markdown = tempDir.resolve("content.md");
        Files.writeString(markdown, "# 标题");

        assertThatThrownBy(() -> publisher.publish(DocumentArtifactDTO.builder().documentId(1L)
                .originalFileName("demo.docx").format(DocumentFormat.WORD).build(),
                new ExtractedDocumentBO(markdown, List.of(), Map.of())))
                .isInstanceOf(ServiceException.class);
        verify(storageService).deleteByPrefix("parsed/1/");
    }

    @Test
    void publishShouldMarkMinerUContentListV2AsDedicatedStructureArtifact() throws Exception {
        FileStorageService storageService = mock(FileStorageService.class);
        when(storageService.saveAs(anyString(), any(), anyLong(), anyString()))
                .thenAnswer(invocation -> new StoredFile(invocation.getArgument(0), "http://storage/test", 1L));
        ArtifactPublisher publisher = new ArtifactPublisher(storageService, new ObjectNameResolver(),
                new MarkdownAssetFileRewriter());
        Path markdown = tempDir.resolve("content.md");
        Path contentListV2 = tempDir.resolve("mineru-content-list-v2.json");
        Files.writeString(markdown, "# 标题");
        Files.writeString(contentListV2, "[]");

        ParsedArtifact result = publisher.publish(DocumentArtifactDTO.builder().documentId(1L)
                        .originalFileName("demo.pdf").format(DocumentFormat.PDF).build(),
                new ExtractedDocumentBO(markdown, List.of(),
                        List.of(new ExtractedStructureArtifactBO(contentListV2, "mineru-content-list-v2.json",
                                "application/json")), Map.of()));

        assertThat(result.metadata().get("structureArtifacts"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("type", "MINERU_CONTENT_LIST_V2_JSON");
    }
}
