package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.GraphFlux;
import com.nexarag.chat.domain.ChatCitationSetCodec;
import com.nexarag.chat.domain.ChatCitationSetDTO;
import com.nexarag.chat.domain.ChatCitationSummaryVO;
import com.nexarag.chat.domain.ConversationContext;
import com.nexarag.chat.service.ConversationMessageService;
import com.nexarag.infra.observability.langfuse.otel.LangfuseOtelContextCodec;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.execution.telemetry.RagTokenBreakdown;
import com.nexarag.model.execution.telemetry.RagTokenBreakdownStatus;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.chat.ChatModelMessage;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.prompt.domain.PromptExecutionSnapshot;
import com.nexarag.model.toolkits.prompt.AnswerPromptComposition;
import com.nexarag.model.toolkits.prompt.PromptBuilder;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.workflow.citation.CitationSetFactory;
import com.nexarag.workflow.service.ModelInputEvidenceSelector;
import com.nexarag.workflow.stream.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static com.nexarag.chat.constants.ChatModelRouteConstants.CHAT_ANSWER_ROUTE_KEY;
import static com.nexarag.workflow.constants.ChatWorkflowNodeConstants.ANSWER_GENERATION_NODE;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.*;
import static com.nexarag.workflow.constants.ChatWorkflowTelemetryConstant.ANSWER_GENERATION_NAME;

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
    private final ConversationMessageService messageService;
    private final ChatCitationSetCodec citationSetCodec;
    private final ModelInputEvidenceSelector modelInputEvidenceSelector;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String conversationId = state.value(CONVERSATION_ID, "");
        String generationId = state.value(GENERATION_ID, "");
        ChatGenerationAccumulator accumulator = state.value(GENERATION_ACCUMULATOR,
                new ChatGenerationAccumulator());

        // 1. 先按当前模型路由的输入窗口选择完整证据；超窗父片段仅回退直接 Rerank 命中子片段。
        List<RetrievalChunk> acceptedChunks = state.value(ACCEPTED_EVIDENCE_RESULTS, List.of());
        List<RetrievalChunk> directHitChunks = state.value(PARENT_CONTEXT_FALLBACK_RESULTS, List.of());
        List<String> toolFailureSummaries = state.value(TOOL_FAILURE_SUMMARIES, List.of());
        int staticPromptTokens = promptTokenCount(promptBuilder.buildAnswerMessages(snapshot(state),
                state.value(REWRITTEN_QUESTION, ""), summary(state), historyMessages(state),
                evidence(List.of(), emptyCitationSet(), toolFailureSummaries)));
        List<RetrievalChunk> chunks = modelInputEvidenceSelector.select(acceptedChunks, directHitChunks,
                staticPromptTokens, CHAT_ANSWER_ROUTE_KEY);

        // 2. 固定引用编号并发布公开摘要，确保正文首个分片前客户端已经拿到编号。
        ChatCitationSetDTO citationSet = new ChatCitationSetDTO(ChatCitationSetDTO.CURRENT_VERSION,
                citationSetFactory.create(chunks));
        String assistantMessageId = state.value(ASSISTANT_MESSAGE_ID, "");
        String referencesJson = citationSetCodec.encode(citationSet);
        accumulator.recordReferencesJson(referencesJson);
        messageService.updateGeneratingAssistantReferences(assistantMessageId, referencesJson);
        eventPublisher.publish(new ChatStreamEvent(ChatStreamEventType.CITATIONS, null, conversationId,
                state.value(TRACE_ID, ""), generationId, assistantMessageId, null, null,
                0L, List.of(), citationSet.citations().stream()
                .map(citation -> new ChatCitationSummaryVO(citation.citationId()))
                .toList()));

        // 3. 调用最终回答模型并绑定取消句柄
        log.info("准备调用模型生成回答，traceId={}，已接纳正文数={}", state.value(TRACE_ID, ""), chunks.size());
        String question = state.value(REWRITTEN_QUESTION, "");
        String summary = summary(state);
        List<ChatModelMessage> historyMessages = historyMessages(state);
        String retrievalContent = retrievalEvidence(chunks, citationSet);
        String toolContent = toolEvidence(toolFailureSummaries);
        AnswerPromptComposition promptComposition = promptBuilder.buildAnswerPrompt(snapshot(state), question, summary,
                historyMessages, retrievalContent, toolContent);
        List<ChatModelMessage> finalMessages = promptComposition.messages();
        RagTokenBreakdown observabilityContext = new RagTokenBreakdown(
                promptComposition.systemInstruction(),
                promptComposition.summary(),
                promptComposition.historyMessages(),
                promptComposition.question(),
                promptComposition.retrievalContent(),
                promptComposition.toolContent(),
                modelInputEvidenceSelector.availableInputTokens(CHAT_ANSWER_ROUTE_KEY),
                acceptedChunks.size(),
                chunks.size(),
                Math.max(0, acceptedChunks.size() - chunks.size()),
                null, null, null, null, null, null, null,
                RagTokenBreakdownStatus.UNAVAILABLE);
        Flux<com.nexarag.model.gateway.chat.ChatModelStreamResponse> modelStream = modelGateway.streamChat(
                        ChatModelRequest.builder()
                                .traceId(state.value(TRACE_ID, ""))
                                .bizType(ModelBizType.CHAT)
                                .bizId(conversationId)
                                .routeKey(CHAT_ANSWER_ROUTE_KEY)
                                .messages(finalMessages)
                                .observabilityContext(observabilityContext)
                                .generationId(generationId)
                                .observationName(ANSWER_GENERATION_NAME)
                                .langfuseContext(LangfuseOtelContextCodec.decode(
                                        state.value(LANGFUSE_OTEL_CONTEXT_CARRIER, "")))
                                .build())
                .doOnSubscribe(subscription -> taskManager.bind(generationId, subscription::cancel));

        // 4. 返回 GraphFlux，使 Graph 在流结束后继续执行持久化节点
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
        return appendToolEvidence(retrievalEvidence(chunks, citationSet), toolEvidence(toolFailureSummaries));
    }

    private String retrievalEvidence(List<RetrievalChunk> chunks, ChatCitationSetDTO citationSet) {
        return java.util.stream.IntStream.range(0, chunks.size())
                .mapToObj(index -> "【证据 " + citationSet.citations().get(index).citationId() + "】 "
                        + chunks.get(index).content())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private String toolEvidence(List<String> toolFailureSummaries) {
        return toolFailureSummaries == null || toolFailureSummaries.isEmpty()
                ? "" : String.join("；", toolFailureSummaries);
    }

    private String appendToolEvidence(String evidence, String toolContent) {
        if (toolContent == null || toolContent.isEmpty()) {
            return evidence;
        }
        return evidence + "\n\n工具执行状态：" + toolContent;
    }

    private ChatCitationSetDTO emptyCitationSet() {
        return new ChatCitationSetDTO(ChatCitationSetDTO.CURRENT_VERSION, List.of());
    }

    private int promptTokenCount(List<ChatModelMessage> messages) {
        return messages.stream().map(ChatModelMessage::content)
                .mapToInt(modelInputEvidenceSelector::estimateTokens)
                .sum();
    }
}
