package com.nexarag.model.provider;

import com.nexarag.model.client.ChatClientFactory;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.gateway.chat.ChatModelMessage;
import com.nexarag.model.gateway.chat.ChatModelResponse;
import com.nexarag.model.gateway.chat.ChatModelStreamResponse;
import com.nexarag.model.route.ModelRouteDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.UsageCalculator;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Locale;

/**
 * Chat Provider，负责基于 Spring AI OpenAI 兼容协议调用聊天模型。
 */
@Component
@RequiredArgsConstructor
public class ChatProvider implements ModelProviderAdapter {

    private final ChatClientFactory chatClientFactory;

    @Override
    public boolean supports(ModelProvider provider, ModelType modelType) {
        return ModelType.CHAT == modelType && provider.isOpenAiCompatible();
    }

    @Override
    public ChatModelResponse chat(ModelRouteDecision decision, ChatModelRequest request) {
        // 1. 将统一网关消息转换为 Spring AI Prompt
        Prompt prompt = new Prompt(messages(request.messages()));
        ChatResponse response = chatClientFactory.getChatClient(decision).call(prompt);

        // 2. 将 Spring AI 响应转换为模型网关统一响应
        Usage usage = usage(response.getMetadata());
        return ChatModelResponse.builder()
                .content(content(response))
                .modelProfile(decision.profileName())
                .promptTokens(promptTokens(usage))
                .completionTokens(completionTokens(usage))
                .totalTokens(totalTokens(usage))
                .build();
    }

    /**
     * 流式调用聊天模型。
     *
     * @param decision 路由决策
     * @param request  聊天请求
     * @return Chat 模型流式响应分片
     */
    public Flux<ChatModelStreamResponse> streamChat(ModelRouteDecision decision, ChatModelRequest request) {
        // 1. 将统一网关消息转换为 Spring AI Prompt
        Prompt prompt = new Prompt(messages(request.messages()), OpenAiChatOptions.builder()
                .streamUsage(true)
                .build());

        // 2. 将 Spring AI 流式响应转换为模型网关统一分片
        return chatClientFactory.getChatClient(decision)
                .stream(prompt)
                .map(response -> {
                    Usage usage = usage(response.getMetadata());
                    return ChatModelStreamResponse.message(content(response), promptTokens(usage),
                            completionTokens(usage), totalTokens(usage));
                })
                .filter(chunk -> StringUtils.hasText(chunk.content()) || chunk.totalTokens() != null);
    }

    private List<Message> messages(List<ChatModelMessage> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return List.of(new UserMessage("你好"));
        }
        return messages.stream()
                .map(this::message)
                .toList();
    }

    private Message message(ChatModelMessage message) {
        String role = message.role() == null ? "USER" : message.role().toUpperCase(Locale.ROOT);
        return switch (role) {
            case "SYSTEM" -> new SystemMessage(message.content());
            case "ASSISTANT" -> new AssistantMessage(message.content());
            default -> new UserMessage(message.content());
        };
    }

    private String content(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    private Usage usage(ChatResponseMetadata metadata) {
        if (metadata == null || UsageCalculator.isEmpty(metadata.getUsage())) {
            return null;
        }
        return metadata.getUsage();
    }

    private Integer promptTokens(Usage usage) {
        return usage == null ? null : usage.getPromptTokens();
    }

    private Integer completionTokens(Usage usage) {
        return usage == null ? null : usage.getCompletionTokens();
    }

    private Integer totalTokens(Usage usage) {
        return usage == null ? null : usage.getTotalTokens();
    }
}
