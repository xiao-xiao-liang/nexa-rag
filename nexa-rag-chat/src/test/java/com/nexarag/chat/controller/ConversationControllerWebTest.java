package com.nexarag.chat.controller;

import com.nexarag.chat.service.ConversationMessageService;
import com.nexarag.chat.service.ConversationService;
import com.nexarag.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会话查询接口的 MVC 参数绑定测试。
 */
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@ExtendWith(MockitoExtension.class)
class ConversationControllerWebTest {

    @Mock
    private ConversationService conversationService;
    @Mock
    private ConversationMessageService messageService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ConversationController(conversationService, messageService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnParamErrorWhenConversationPageParameterIsNotNumeric() throws Exception {
        mockMvc.perform(get("/api/conversations").param("current", "不是数字"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("A000002"));
    }
}
