package com.nexarag.model.provider;

import com.nexarag.model.client.ChatClientFactory;
import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.gateway.chat.ChatModelMessage;
import com.nexarag.model.gateway.chat.ChatModelStreamResponse;
import com.nexarag.model.route.ModelRouteDecision;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Chat Provider 流式调用测试。
 */
class ChatProviderStreamTest {

    @Test
    void streamChatShouldReturnContentChunks() {
        ChatClientFactory factory = mock(ChatClientFactory.class);
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        when(factory.getChatClient(any())).thenReturn(chatModel);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("你")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("好"))))
        ));

        ChatProvider provider = new ChatProvider(factory);

        StepVerifier.create(provider.streamChat(decision(), request()))
                .expectNextMatches(chunk -> "你".equals(chunk.content()))
                .expectNextMatches(chunk -> "好".equals(chunk.content()))
                .verifyComplete();
    }

    private ChatModelRequest request() {
        return ChatModelRequest.builder()
                .traceId("trace-1")
                .bizType(ModelBizType.CHAT)
                .bizId("chat.default")
                .routeKey("chat.default")
                .messages(List.of(new ChatModelMessage("USER", "你好")))
                .options(Map.of())
                .build();
    }

    private ModelRouteDecision decision() {
        ModelProfileProperties profile = ModelProfileProperties.builder()
                .provider("OLLAMA")
                .baseUrl("http://localhost:11434/v1")
                .endpointPath("/chat/completions")
                .modelName("qwen2.5:7b")
                .build();
        return new ModelRouteDecision("chat-primary", profile, false);
    }
}
