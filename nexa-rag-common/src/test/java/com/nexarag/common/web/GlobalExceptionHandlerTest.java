package com.nexarag.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全局异常处理器测试，验证 SSE 客户端断连不再尝试写入统一响应。
 */
class GlobalExceptionHandlerTest {

    @Test
    void shouldIgnoreCommittedSseResponseWhenClientDisconnects() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chat/stream");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCommitted(true);

        assertThat(handler.shouldIgnoreSseClientDisconnect(request, response)).isTrue();
    }

    @Test
    void shouldWriteJsonWhenHttpMediaTypeNotAcceptable() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chat/stream");
        MockHttpServletResponse response = new MockHttpServletResponse();
        org.springframework.web.HttpMediaTypeNotAcceptableException exception =
                new org.springframework.web.HttpMediaTypeNotAcceptableException("No acceptable representation");

        handler.handleHttpMediaTypeNotAcceptableException(request, response, exception);

        assertThat(response.getStatus()).isEqualTo(406);
        assertThat(response.getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("请求指定的媒体类型不受支持");
    }
}
