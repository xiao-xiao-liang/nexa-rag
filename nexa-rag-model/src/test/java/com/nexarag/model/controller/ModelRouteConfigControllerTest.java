package com.nexarag.model.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.model.dto.ModelRouteConfigResponse;
import com.nexarag.model.entity.ModelRouteConfig;
import com.nexarag.model.enums.ModelRouteRole;
import com.nexarag.model.service.ModelRouteConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 模型路由候选配置 Controller 测试。
 */
@WebMvcTest(ModelRouteConfigController.class)
@Import(ModelRouteConfigController.class)
class ModelRouteConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ModelRouteConfigService modelRouteConfigService;

    @Test
    void listRouteConfigsShouldReturnRouteConfigResponses() throws Exception {
        when(modelRouteConfigService.listRouteConfigResponses(1L)).thenReturn(List.of(routeConfigResponse()));

        mockMvc.perform(get("/api/model/routes/{routeId}/configs", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].configId").value(100L));
    }

    @Test
    void createRouteConfigShouldReturnCreatedRouteConfig() throws Exception {
        ModelRouteConfig routeConfig = routeConfig();
        when(modelRouteConfigService.createRouteConfig(eq(1L), any())).thenReturn(routeConfig);
        when(modelRouteConfigService.toResponse(routeConfig)).thenReturn(routeConfigResponse());

        mockMvc.perform(post("/api/model/routes/{routeId}/configs", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "configId", 100L,
                                "role", "PRIMARY",
                                "priority", 0,
                                "weight", 100
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routeConfigId").value(10L));
    }

    @Test
    void patchRouteConfigShouldReturnUpdatedRouteConfig() throws Exception {
        ModelRouteConfig routeConfig = routeConfig();
        when(modelRouteConfigService.updateRouteConfig(eq(1L), eq(10L), any())).thenReturn(routeConfig);
        when(modelRouteConfigService.toResponse(routeConfig)).thenReturn(routeConfigResponse());

        mockMvc.perform(patch("/api/model/routes/{routeId}/configs/{routeConfigId}", 1L, 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routeConfigId").value(10L));
    }

    @Test
    void deleteRouteConfigShouldDelegateToService() throws Exception {
        mockMvc.perform(delete("/api/model/routes/{routeId}/configs/{routeConfigId}", 1L, 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(modelRouteConfigService).deleteRouteConfig(1L, 10L);
    }

    private ModelRouteConfig routeConfig() {
        return ModelRouteConfig.builder()
                .routeConfigId(10L)
                .routeId(1L)
                .configId(100L)
                .role(ModelRouteRole.PRIMARY)
                .priority(0)
                .weight(100)
                .enabled(true)
                .build();
    }

    private ModelRouteConfigResponse routeConfigResponse() {
        return ModelRouteConfigResponse.builder()
                .routeConfigId(10L)
                .routeId(1L)
                .configId(100L)
                .role(ModelRouteRole.PRIMARY)
                .priority(0)
                .weight(100)
                .enabled(true)
                .build();
    }
}
