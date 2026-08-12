package com.nexarag.infra.parser.publish;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.DocumentFormat;
import com.nexarag.infra.parser.model.ExtractedDocumentBO;
import com.nexarag.infra.storage.ObjectNameResolver;
import com.nexarag.infra.storage.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
}
