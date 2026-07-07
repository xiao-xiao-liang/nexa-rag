package com.nexarag.model.controller;

import com.nexarag.model.dto.ModelProviderCatalogResponse;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.service.ModelProviderCatalogService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 模型厂商推荐值 Controller 测试。
 */
@WebMvcTest(ModelProviderController.class)
@Import(ModelProviderController.class)
class ModelProviderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ModelProviderCatalogService modelProviderCatalogService;

    @Test
    void listProvidersShouldReturnProviderCatalog() throws Exception {
        when(modelProviderCatalogService.listProviders()).thenReturn(List.of(
                providerCatalog(ModelProvider.OPENAI),
                providerCatalog(ModelProvider.OLLAMA),
                providerCatalog(ModelProvider.DASHSCOPE)
        ));

        mockMvc.perform(get("/api/model/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].provider").value("OPENAI"))
                .andExpect(jsonPath("$.data[1].provider").value("OLLAMA"))
                .andExpect(jsonPath("$.data[2].provider").value("DASHSCOPE"));
    }

    @Test
    void listProviderCatalogShouldReturnProviderCatalog() throws Exception {
        when(modelProviderCatalogService.listProviders()).thenReturn(List.of(providerCatalog(ModelProvider.OPENAI)));

        mockMvc.perform(get("/api/model/providers/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].provider").value("OPENAI"));
    }

    private ModelProviderCatalogResponse providerCatalog(ModelProvider provider) {
        return ModelProviderCatalogResponse.builder()
                .provider(provider)
                .displayName(provider.name())
                .supportedTypes(List.of(ModelType.CHAT))
                .defaultBaseUrl("https://example.com/v1")
                .recommendedModels(Map.of())
                .apiKeyRequired(true)
                .build();
    }
}
