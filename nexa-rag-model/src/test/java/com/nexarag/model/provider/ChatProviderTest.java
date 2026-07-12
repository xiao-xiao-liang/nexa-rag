package com.nexarag.model.provider;

import com.nexarag.model.client.ChatClientFactory;
import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.gateway.chat.ChatModelMessage;
import com.nexarag.model.gateway.chat.ChatModelResponse;
import com.nexarag.model.route.ModelRouteDecision;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Chat Provider 测试。
 */
class ChatProviderTest {

    @Test
    void chatShouldMapSpringAiResponse() {
        ChatClientFactory chatClientFactory = mock(ChatClientFactory.class);
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        ChatProvider provider = new ChatProvider(chatClientFactory);
        ModelRouteDecision decision = routeDecision();
        ChatModelRequest request = ChatModelRequest.builder()
                .traceId("trace-1")
                .bizType(ModelBizType.CHAT)
                .bizId("conversation-1")
                .routeKey("chat")
                .messages(List.of(new ChatModelMessage("USER", "你好")))
                .options(Map.of())
                .build();

        when(chatClientFactory.getChatClient(decision)).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse());

        ChatModelResponse response = provider.chat(decision, request);

        assertThat(response.content()).isEqualTo("连接正常");
        assertThat(response.modelProfile()).isEqualTo("chat-primary");
        assertThat(response.promptTokens()).isEqualTo(1);
        assertThat(response.completionTokens()).isEqualTo(2);
        assertThat(response.totalTokens()).isEqualTo(3);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getInstructions()).hasSize(1);
    }

    @Test
    void chatShouldKeepTokenUsageNullWhenProviderDoesNotReturnUsage() {
        ChatClientFactory chatClientFactory = mock(ChatClientFactory.class);
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        ChatProvider provider = new ChatProvider(chatClientFactory);
        ModelRouteDecision decision = routeDecision();
        ChatModelRequest request = ChatModelRequest.builder()
                .traceId("trace-1")
                .bizType(ModelBizType.CHAT)
                .bizId("conversation-1")
                .routeKey("chat")
                .messages(List.of(new ChatModelMessage("USER", "你好")))
                .options(Map.of())
                .build();

        when(chatClientFactory.getChatClient(decision)).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponseWithoutUsage());

        ChatModelResponse response = provider.chat(decision, request);

        assertThat(response.promptTokens()).isNull();
        assertThat(response.completionTokens()).isNull();
        assertThat(response.totalTokens()).isNull();
    }

    @Test
    void streamChatShouldMapSpringAiUsage() {
        ChatClientFactory chatClientFactory = mock(ChatClientFactory.class);
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        ChatProvider provider = new ChatProvider(chatClientFactory);
        ModelRouteDecision decision = routeDecision();
        ChatModelRequest request = ChatModelRequest.builder()
                .traceId("trace-1")
                .bizType(ModelBizType.CHAT)
                .bizId("conversation-1")
                .routeKey("chat")
                .messages(List.of(new ChatModelMessage("USER", "你好")))
                .options(Map.of())
                .build();

        when(chatClientFactory.getChatClient(decision)).thenReturn(chatModel);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(chatResponse()));

        StepVerifier.create(provider.streamChat(decision, request))
                .expectNextMatches(response -> "连接正常".equals(response.content())
                        && Integer.valueOf(1).equals(response.promptTokens())
                        && Integer.valueOf(2).equals(response.completionTokens())
                        && Integer.valueOf(3).equals(response.totalTokens()))
                .verifyComplete();
    }

    @Test
    void streamChatShouldMapUsageOnlyChunkWithoutContent() {
        ChatClientFactory chatClientFactory = mock(ChatClientFactory.class);
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        ChatProvider provider = new ChatProvider(chatClientFactory);
        ModelRouteDecision decision = routeDecision();
        ChatModelRequest request = ChatModelRequest.builder()
                .traceId("trace-1")
                .bizType(ModelBizType.CHAT)
                .bizId("conversation-1")
                .routeKey("chat")
                .messages(List.of(new ChatModelMessage("USER", "你好")))
                .options(Map.of())
                .build();

        when(chatClientFactory.getChatClient(decision)).thenReturn(chatModel);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(chatUsageOnlyResponse()));

        StepVerifier.create(provider.streamChat(decision, request))
                .expectNextMatches(response -> !StringUtils.hasText(response.content())
                        && Integer.valueOf(1).equals(response.promptTokens())
                        && Integer.valueOf(2).equals(response.completionTokens())
                        && Integer.valueOf(3).equals(response.totalTokens()))
                .verifyComplete();
    }

    @Test
    void shouldSupportOpenAiCompatibleChatProviders() {
        ChatProvider provider = new ChatProvider(mock(ChatClientFactory.class));

        assertThat(provider.supports(ModelProvider.OPENAI, ModelType.CHAT)).isTrue();
        assertThat(provider.supports(ModelProvider.OLLAMA, ModelType.CHAT)).isTrue();
        assertThat(provider.supports(ModelProvider.DASHSCOPE, ModelType.CHAT)).isTrue();
        assertThat(provider.supports(ModelProvider.OPENAI, ModelType.EMBEDDING)).isFalse();
    }

    private ChatResponse chatResponse() {
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .usage(usage())
                .build();
        return new ChatResponse(List.of(new Generation(new AssistantMessage("连接正常"))), metadata);
    }

    private ChatResponse chatResponseWithoutUsage() {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("连接正常"))));
    }

    private ChatResponse chatUsageOnlyResponse() {
        return new ChatResponse(List.of(), ChatResponseMetadata.builder()
                .usage(usage())
                .build());
    }

    private Usage usage() {
        return new Usage() {
            @Override
            public Integer getPromptTokens() {
                return 1;
            }

            @Override
            public Integer getCompletionTokens() {
                return 2;
            }

            @Override
            public Object getNativeUsage() {
                return null;
            }
        };
    }

    private ModelRouteDecision routeDecision() {
        ModelProfileProperties profile = new ModelProfileProperties();
        profile.setProvider("OPENAI");
        profile.setBaseUrl("https://api.openai.com/v1");
        profile.setModelName("gpt-4o-mini");
        return new ModelRouteDecision("chat-primary", profile, false);
    }
}
