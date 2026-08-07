package com.nexarag.document.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.document.enums.DocumentTaskStatus;
import com.nexarag.document.enums.DocumentTaskType;
import com.nexarag.document.enums.OutboxPublishStatus;
import com.nexarag.document.model.entity.DocumentTaskOutboxDO;
import com.nexarag.document.model.vo.DocumentTaskVO;
import com.nexarag.document.service.DocumentPipelineOutboxService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档任务人工重试测试，验证只复制失败任务的安全消息内容。
 */
class DocumentTaskAdminServiceImplTest {

    @Test
    void shouldCreatePendingCopyWhenRetryingFailedTask() {
        DocumentPipelineOutboxService outboxService = mock(DocumentPipelineOutboxService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        DocumentTaskOutboxDO failedTask = DocumentTaskOutboxDO.builder()
                .outboxId(11L)
                .documentId(1L)
                .processId("operation-old")
                .taskType(DocumentTaskType.CLEAN_DOCUMENT_INDEX)
                .messageKey("message-old")
                .topic("nexa-document-index-cleanup")
                .messageBody("{}")
                .publishStatus(OutboxPublishStatus.PUBLISHED)
                .taskStatus(DocumentTaskStatus.FAILED)
                .build();
        when(outboxService.getById(11L)).thenReturn(failedTask);
        when(outboxService.save(org.mockito.ArgumentMatchers.any(DocumentTaskOutboxDO.class))).thenReturn(true);
        try {
            when(objectMapper.readValue("{}", com.nexarag.infra.messaging.document.task.DocumentTaskMessage.class))
                    .thenReturn(new com.nexarag.infra.messaging.document.task.DocumentTaskMessage(11L, 1L, null,
                            "operation-old", "CLEAN_DOCUMENT_INDEX", 1, java.time.LocalDateTime.now()));
            when(objectMapper.writeValueAsString(org.mockito.ArgumentMatchers.any())).thenReturn("{\"outboxId\":12}");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        DocumentTaskAdminServiceImpl service = new DocumentTaskAdminServiceImpl(outboxService, objectMapper);

        DocumentTaskVO retried = service.retryFailedTask(11L);

        ArgumentCaptor<DocumentTaskOutboxDO> captor = ArgumentCaptor.forClass(DocumentTaskOutboxDO.class);
        verify(outboxService).save(captor.capture());
        assertThat(captor.getValue().getOutboxId()).isNotEqualTo(11L);
        assertThat(captor.getValue().getProcessId()).isNotEqualTo("operation-old");
        assertThat(captor.getValue().getTaskStatus()).isEqualTo(DocumentTaskStatus.PENDING);
        assertThat(captor.getValue().getMessageBody()).contains("outboxId");
        assertThat(retried.taskStatus()).isEqualTo(DocumentTaskStatus.PENDING);
    }
}
