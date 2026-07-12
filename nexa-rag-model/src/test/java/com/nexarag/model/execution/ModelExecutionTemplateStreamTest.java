package com.nexarag.model.execution;

import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.entity.ModelCallLog;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.enums.ModelRequestType;
import com.nexarag.model.enums.TokenUsageSource;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.gateway.chat.ChatModelMessage;
import com.nexarag.model.gateway.chat.ChatModelStreamResponse;
import com.nexarag.model.route.ModelRouteContext;
import com.nexarag.model.route.ModelRouteDecision;
import com.nexarag.model.route.ModelRoutePlan;
import com.nexarag.model.route.ModelRouter;
import com.nexarag.model.service.ModelCallLogService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模型执行模板流式调用测试。
 */
class ModelExecutionTemplateStreamTest {

    @Test
    void streamShouldFallbackBeforeFirstChunk() {
        ModelRouter router = mock(ModelRouter.class);
        ModelCallLogService logService = mock(ModelCallLogService.class);
        when(router.plan(any(ModelRouteContext.class))).thenReturn(new ModelRoutePlan("chat", null,
                List.of(decision("primary"), decision("backup"))));
        when(logService.createRunningLog(nullable(String.class), eq(ModelBizType.CHAT), nullable(String.class),
                nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class),
                any(ModelRequestType.class), any(), nullable(String.class), nullable(String.class)))
                .thenReturn(ModelCallLog.builder().callId("call-1").build())
                .thenReturn(ModelCallLog.builder().callId("call-2").build());
        ModelExecutionTemplate template = new ModelExecutionTemplate(router, logService);

        ModelExecutionCommand<Flux<ChatModelStreamResponse>> command = streamCommand(currentDecision -> {
            if ("primary".equals(currentDecision.profileName())) {
                return Flux.error(new RuntimeException("主模型连接失败"));
            }
            return Flux.just(ChatModelStreamResponse.message("hello"));
        });

        StepVerifier.create(template.executeStream(command))
                .expectNextMatches(chunk -> "hello".equals(chunk.content()))
                .verifyComplete();

        verify(logService).markFailed(eq("call-1"), anyString(), contains("主模型连接失败"), anyLong());
        verify(logService).markStreamSuccess(eq("call-2"), eq(0), eq(0), eq(0), any(TokenUsageSource.class),
                any(), any(), any(), any(), anyLong());
    }

    @Test
    void streamShouldNotFallbackAfterFirstChunk() {
        ModelRouter router = mock(ModelRouter.class);
        ModelCallLogService logService = mock(ModelCallLogService.class);
        when(router.plan(any(ModelRouteContext.class))).thenReturn(new ModelRoutePlan("chat", null,
                List.of(decision("primary"), decision("backup"))));
        when(logService.createRunningLog(nullable(String.class), eq(ModelBizType.CHAT), nullable(String.class),
                nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class),
                any(ModelRequestType.class), any(), nullable(String.class), nullable(String.class)))
                .thenReturn(ModelCallLog.builder().callId("call-1").build());
        ModelExecutionTemplate template = new ModelExecutionTemplate(router, logService);
        ModelExecutionCommand<Flux<ChatModelStreamResponse>> command = streamCommand(currentDecision ->
                Flux.just(ChatModelStreamResponse.message("partial"))
                        .concatWith(Flux.error(new RuntimeException("输出后失败"))));

        StepVerifier.create(template.executeStream(command))
                .expectNextMatches(chunk -> "partial".equals(chunk.content()))
                .expectErrorMessage("输出后失败")
                .verify();

        verify(logService, never()).createRunningLog(nullable(String.class), eq(ModelBizType.CHAT),
                nullable(String.class), eq("backup"), nullable(String.class), nullable(String.class),
                nullable(String.class), any(ModelRequestType.class), any(), nullable(String.class),
                nullable(String.class));
        verify(logService).markFailed(eq("call-1"), anyString(), contains("输出后失败"), anyLong());
    }

    @Test
    void streamShouldRecordProviderUsageWhenUsageChunkExists() {
        ModelRouter router = mock(ModelRouter.class);
        ModelCallLogService logService = mock(ModelCallLogService.class);
        when(router.plan(any(ModelRouteContext.class))).thenReturn(new ModelRoutePlan("chat", null,
                List.of(decision("primary"))));
        when(logService.createRunningLog(nullable(String.class), eq(ModelBizType.CHAT), nullable(String.class),
                nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class),
                any(ModelRequestType.class), any(), nullable(String.class), nullable(String.class)))
                .thenReturn(ModelCallLog.builder().callId("call-1").build());
        ModelExecutionTemplate template = new ModelExecutionTemplate(router, logService);
        ModelExecutionCommand<Flux<ChatModelStreamResponse>> command = streamCommand(currentDecision ->
                Flux.just(
                        ChatModelStreamResponse.message("hello"),
                        ChatModelStreamResponse.message(null, 11, 22, 33)
                ));

        StepVerifier.create(template.executeStream(command))
                .expectNextMatches(chunk -> "hello".equals(chunk.content()))
                .verifyComplete();

        verify(logService).markStreamSuccess(eq("call-1"), eq(11), eq(22), eq(33),
                eq(TokenUsageSource.PROVIDER_USAGE), any(), eq(1), eq(5), eq(0), anyLong());
    }

    private ModelExecutionCommand<Flux<ChatModelStreamResponse>> streamCommand(
            Function<ModelRouteDecision, Flux<ChatModelStreamResponse>> executor) {
        ChatModelRequest request = ChatModelRequest.builder()
                .traceId("trace-1")
                .bizType(ModelBizType.CHAT)
                .bizId("conversation-1")
                .routeKey("chat")
                .messages(List.of(new ChatModelMessage("USER", "你好")))
                .options(Map.of())
                .build();
        return ModelExecutionCommand.ofChatStream(request, executor);
    }

    private ModelRouteDecision decision(String profileName) {
        ModelProfileProperties profile = ModelProfileProperties.builder()
                .provider("OPENAI")
                .baseUrl("https://api.openai.com/v1")
                .modelName("gpt-4.1-mini")
                .build();
        return new ModelRouteDecision(profileName, profile, false);
    }
}
