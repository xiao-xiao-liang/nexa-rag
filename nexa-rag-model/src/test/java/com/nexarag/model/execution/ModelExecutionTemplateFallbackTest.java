package com.nexarag.model.execution;

import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.entity.ModelCallLog;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.enums.ModelRequestType;
import com.nexarag.model.enums.ModelRouteStrategy;
import com.nexarag.model.enums.TokenUsageSource;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.gateway.chat.ChatModelResponse;
import com.nexarag.model.route.ModelRouteContext;
import com.nexarag.model.route.ModelRouteDecision;
import com.nexarag.model.route.ModelRoutePlan;
import com.nexarag.model.route.ModelRouter;
import com.nexarag.model.service.ModelCallLogService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模型执行模板 fallback 测试。
 */
class ModelExecutionTemplateFallbackTest {

    @Test
    void executeShouldTryBackupCandidateWhenPrimaryFails() {
        ModelRouter modelRouter = mock(ModelRouter.class);
        ModelCallLogService modelCallLogService = mock(ModelCallLogService.class);
        ModelExecutionTemplate template = new ModelExecutionTemplate(modelRouter, modelCallLogService);
        AtomicInteger calls = new AtomicInteger();
        ModelRouteDecision primary = decision("chat-primary");
        ModelRouteDecision backup = decision("chat-backup");
        ChatModelRequest request = request();

        when(modelRouter.plan(any(ModelRouteContext.class))).thenReturn(new ModelRoutePlan(
                "chat.default", ModelRouteStrategy.PRIMARY_BACKUP, List.of(primary, backup)
        ));
        when(modelCallLogService.createRunningLog(nullable(String.class), eq(ModelBizType.CHAT),
                nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class), any(ModelRequestType.class), any(Integer.class), nullable(String.class),
                nullable(String.class))).thenAnswer(invocation -> ModelCallLog.builder()
                        .callId(invocation.getArgument(3))
                        .build());

        ChatModelResponse response = template.execute(ModelExecutionCommand.ofChat(request, decision -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("主模型失败");
            }
            return ChatModelResponse.builder()
                    .content("备用模型成功")
                    .modelProfile(decision.profileName())
                    .promptTokens(1)
                    .completionTokens(2)
                    .totalTokens(3)
                    .build();
        }));

        assertThat(response.content()).isEqualTo("备用模型成功");
        assertThat(response.modelProfile()).isEqualTo("chat-backup");
        assertThat(calls).hasValue(2);
        verify(modelCallLogService).markFailed(eq("chat-primary"), eq("IllegalStateException"),
                eq("主模型失败"), anyLong());
        verify(modelCallLogService).markSuccess(eq("chat-backup"), eq(1), eq(2), eq(3),
                eq(TokenUsageSource.PROVIDER_USAGE), anyLong());
    }

    private ChatModelRequest request() {
        return ChatModelRequest.builder()
                .traceId("trace-1")
                .bizType(ModelBizType.CHAT)
                .bizId("conversation-1")
                .routeKey("chat.default")
                .messages(List.of(new ChatModelRequest.ChatMessage("USER", "你好")))
                .options(Map.of())
                .build();
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
