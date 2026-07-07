package com.nexarag.model.controller;

import com.nexarag.model.dto.ModelGovernanceConfigResponse;
import com.nexarag.model.entity.ModelGovernanceConfig;
import com.nexarag.model.enums.ModelGovernanceBindingMode;
import com.nexarag.model.service.ModelGovernanceConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 模型治理配置 Controller 测试。
 */
@WebMvcTest(ModelGovernanceConfigController.class)
@Import(ModelGovernanceConfigController.class)
class ModelGovernanceConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ModelGovernanceConfigService modelGovernanceConfigService;

    @Test
    void listGovernanceConfigsShouldReturnGovernanceResponses() throws Exception {
        ModelGovernanceConfig config = governanceConfig();
        when(modelGovernanceConfigService.list()).thenReturn(List.of(config));
        when(modelGovernanceConfigService.toResponse(config)).thenReturn(governanceResponse());

        mockMvc.perform(get("/api/model/governance-configs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].governanceId").value(100L));
    }

    @Test
    void resetDefaultShouldDelegateToService() throws Exception {
        mockMvc.perform(post("/api/model/governance-configs/{governanceId}/reset-default", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(modelGovernanceConfigService).resetDefault(100L);
    }

    private ModelGovernanceConfig governanceConfig() {
        return ModelGovernanceConfig.builder()
                .governanceId(100L)
                .bindingMode(ModelGovernanceBindingMode.CONFIG)
                .configId(1L)
                .enabled(true)
                .build();
    }

    private ModelGovernanceConfigResponse governanceResponse() {
        return ModelGovernanceConfigResponse.builder()
                .governanceId(100L)
                .bindingMode(ModelGovernanceBindingMode.CONFIG)
                .configId(1L)
                .enabled(true)
                .build();
    }
}
