package com.nexarag.model.execution;

import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.entity.ModelCallLog;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.enums.ModelRequestType;
import com.nexarag.model.route.ModelRouteContext;
import com.nexarag.model.route.ModelRouteDecision;
import com.nexarag.model.route.ModelRouter;
import com.nexarag.model.service.ModelCallLogService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模型执行模板测试。
 */
class ModelExecutionTemplateTest {

    @Test
    void shouldRecordSuccessLog() {
        ModelRouter router = mock(ModelRouter.class);
        ModelCallLogService logService = mock(ModelCallLogService.class);

        ModelProfileProperties profile = new ModelProfileProperties();
        profile.setProvider("OPENAI_COMPATIBLE");
        profile.setBaseUrl("http://localhost:11434/v1");
        profile.setModelName("qwen2.5:7b");
        when(router.route(any(ModelRouteContext.class)))
                .thenReturn(new ModelRouteDecision("chat-primary", profile, false));

        ModelCallLog log = new ModelCallLog();
        log.setCallId("call-1");
        when(logService.createRunningLog(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(log);

        ModelExecutionTemplate template = new ModelExecutionTemplate(router, logService);

        String result = template.execute(new ModelExecutionCommand<>(
                "trace-1", ModelBizType.CHAT, "biz-1", "chat", ModelRequestType.CHAT,
                decision -> "ok", response -> 1, response -> 2, response -> 3
        ));

        assertThat(result).isEqualTo("ok");
        verify(logService).markSuccess(eq("call-1"), eq(1), eq(2), eq(3), longThat(durationMs -> durationMs >= 0));
    }
}
