package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.GraphFlux;
import com.nexarag.chat.domain.ChatCitationSetDTO;
import com.nexarag.chat.domain.ChatCitationSummaryVO;
import com.nexarag.chat.domain.ConversationContext;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.gateway.chat.ChatModelMessage;
import com.nexarag.model.toolkits.prompt.PromptBuilder;
import com.nexarag.model.prompt.domain.PromptExecutionSnapshot;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.workflow.stream.ChatGenerationAccumulator;
import com.nexarag.workflow.stream.ChatGenerationTaskManager;
import com.nexarag.workflow.stream.ChatGenerationEventPublisher;
import com.nexarag.workflow.stream.ChatWorkflowStreamingUtil;
import com.nexarag.workflow.stream.ChatStreamEvent;
import com.nexarag.workflow.stream.ChatStreamEventType;
import com.nexarag.workflow.citation.CitationSetFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.GENERATION_ACCUMULATOR;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.MODEL_STREAM_RESULT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.ACCEPTED_EVIDENCE_RESULTS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CITATION_SET;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.REWRITTEN_QUESTION;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TRACE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.PROMPT_EXECUTION_SNAPSHOT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TOOL_FAILURE_SUMMARIES;
import static com.nexarag.chat.constants.ChatModelRouteConstants.CHAT_ANSWER_ROUTE_KEY;

/**
 * 回答生成节点，负责创建助手消息占位并返回 Graph 可识别的模型流。
 */
@Component
@ConditionalOnProperty(prefix = "nexa.chat", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class AnswerGenerationNode implements NodeAction {

    private final ModelGateway modelGateway;
    private final PromptBuilder promptBuilder;
    private final ChatGenerationTaskManager taskManager;
    private final ChatGenerationEventPublisher eventPublisher;
    private final CitationSetFactory citationSetFactory;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String conversationId = state.value(CONVERSATION_ID, "");
        String generationId = state.value(GENERATION_ID, "");
        ChatGenerationAccumulator accumulator = state.value(GENERATION_ACCUMULATOR,
                new ChatGenerationAccumulator());

        // 1. 先固定引用编号并发布公开摘要，确保正文首个分片前客户端已经拿到编号。
        List<RetrievalChunk> chunks = state.value(ACCEPTED_EVIDENCE_RESULTS, List.of());
        ChatCitationSetDTO citationSet = new ChatCitationSetDTO(ChatCitationSetDTO.CURRENT_VERSION,
                citationSetFactory.create(chunks));
        eventPublisher.publish(new ChatStreamEvent(ChatStreamEventType.CITATIONS, null, conversationId,
                state.value(TRACE_ID, ""), generationId, state.value(ASSISTANT_MESSAGE_ID, ""), null, null,
                0L, List.of(), citationSet.citations().stream()
                .map(citation -> new ChatCitationSummaryVO(citation.citationId()))
                .toList()));

        // 2. 调用最终回答模型并绑定取消句柄
        log.info("准备调用模型生成回答，traceId={}，已接纳正文数={}", state.value(TRACE_ID, ""), chunks.size());
        Flux<com.nexarag.model.gateway.chat.ChatModelStreamResponse> modelStream = modelGateway.streamChat(
                ChatModelRequest.builder()
                        .traceId(state.value(TRACE_ID, ""))
                        .bizType(ModelBizType.CHAT)
                        .bizId(conversationId)
                        .routeKey(CHAT_ANSWER_ROUTE_KEY)
                        .messages(promptBuilder.buildAnswerMessages(snapshot(state),
                                state.value(REWRITTEN_QUESTION, ""), summary(state), historyMessages(state),
                                evidence(chunks, citationSet, state.value(TOOL_FAILURE_SUMMARIES, List.of()))))
                        .build())
                .doOnSubscribe(subscription -> taskManager.bind(generationId, subscription::cancel));

        // 3. 返回 GraphFlux，使 Graph 在流结束后继续执行持久化节点
        GraphFlux<?> graphFlux = GraphFlux.of(ANSWER_GENERATION_NODE, MODEL_STREAM_RESULT,
                ChatWorkflowStreamingUtil.toGraphStream(AnswerGenerationNode.class, state, modelStream, accumulator,
                        eventPublisher::publish));
        return Map.of(MODEL_STREAM_RESULT, graphFlux, CITATION_SET, citationSet);
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

    private String evidence(List<RetrievalChunk> chunks, ChatCitationSetDTO citationSet,
                            List<String> toolFailureSummaries) {
        String evidence = java.util.stream.IntStream.range(0, chunks.size())
                .mapToObj(index -> "【证据 " + citationSet.citations().get(index).citationId() + "】 "
                        + chunks.get(index).content())
                .collect(java.util.stream.Collectors.joining("\n"));
        if (toolFailureSummaries.isEmpty()) {
            return evidence;
        }
        return evidence + "\n\n工具执行状态：" + String.join("；", toolFailureSummaries);
    }
}
