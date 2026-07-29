package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.GraphFlux;
import com.nexarag.chat.domain.ChatMessageVO;
import com.nexarag.chat.domain.ConversationContext;
import com.nexarag.chat.service.ConversationMessageService;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.gateway.chat.ChatModelMessage;
import com.nexarag.model.toolkits.prompt.PromptBuilder;
import com.nexarag.model.prompt.domain.PromptExecutionSnapshot;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.workflow.stream.ChatGenerationAccumulator;
import com.nexarag.workflow.stream.ChatGenerationTaskManager;
import com.nexarag.workflow.stream.ChatWorkflowStreamingUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowNodeConstants.ANSWER_GENERATION_NODE;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.ASSISTANT_MESSAGE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CONVERSATION_CONTEXT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CONVERSATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.GENERATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.MODEL_STREAM_RESULT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RERANKED_RETRIEVAL_RESULTS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.REWRITTEN_QUESTION;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TRACE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.PROMPT_EXECUTION_SNAPSHOT;
import static com.nexarag.chat.constants.ChatModelRouteConstants.CHAT_ANSWER_ROUTE_KEY;

/**
 * 回答生成节点，负责创建助手消息占位并返回 Graph 可识别的模型流。
 */
@Component
@ConditionalOnProperty(prefix = "nexa.chat", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AnswerGenerationNode implements NodeAction {

    private final ConversationMessageService messageService;
    private final ModelGateway modelGateway;
    private final PromptBuilder promptBuilder;
    private final ChatGenerationTaskManager taskManager;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String conversationId = state.value(CONVERSATION_ID, "");
        String userId = state.value(USER_ID, "");
        String generationId = state.value(GENERATION_ID, "");
        // 1. 创建生成中的助手消息占位
        ChatMessageVO assistantMessage = messageService.startAssistantMessage(conversationId, userId);
        ChatGenerationAccumulator accumulator = new ChatGenerationAccumulator();
        taskManager.register(generationId, userId, conversationId, accumulator,
                () -> messageService.cancelAssistantMessage(
                        assistantMessage.messageId(), accumulator.snapshot().content()));

        // 2. 调用最终回答模型并绑定取消句柄
        List<RetrievalChunk> chunks = state.value(RERANKED_RETRIEVAL_RESULTS, List.of());
        Flux<com.nexarag.model.gateway.chat.ChatModelStreamResponse> modelStream = modelGateway.streamChat(
                ChatModelRequest.builder()
                        .traceId(state.value(TRACE_ID, ""))
                        .bizType(ModelBizType.CHAT)
                        .bizId(conversationId)
                        .routeKey(CHAT_ANSWER_ROUTE_KEY)
                        .messages(promptBuilder.buildAnswerMessages(snapshot(state),
                                state.value(REWRITTEN_QUESTION, ""), summary(state), historyMessages(state), evidence(chunks)))
                        .build())
                .doOnSubscribe(subscription -> taskManager.bind(generationId, subscription::cancel));

        // 3. 返回 GraphFlux，使 Graph 在流结束后继续执行持久化节点
        GraphFlux<?> graphFlux = GraphFlux.of(ANSWER_GENERATION_NODE, MODEL_STREAM_RESULT,
                ChatWorkflowStreamingUtil.toGraphStream(AnswerGenerationNode.class, state, modelStream, accumulator));
        return Map.of(ASSISTANT_MESSAGE_ID, assistantMessage.messageId(), MODEL_STREAM_RESULT, graphFlux);
    }

    private PromptExecutionSnapshot snapshot(OverAllState state) {
        return state.value(PROMPT_EXECUTION_SNAPSHOT, (PromptExecutionSnapshot) null);
    }

    private String summary(OverAllState state) {
        ConversationContext context = state.value(CONVERSATION_CONTEXT, (ConversationContext) null);
        return context == null || context.summary() == null ? "" : context.summary();
    }

    private List<ChatModelMessage> historyMessages(OverAllState state) {
        ConversationContext context = state.value(CONVERSATION_CONTEXT, (ConversationContext) null);
        if (context == null) {
            return List.of();
        }
        return context.recentMessages().stream()
                .filter(com.nexarag.chat.domain.ChatMessageVO::usableForContext)
                .map(message -> new ChatModelMessage(message.role().name(), message.content()))
                .toList();
    }

    private String evidence(List<RetrievalChunk> chunks) {
        return chunks.stream()
                .map(chunk -> "[" + chunk.chunkId() + "] " + chunk.content())
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
