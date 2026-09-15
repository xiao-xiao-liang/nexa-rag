package com.nexarag.boot.controller;

import com.nexarag.boot.observability.ModelObservabilityOverviewVO;
import com.nexarag.boot.observability.ModelObservabilityService;
import com.nexarag.boot.observability.ModelObservabilityTraceVO;
import com.nexarag.common.trace.TraceIdContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 模型观测控制器测试。
 */
class ModelObservabilityControllerTest {

    @AfterEach
    void tearDown() {
        TraceIdContext.clear();
    }

    @Test
    void shouldExposeOverviewWithoutSensitiveFields() throws Exception {
        ModelObservabilityService service = mock(ModelObservabilityService.class);
        when(service.overview(any())).thenReturn(new ModelObservabilityOverviewVO(true, null, 10, 9, 0.9D,
                100, 200, 20, 40, 50, 80, 7));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ModelObservabilityController(service)).build();

        mockMvc.perform(get("/api/model-observability/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.inputTokensP50").value(100))
                .andExpect(jsonPath("$.data.secretKey").doesNotExist())
                .andExpect(jsonPath("$.data.input").doesNotExist())
                .andExpect(jsonPath("$.data.output").doesNotExist())
                .andExpect(jsonPath("$.data.prompt").doesNotExist())
                .andExpect(jsonPath("$.data.question").doesNotExist())
                .andExpect(jsonPath("$.data.evidence").doesNotExist());
    }

    @Test
    void shouldExposeSafeTraceRowsWithoutContentOrUserFields() throws Exception {
        ModelObservabilityService service = mock(ModelObservabilityService.class);
        when(service.traces(any())).thenReturn(List.of(new ModelObservabilityTraceVO("trace-1", "generation-1",
                "2026-09-11T00:00:00Z", "Qwen3.5-4B", "local-qwen", "DEFAULT", 120, 30, 150, 30,
                "COMPLETE")));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ModelObservabilityController(service)).build();

        mockMvc.perform(get("/api/model-observability/traces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].traceId").value("trace-1"))
                .andExpect(jsonPath("$.data[0].inputTokens").value(120))
                .andExpect(jsonPath("$.data[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data[0].sessionId").doesNotExist())
                .andExpect(jsonPath("$.data[0].input").doesNotExist())
                .andExpect(jsonPath("$.data[0].output").doesNotExist())
                .andExpect(jsonPath("$.data[0].prompt").doesNotExist())
                .andExpect(jsonPath("$.data[0].question").doesNotExist());
    }
}
