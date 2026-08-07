package com.nexarag.document.service.impl;

import com.nexarag.document.config.DocumentPipelineOutboxProperties;
import com.nexarag.document.model.entity.DocumentTaskOutboxDO;
import com.nexarag.document.enums.OutboxPublishStatus;
import com.nexarag.document.mapper.DocumentPipelineOutboxMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档流水线Outbox服务测试。
 */
class DocumentPipelineOutboxServiceImplTest {

    @Test
    void claimPublishableMessagesShouldReturnOnlyClaimedRecords() {
        DocumentPipelineOutboxMapper mapper = mock(DocumentPipelineOutboxMapper.class);
        DocumentTaskOutboxDO candidate = DocumentTaskOutboxDO.builder()
                .outboxId(1L)
                .publishStatus(OutboxPublishStatus.PENDING)
                .build();
        when(mapper.selectPublishable(any(), any(), eq(10))).thenReturn(List.of(candidate));
        when(mapper.claim(eq(1L), eq(OutboxPublishStatus.PENDING), eq(null), eq("worker-1"), any()))
                .thenReturn(1);
        DocumentPipelineOutboxServiceImpl service = new DocumentPipelineOutboxServiceImpl(mapper, properties());

        List<DocumentTaskOutboxDO> claimed = service.claimPublishableMessages("worker-1", LocalDateTime.now());

        assertThat(claimed).containsExactly(candidate);
    }

    @Test
    void markPublishFailedShouldReachFailedStatusAtRetryLimit() {
        DocumentPipelineOutboxMapper mapper = mock(DocumentPipelineOutboxMapper.class);
        DocumentTaskOutboxDO outbox = DocumentTaskOutboxDO.builder()
                .outboxId(1L)
                .publishRetryCount(2)
                .build();
        when(mapper.selectById(1L)).thenReturn(outbox);
        when(mapper.updatePublishFailure(eq(1L), eq(OutboxPublishStatus.FAILED), eq(3),
                eq(null), eq("测试失败"))).thenReturn(1);
        DocumentPipelineOutboxProperties properties = properties();
        properties.setMaxPublishRetries(3);
        DocumentPipelineOutboxServiceImpl service = new DocumentPipelineOutboxServiceImpl(mapper, properties);

        service.markPublishFailed(1L, "测试失败");

        verify(mapper).updatePublishFailure(eq(1L), eq(OutboxPublishStatus.FAILED), eq(3),
                eq(null), eq("测试失败"));
    }

    private DocumentPipelineOutboxProperties properties() {
        DocumentPipelineOutboxProperties properties = new DocumentPipelineOutboxProperties();
        properties.setBatchSize(10);
        properties.setPublishingTimeoutSeconds(60);
        properties.setMaxPublishRetries(3);
        properties.setInitialRetryDelaySeconds(1);
        properties.setMaxRetryDelaySeconds(60);
        return properties;
    }
}
