package com.nexarag.model.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.model.dto.ModelConfigResponse;
import com.nexarag.model.dto.ModelConfigUpdateRequest;
import com.nexarag.model.dto.ModelGovernanceConfigResponse;
import com.nexarag.model.entity.ModelConfig;
import com.nexarag.model.entity.ModelGovernanceConfig;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.service.ModelConfigService;
import com.nexarag.model.service.ModelConnectionTestService;
import com.nexarag.model.service.ModelGovernanceConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 模型配置 Controller 测试。
 */
@WebMvcTest(ModelConfigController.class)
@Import(ModelConfigController.class)
class ModelConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ModelConfigService modelConfigService;

    @MockitoBean
    private ModelConnectionTestService modelConnectionTestService;

    @MockitoBean
    private ModelGovernanceConfigService modelGovernanceConfigService;

    @Test
    void listConfigsShouldReturnConfigResponses() throws Exception {
        when(modelConfigService.listConfigResponses()).thenReturn(List.of(configResponse()));

        mockMvc.perform(get("/api/model/configs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].configId").value(1L))
                .andExpect(jsonPath("$.data[0].apiKeyMask").value("sk-****cdef"));
    }

    @Test
    void getRawApiKeyShouldReturnRawKey() throws Exception {
        when(modelConfigService.getRawApiKey(1L)).thenReturn("sk-raw-secret-key-123456");

        mockMvc.perform(get("/api/model/configs/{configId}/raw-key", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value("sk-raw-secret-key-123456"));
    }

    @Test
    void createConfigShouldReturnCreatedConfigResponse() throws Exception {
        ModelConfig config = ModelConfig.builder()
                .configId(1L)
                .build();
        when(modelConfigService.createConfig(any())).thenReturn(config);
        when(modelConfigService.getConfigResponse(1L)).thenReturn(configResponse());

        mockMvc.perform(post("/api/model/configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "configKey", "embedding.openai",
                                "modelType", "EMBEDDING",
                                "provider", "OPENAI",
                                "baseUrl", "https://api.openai.com/v1",
                                "apiKey", "sk-test-abcdef",
                                "modelName", "text-embedding-3-small"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configId").value(1L));
    }

    @Test
    void updateConfigShouldIgnoreBlankFieldsAndUpdateOnlyPresentValues() throws Exception {
        ModelConfig config = ModelConfig.builder()
                .configId(2074002239234560001L)
                .build();
        when(modelConfigService.updateConfig(eq(2074002239234560001L), any())).thenReturn(config);
        when(modelConfigService.getConfigResponse(2074002239234560001L)).thenReturn(configResponse());

        mockMvc.perform(put("/api/model/configs/{configId}", 2074002239234560001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "configKey": "",
                                    "modelType": "",
                                    "provider": "",
                                    "baseUrl": "",
                                    "endpointPath": "",
                                    "apiKey": "sk-893049d3636b46339b0dc63816ac6443",
                                    "modelName": "",
                                    "enabled": true,
                                    "timeoutMs": 50000,
                                    "maxRetries": 3,
                                    "extraConfig": "",
                                    "remark": ""
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        ArgumentCaptor<ModelConfigUpdateRequest> requestCaptor = forClass(ModelConfigUpdateRequest.class);
        verify(modelConfigService).updateConfig(eq(2074002239234560001L), requestCaptor.capture());
        ModelConfigUpdateRequest request = requestCaptor.getValue();
        assertThat(request.modelType()).isNull();
        assertThat(request.provider()).isNull();
        assertThat(request.apiKey()).isEqualTo("sk-893049d3636b46339b0dc63816ac6443");
        assertThat(request.timeoutMs()).isEqualTo(50000);
        assertThat(request.maxRetries()).isEqualTo(3);
    }

    @Test
    void deleteConfigShouldDelegateToService() throws Exception {
        mockMvc.perform(delete("/api/model/configs/{configId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(modelConfigService).deleteConfig(eq(1L));
    }

    @Test
    void getGovernanceShouldReturnGovernanceConfig() throws Exception {
        ModelGovernanceConfig config = governanceConfig();
        when(modelGovernanceConfigService.getByConfigId(1L)).thenReturn(config);
        when(modelGovernanceConfigService.toResponse(config)).thenReturn(governanceResponse());

        mockMvc.perform(get("/api/model/configs/{configId}/governance", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.configId").value(1L))
                .andExpect(jsonPath("$.data.maxAttempts").value(2));
    }

    @Test
    void saveGovernanceShouldReturnSavedGovernanceConfig() throws Exception {
        ModelGovernanceConfig config = governanceConfig();
        when(modelGovernanceConfigService.saveByConfigId(eq(1L), any())).thenReturn(config);
        when(modelGovernanceConfigService.toResponse(config)).thenReturn(governanceResponse());

        mockMvc.perform(put("/api/model/configs/{configId}/governance", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "enabled", true,
                                "retryEnabled", true,
                                "maxAttempts", 2,
                                "retryWaitMs", 100
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.retryEnabled").value(true))
                .andExpect(jsonPath("$.data.maxAttempts").value(2));
    }

    private ModelConfigResponse configResponse() {
        return ModelConfigResponse.builder()
                .configId(1L)
                .configKey("embedding.openai")
                .modelType(ModelType.EMBEDDING)
                .provider(ModelProvider.OPENAI)
                .baseUrl("https://api.openai.com/v1")
                .apiKeyMask("sk-****cdef")
                .modelName("text-embedding-3-small")
                .enabled(true)
                .timeoutMs(30000)
                .maxRetries(0)
                .version(1L)
                .build();
    }

    private ModelGovernanceConfig governanceConfig() {
        return ModelGovernanceConfig.builder()
                .governanceId(100L)
                .configId(1L)
                .enabled(true)
                .retryEnabled(true)
                .maxAttempts(2)
                .retryWaitMs(100)
                .build();
    }

    private ModelGovernanceConfigResponse governanceResponse() {
        return ModelGovernanceConfigResponse.builder()
                .governanceId(100L)
                .configId(1L)
                .enabled(true)
                .retryEnabled(true)
                .maxAttempts(2)
                .retryWaitMs(100)
                .build();
    }
}
