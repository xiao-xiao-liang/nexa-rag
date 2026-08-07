package com.nexarag.document.model.vo;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档处理状态响应契约测试，防止已删除的精确排队字段重新暴露。
 */
class DocumentProcessStatusVOTest {

    @Test
    void shouldOnlyExposeDatabaseProcessingStatusFields() {
        String[] componentNames = Arrays.stream(DocumentProcessStatusVO.class.getRecordComponents())
                .map(component -> component.getName())
                .toArray(String[]::new);

        assertThat(componentNames).containsExactly(
                "documentId", "processId", "status", "messageStatus", "consumedTimes",
                "failureStage", "failureReason");
        assertThat(componentNames).doesNotContain(
                "queuePosition", "waitingCount", "running", "workerId", "leaseTtlSeconds");
    }
}
