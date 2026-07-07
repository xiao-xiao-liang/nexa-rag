package com.nexarag.model.controller;

import com.nexarag.model.entity.ModelConfig;
import com.nexarag.model.entity.ModelGovernanceConfig;
import com.nexarag.model.entity.ModelRoute;
import com.nexarag.model.entity.ModelRouteConfig;
import com.nexarag.model.registry.ModelRegistry;
import com.nexarag.model.registry.ModelRegistryRefresher;
import com.nexarag.model.registry.ModelRegistrySnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 模型注册表 Controller 测试。
 */
@WebMvcTest(ModelRegistryController.class)
@Import(ModelRegistryController.class)
class ModelRegistryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ModelRegistry modelRegistry;

    @MockitoBean
    private ModelRegistryRefresher modelRegistryRefresher;

    @Test
    void refreshShouldReturnRefreshResult() throws Exception {
        when(modelRegistryRefresher.refreshCurrentVersion()).thenReturn(true);

        mockMvc.perform(post("/api/model/registry/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void getSnapshotShouldReturnSnapshotSummary() throws Exception {
        when(modelRegistry.current()).thenReturn(new ModelRegistrySnapshot(
                3L,
                Map.of(1L, new ModelConfig()),
                Map.of(2L, new ModelRoute()),
                Map.of(2L, List.of(new ModelRouteConfig())),
                Map.of("CONFIG:1", new ModelGovernanceConfig())
        ));

        mockMvc.perform(get("/api/model/registry/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.versionNo").value(3L))
                .andExpect(jsonPath("$.data.configCount").value(1))
                .andExpect(jsonPath("$.data.routeConfigCount").value(1));
    }
}
