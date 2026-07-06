package com.nexarag.model.controller;

import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import com.nexarag.model.dto.ModelChatRequest;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.gateway.chat.ChatModelResponse;
import com.nexarag.model.gateway.chat.ChatModelStreamResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

import reactor.core.publisher.Flux;

/**
 * 模型 Chat Controller，提供模型模块裸 Chat 调用入口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/model/chat")
public class ModelChatController {

    private final ModelGateway modelGateway;

    /**
     * 执行同步 Chat 调用。
     *
     * @param request 裸 Chat 请求
     * @return Chat 模型响应
     */
    @PostMapping
    public Result<ChatModelResponse> chat(@RequestBody ModelChatRequest request) {
        // 1. 委托统一模型网关执行 Chat 调用
        return Results.success(modelGateway.chat(toGatewayRequest(request)));
    }

    /**
     * 执行流式 Chat 调用。
     *
     * @param request 裸 Chat 请求
     * @return Chat 模型流式响应分片
     */
    @PostMapping(value = "/stream", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<ChatModelStreamResponse> streamChat(@RequestBody ModelChatRequest request) {
        // 1. 委托统一模型网关执行流式 Chat 调用
        return modelGateway.streamChat(toGatewayRequest(request));
    }

    private ChatModelRequest toGatewayRequest(ModelChatRequest request) {
        // 1. 将 HTTP 请求转换为统一模型网关请求
        return ChatModelRequest.builder()
                .traceId(UUID.randomUUID().toString())
                .bizType(ModelBizType.CHAT)
                .bizId(request.routeKey())
                .routeKey(request.routeKey())
                .messages(request.messages())
                .options(request.options() == null ? Map.of() : request.options())
                .build();
    }
}
