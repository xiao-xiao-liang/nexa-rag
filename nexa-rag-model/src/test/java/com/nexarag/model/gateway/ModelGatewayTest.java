package com.nexarag.model.gateway;

import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.execution.ModelExecutionCommand;
import com.nexarag.model.execution.ModelExecutionTemplate;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.gateway.chat.ChatModelResponse;
import com.nexarag.model.gateway.chat.ChatModelStreamResponse;
import com.nexarag.model.gateway.embedding.EmbeddingModelRequest;
import com.nexarag.model.gateway.embedding.EmbeddingModelResponse;
import com.nexarag.model.gateway.rerank.RerankCandidate;
import com.nexarag.model.gateway.rerank.RerankModelRequest;
import com.nexarag.model.gateway.rerank.RerankModelResponse;
import com.nexarag.model.provider.ModelProviderDispatcher;
import com.nexarag.model.route.ModelRouteDecision;
import org.junit.jupiter.api.Test;
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
 * 统一模型网关测试。
 */
class ModelGatewayTest {

    @Test
    void embeddingShouldDelegateToDispatcherThroughExecutionTemplate() {
        ModelExecutionTemplate executionTemplate = mock(ModelExecutionTemplate.class);
        ModelProviderDispatcher providerDispatcher = mock(ModelProviderDispatcher.class);
        ModelGateway modelGateway = new ModelGateway(executionTemplate, providerDispatcher);
        ModelRouteDecision decision = routeDecision();
        EmbeddingModelRequest request = EmbeddingModelRequest.builder()
                .traceId("trace-1")
                .bizType(ModelBizType.RETRIEVAL)
                .bizId("document-1")
                .routeKey("embedding")
                .texts(List.of("片段"))
                .build();
        EmbeddingModelResponse expected = new EmbeddingModelResponse(
                List.of(new float[]{0.1f, 0.2f}), "embedding-primary", 10
        );

        when(executionTemplate.execute(any())).thenAnswer(invocation -> {
            ModelExecutionCommand<EmbeddingModelResponse> command = invocation.getArgument(0);
            return command.executor().apply(decision);
        });
        when(providerDispatcher.embedding(decision, request)).thenReturn(expected);

        EmbeddingModelResponse actual = modelGateway.embedding(request);

        assertThat(actual).isSameAs(expected);
        verify(providerDispatcher).embedding(decision, request);
    }

    @Test
    void rerankShouldDelegateToDispatcherThroughExecutionTemplate() {
        ModelExecutionTemplate executionTemplate = mock(ModelExecutionTemplate.class);
        ModelProviderDispatcher providerDispatcher = mock(ModelProviderDispatcher.class);
        ModelGateway modelGateway = new ModelGateway(executionTemplate, providerDispatcher);
        ModelRouteDecision decision = routeDecision();
        RerankCandidate candidate = new RerankCandidate("chunk-1", "片段内容", Map.of("documentId", 1L));
        RerankModelRequest request = RerankModelRequest.builder()
                .traceId("trace-1")
                .bizType(ModelBizType.RERANK)
                .bizId("conversation-1")
                .routeKey("rerank")
                .query("问题")
                .candidates(List.of(candidate))
                .build();
        RerankModelResponse expected = new RerankModelResponse(
                List.of(new RerankModelResponse.RerankScore("chunk-1", 0.9)), "rerank-primary", 20
        );

        when(executionTemplate.execute(any())).thenAnswer(invocation -> {
            ModelExecutionCommand<RerankModelResponse> command = invocation.getArgument(0);
            return command.executor().apply(decision);
        });
        when(providerDispatcher.rerank(decision, request)).thenReturn(expected);

        RerankModelResponse actual = modelGateway.rerank(request);

        assertThat(actual).isSameAs(expected);
        verify(providerDispatcher).rerank(decision, request);
    }

    @Test
    void chatShouldDelegateToDispatcherThroughExecutionTemplate() {
        ModelExecutionTemplate executionTemplate = mock(ModelExecutionTemplate.class);
        ModelProviderDispatcher providerDispatcher = mock(ModelProviderDispatcher.class);
        ModelGateway modelGateway = new ModelGateway(executionTemplate, providerDispatcher);
        ModelRouteDecision decision = routeDecision();
        ChatModelRequest request = ChatModelRequest.builder()
                .traceId("trace-1")
                .bizType(ModelBizType.CHAT)
                .bizId("conversation-1")
                .routeKey("chat")
                .messages(List.of(new ChatModelRequest.ChatMessage("USER", "你好")))
                .options(Map.of())
                .build();
        ChatModelResponse expected = ChatModelResponse.builder()
                .content("你好")
                .modelProfile("chat-primary")
                .promptTokens(1)
                .completionTokens(2)
                .totalTokens(3)
                .build();

        when(executionTemplate.execute(any())).thenAnswer(invocation -> {
            ModelExecutionCommand<ChatModelResponse> command = invocation.getArgument(0);
            return command.executor().apply(decision);
        });
        when(providerDispatcher.chat(decision, request)).thenReturn(expected);

        ChatModelResponse actual = modelGateway.chat(request);

        assertThat(actual).isSameAs(expected);
        verify(providerDispatcher).chat(decision, request);
    }

    @Test
    void streamChatShouldDelegateToDispatcherThroughExecutionTemplate() {
        ModelExecutionTemplate executionTemplate = mock(ModelExecutionTemplate.class);
        ModelProviderDispatcher providerDispatcher = mock(ModelProviderDispatcher.class);
        ModelGateway modelGateway = new ModelGateway(executionTemplate, providerDispatcher);
        ModelRouteDecision decision = routeDecision();
        ChatModelRequest request = ChatModelRequest.builder()
                .traceId("trace-1")
                .bizType(ModelBizType.CHAT)
                .bizId("conversation-1")
                .routeKey("chat")
                .messages(List.of(new ChatModelRequest.ChatMessage("USER", "你好")))
                .options(Map.of())
                .build();

        when(executionTemplate.executeStream(any())).thenAnswer(invocation -> {
            ModelExecutionCommand<Flux<ChatModelStreamResponse>> command = invocation.getArgument(0);
            return command.executor().apply(decision);
        });
        when(providerDispatcher.streamChat(decision, request)).thenReturn(Flux.just(
                ChatModelStreamResponse.message("你"),
                ChatModelStreamResponse.message("好")
        ));

        StepVerifier.create(modelGateway.streamChat(request))
                .expectNextMatches(chunk -> "你".equals(chunk.content()))
                .expectNextMatches(chunk -> "好".equals(chunk.content()))
                .verifyComplete();
        verify(providerDispatcher).streamChat(decision, request);
    }

    private ModelRouteDecision routeDecision() {
        ModelProfileProperties profile = new ModelProfileProperties();
        profile.setProvider("OPENAI");
        profile.setBaseUrl("https://api.openai.com/v1");
        profile.setModelName("text-embedding-3-small");
        return new ModelRouteDecision("embedding-primary", profile, false);
    }
}
