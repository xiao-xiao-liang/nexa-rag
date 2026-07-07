package com.nexarag.model.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.model.dto.ModelConnectionTestResponse;
import com.nexarag.model.dto.ModelRouteResponse;
import com.nexarag.model.entity.ModelRoute;
import com.nexarag.model.enums.ModelRouteStrategy;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.service.ModelConnectionTestService;
import com.nexarag.model.service.ModelRouteService;
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
 * 模型路由 Controller 测试。
 */
@WebMvcTest(ModelRouteController.class)
@Import(ModelRouteController.class)
class ModelRouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ModelRouteService modelRouteService;

    @MockitoBean
    private ModelConnectionTestService modelConnectionTestService;

    @Test
    void listRoutesShouldReturnRouteResponses() throws Exception {
        when(modelRouteService.listRouteResponses()).thenReturn(List.of(routeResponse()));

        mockMvc.perform(get("/api/model/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].routeKey").value("chat.default"));
    }

    @Test
    void createRouteShouldReturnCreatedRoute() throws Exception {
        ModelRoute route = route();
        when(modelRouteService.createRoute(any())).thenReturn(route);
        when(modelRouteService.toResponse(route)).thenReturn(routeResponse());

        mockMvc.perform(post("/api/model/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "routeKey", "chat.default",
                                "modelType", "CHAT",
                                "strategy", "PRIMARY_BACKUP"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routeId").value(1L));
    }

    @Test
    void patchRouteShouldReturnUpdatedRoute() throws Exception {
        ModelRoute route = route();
        when(modelRouteService.updateRoute(eq(1L), any())).thenReturn(route);
        when(modelRouteService.toResponse(route)).thenReturn(routeResponse());

        mockMvc.perform(patch("/api/model/routes/{routeId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routeKey").value("chat.default"));
    }

    @Test
    void deleteRouteShouldDelegateToService() throws Exception {
        mockMvc.perform(delete("/api/model/routes/{routeId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(modelRouteService).deleteRoute(1L);
    }

    @Test
    void createRouteConnectionTestShouldDelegateToConnectionTestService() throws Exception {
        when(modelConnectionTestService.testRoute(eq(1L), any())).thenReturn(ModelConnectionTestResponse.builder()
                .success(true)
                .build());

        mockMvc.perform(post("/api/model/routes/{routeId}/connection-tests", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));
    }

    private ModelRoute route() {
        return ModelRoute.builder()
                .routeId(1L)
                .routeKey("chat.default")
                .modelType(ModelType.CHAT)
                .strategy(ModelRouteStrategy.PRIMARY_BACKUP)
                .enabled(true)
                .build();
    }

    private ModelRouteResponse routeResponse() {
        return ModelRouteResponse.builder()
                .routeId(1L)
                .routeKey("chat.default")
                .modelType(ModelType.CHAT)
                .strategy(ModelRouteStrategy.PRIMARY_BACKUP)
                .enabled(true)
                .build();
    }
}
