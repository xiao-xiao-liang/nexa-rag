package com.nexarag.model.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.chat.ChatModelResponse;
import com.nexarag.model.gateway.chat.ChatModelStreamResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 模型 Chat Controller 测试。
 */
@WebMvcTest(ModelChatController.class)
@Import(ModelChatController.class)
class ModelChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ModelGateway modelGateway;

    @Test
    void chatShouldCallModelGateway() throws Exception {
        when(modelGateway.chat(argThat(request -> "chat.default".equals(request.routeKey())
                && "你好".equals(request.messages().getFirst().content())
                && "stream".equals(request.options().get("mode")))))
                .thenReturn(ChatModelResponse.builder()
                        .content("你好，我是 NexaRAG")
                        .modelProfile("chat-primary")
                        .promptTokens(3)
                        .completionTokens(6)
                        .totalTokens(9)
                        .build());

        mockMvc.perform(post("/api/model/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "routeKey", "chat.default",
                                "messages", List.of(Map.of("role", "USER", "content", "你好")),
                                "options", Map.of("mode", "stream")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.content").value("你好，我是 NexaRAG"))
                .andExpect(jsonPath("$.data.totalTokens").value(9));
    }

    @Test
    void streamChatShouldReturnStreamChunks() throws Exception {
        when(modelGateway.streamChat(argThat(request -> "chat.default".equals(request.routeKey())
                && "你好".equals(request.messages().getFirst().content()))))
                .thenReturn(Flux.just(
                        ChatModelStreamResponse.message("A"),
                        ChatModelStreamResponse.message("B")
                ));

        mockMvc.perform(post("/api/model/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_NDJSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "routeKey", "chat.default",
                                "messages", List.of(Map.of("role", "USER", "content", "你好")),
                                "options", Map.of()
                        ))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON))
                .andExpect(content().string(containsString("\"content\":\"A\"")))
                .andExpect(content().string(containsString("\"content\":\"B\"")));
    }
}
