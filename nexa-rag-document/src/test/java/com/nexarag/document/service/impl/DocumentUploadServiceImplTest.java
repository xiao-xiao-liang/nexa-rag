package com.nexarag.document.service.impl;

import com.nexarag.common.exception.ClientException;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.config.DocumentUploadRetryProperties;
import com.nexarag.document.model.dto.UploadDocumentRequest;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.service.DocumentPipelineSubmitService;
import com.nexarag.document.service.DocumentUploadRetryWaiter;
import com.nexarag.document.service.ProcessConfigDefaults;
import com.nexarag.document.model.vo.UploadDocumentResponse;
import com.nexarag.infra.storage.StoredFile;
import com.nexarag.infra.storage.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档上传服务测试，验证对象存储重试和事务失败补偿行为。
 */
class DocumentUploadServiceImplTest {

    @Test
    void uploadShouldRetryTwiceAndSucceedOnThirdAttempt() {
        FileStorageService storageService = mock(FileStorageService.class);
        DocumentPipelineSubmitService submitService = mock(DocumentPipelineSubmitService.class);
        DocumentUploadRetryWaiter waiter = mock(DocumentUploadRetryWaiter.class);
        AtomicInteger attempts = new AtomicInteger();
        when(storageService.save(any(), any(InputStream.class), any(Long.class))).thenAnswer(invocation -> {
            if (attempts.incrementAndGet() < 3) {
                throw new ServiceException("模拟对象存储异常");
            }
            return storedFile();
        });
        when(submitService.createAndSubmit(any(), any(), any())).thenReturn(queuedDocument());

        UploadDocumentResponse response = service(storageService, submitService, waiter)
                .upload(10L, file(), new UploadDocumentRequest(null, "描述", null, null, null));

        assertThat(attempts).hasValue(3);
        verify(waiter).await(200L);
        verify(waiter).await(500L);
        assertThat(response.documentId()).isEqualTo(1L);
        assertThat(response.processId()).isEqualTo("process-1");
        assertThat(response.status()).isEqualTo(DocumentStatus.QUEUED);
    }

    @Test
    void uploadShouldNotCreateDocumentWhenStorageRetriesExhausted() {
        FileStorageService storageService = mock(FileStorageService.class);
        DocumentPipelineSubmitService submitService = mock(DocumentPipelineSubmitService.class);
        when(storageService.save(any(), any(InputStream.class), any(Long.class)))
                .thenThrow(new ServiceException("模拟对象存储异常"));

        assertThatThrownBy(() -> service(storageService, submitService, mock(DocumentUploadRetryWaiter.class))
                .upload(10L, file(), null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("重试耗尽");
        verify(submitService, never()).createAndSubmit(any(), any(), any());
    }

    @Test
    void uploadShouldDeleteStoredObjectWhenSubmitTransactionFails() {
        FileStorageService storageService = mock(FileStorageService.class);
        DocumentPipelineSubmitService submitService = mock(DocumentPipelineSubmitService.class);
        ServiceException failure = new ServiceException("模拟事务失败");
        when(storageService.save(any(), any(InputStream.class), any(Long.class))).thenReturn(storedFile());
        when(submitService.createAndSubmit(any(), any(), any())).thenThrow(failure);

        assertThatThrownBy(() -> service(storageService, submitService, mock(DocumentUploadRetryWaiter.class))
                .upload(10L, file(), null)).isSameAs(failure);
        verify(storageService).delete("original/demo.pdf");
    }

    @Test
    void uploadShouldRejectInvalidFileBeforeStorage() {
        FileStorageService storageService = mock(FileStorageService.class);
        DocumentPipelineSubmitService submitService = mock(DocumentPipelineSubmitService.class);

        assertThatThrownBy(() -> service(storageService, submitService, mock(DocumentUploadRetryWaiter.class))
                .upload(10L, new MockMultipartFile("file", "demo.exe", "application/octet-stream", new byte[]{1}), null))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("不支持的文档类型");
        verify(storageService, never()).save(any(), any(), any(Long.class));
    }

    private DocumentUploadServiceImpl service(FileStorageService storageService,
                                              DocumentPipelineSubmitService submitService,
                                              DocumentUploadRetryWaiter waiter) {
        MultipartProperties multipartProperties = new MultipartProperties();
        multipartProperties.setMaxFileSize(DataSize.ofMegabytes(100));
        DocumentUploadRetryProperties retryProperties = new DocumentUploadRetryProperties();
        retryProperties.setBackoffMillis(List.of(200L, 500L, 1000L));
        return new DocumentUploadServiceImpl(storageService, submitService, new ProcessConfigDefaults(),
                multipartProperties, retryProperties, waiter);
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "demo.pdf", "application/pdf", "hello".getBytes());
    }

    private StoredFile storedFile() {
        return new StoredFile("original/demo.pdf", "http://127.0.0.1/original/demo.pdf", 5L);
    }

    private Document queuedDocument() {
        return Document.builder()
                .documentId(1L)
                .processId("process-1")
                .status(DocumentStatus.QUEUED)
                .build();
    }
}
