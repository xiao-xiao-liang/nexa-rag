package com.nexarag.model.service.impl;

import com.nexarag.model.entity.ModelCallLog;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.enums.ModelCallStatus;
import com.nexarag.model.enums.ModelRequestType;
import com.nexarag.model.mapper.ModelCallLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 模型调用日志服务测试。
 */
@ExtendWith(MockitoExtension.class)
class ModelCallLogServiceImplTest {

    @Mock
    private ModelCallLogMapper modelCallLogMapper;

    @InjectMocks
    private ModelCallLogServiceImpl modelCallLogService;

    @Test
    void createRunningLogShouldFillBasicFields() {
        ReflectionTestUtils.setField(modelCallLogService, "baseMapper", modelCallLogMapper);
        when(modelCallLogMapper.insert(any(ModelCallLog.class))).thenReturn(1);

        ModelCallLog log = modelCallLogService.createRunningLog(
                "trace-1",
                ModelBizType.CHAT,
                "conversation-1",
                "chat-primary",
                "OPENAI",
                "http://localhost:11434/v1",
                "qwen2.5:7b",
                ModelRequestType.CHAT
        );

        assertThat(log.getCallId()).isNotBlank();
        assertThat(log.getTraceId()).isEqualTo("trace-1");
        assertThat(log.getStatus()).isEqualTo(ModelCallStatus.RUNNING);
        assertThat(log.getModelProfile()).isEqualTo("chat-primary");
    }
}
