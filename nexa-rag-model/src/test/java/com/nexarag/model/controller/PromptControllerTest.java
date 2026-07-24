package com.nexarag.model.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.model.dto.prompt.PromptResponse;
import com.nexarag.model.service.PromptManagementService;
import com.nexarag.model.prompt.PromptOperatorProvider;
import com.nexarag.model.service.PromptPublishService;
import com.nexarag.model.prompt.domain.PromptReleaseResult;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prompt 在线管理接口测试。
 */
@WebMvcTest(PromptController.class)
@Import(PromptController.class)
class PromptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PromptManagementService promptManagementService;

    @MockitoBean
    private PromptPublishService promptPublishService;

    @MockitoBean
    private PromptOperatorProvider promptOperatorProvider;

    @Test
    void listPromptsShouldReturnPromptDefinitions() throws Exception {
        when(promptManagementService.listPrompts()).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/model/prompts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].promptCode").value("chat.rewrite.instruction"));
    }

    @Test
    void getPromptShouldReturnVersionsAndReleaseHistory() throws Exception {
        when(promptManagementService.getPrompt("chat.rewrite.instruction")).thenReturn(response());

        mockMvc.perform(get("/api/model/prompts/{promptCode}", "chat.rewrite.instruction"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versions[0].versionId").value(101L))
                .andExpect(jsonPath("$.data.releases[0].releaseId").value(201L));
    }

    @Test
    void previewShouldRenderOnlyDesensitizedVariablesWithoutCallingModelOrWritingDatabase() throws Exception {
        when(promptManagementService.preview(eq("chat.rewrite.instruction"), eq("问题：{{question}}"), any()))
                .thenReturn("问题：示例问题");

        mockMvc.perform(post("/api/model/prompts/{promptCode}/preview", "chat.rewrite.instruction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "问题：{{question}}"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("问题：示例问题"));

        verify(promptPublishService, never()).submit(any(), any(), any());
        verify(promptPublishService, never()).release(any(), any(), any(), any(), any());
        verify(promptPublishService, never()).rollback(any(), any(), any());
    }

    @Test
    void submitShouldUseCurrentUserAsOperatorAndReturnReleaseResult() throws Exception {
        when(promptOperatorProvider.getCurrentOperator()).thenReturn("u1");
        when(promptPublishService.submit("chat.rewrite.instruction", "问题：{{question}}", "u1"))
                .thenReturn(new PromptReleaseResult(101L, 201L, 2L));

        mockMvc.perform(post("/api/model/prompts/{promptCode}/submit", "chat.rewrite.instruction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "问题：{{question}}"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionId").value(101L))
                .andExpect(jsonPath("$.data.releaseRevision").value(2L));
    }

    @Test
    void releaseShouldRejectCanaryPercentageOutsideZeroToOneHundred() throws Exception {
        when(promptOperatorProvider.getCurrentOperator()).thenReturn("u1");

        mockMvc.perform(post("/api/model/prompts/{promptCode}/release", "chat.rewrite.instruction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "stableVersionId", 101L,
                                "canaryVersionId", 102L,
                                "canaryPercentage", 101
                        ))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void rollbackShouldDelegateOwnershipValidationToPublishService() throws Exception {
        when(promptOperatorProvider.getCurrentOperator()).thenReturn("u1");
        when(promptPublishService.rollback("chat.rewrite.instruction", 101L, "u1"))
                .thenReturn(new PromptReleaseResult(101L, 202L, 3L));

        mockMvc.perform(post("/api/model/prompts/{promptCode}/rollback", "chat.rewrite.instruction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("targetVersionId", 101L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.releaseId").value(202L));

        verify(promptPublishService).rollback("chat.rewrite.instruction", 101L, "u1");
    }

    private PromptResponse response() {
        return PromptResponse.builder()
                .promptCode("chat.rewrite.instruction")
                .name("问题改写")
                .enabled(true)
                .currentReleaseId(201L)
                .currentReleaseRevision(2L)
                .versions(List.of(PromptResponse.Version.builder().versionId(101L).versionNo(1L).build()))
                .releases(List.of(PromptResponse.Release.builder().releaseId(201L).releaseRevision(2L).build()))
                .build();
    }
}
