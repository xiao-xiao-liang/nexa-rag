package com.nexarag.document.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.document.enums.DocumentTaskStatus;
import com.nexarag.document.enums.DocumentTaskType;
import com.nexarag.document.enums.OutboxPublishStatus;
import com.nexarag.document.model.entity.DocumentTaskOutboxDO;
import com.nexarag.document.service.DocumentPipelineOutboxService;
import com.nexarag.infra.config.AlertProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档告警任务创建服务测试，验证两个渠道使用独立Outbox记录。
 */
class DocumentTaskAlertServiceImplTest {

    @Test
    void shouldCreateIndependentTasksForFeishuAndEmail() throws Exception {
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        AlertProperties properties = new AlertProperties();
        properties.setTopic("nexa-alert");
        DocumentTaskOutboxDO parent = DocumentTaskOutboxDO.builder()
                .outboxId(101L)
                .documentId(1L)
                .processId("operation-1")
                .taskType(DocumentTaskType.CLEAN_DOCUMENT_INDEX)
                .build();
        when(outboxService.getById(101L)).thenReturn(parent);
        when(outboxService.save(org.mockito.ArgumentMatchers.any(DocumentTaskOutboxDO.class))).thenReturn(true);
        when(objectMapper.writeValueAsString(org.mockito.ArgumentMatchers.any())).thenReturn("{}");
        DocumentTaskAlertServiceImpl service = new DocumentTaskAlertServiceImpl(outboxService, objectMapper, properties);

        service.createFailureAlerts(101L, 5, "索引清理失败");

        ArgumentCaptor<DocumentTaskOutboxDO> captor = ArgumentCaptor.forClass(DocumentTaskOutboxDO.class);
        verify(outboxService, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(DocumentTaskOutboxDO::getTaskType)
                .containsExactly(DocumentTaskType.SEND_FEISHU_FAILURE_ALERT,
                        DocumentTaskType.SEND_EMAIL_FAILURE_ALERT);
        assertThat(captor.getAllValues())
                .allSatisfy(task -> {
                    assertThat(task.getParentOutboxId()).isEqualTo(101L);
                    assertThat(task.getPublishStatus()).isEqualTo(OutboxPublishStatus.PENDING);
                    assertThat(task.getTaskStatus()).isEqualTo(DocumentTaskStatus.PENDING);
                });
    }
}
