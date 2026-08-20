package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.retrieval.service.ConversationRetrievalService;
import com.nexarag.retrieval.dto.req.ConversationRetrievalRequest;
import com.nexarag.retrieval.dto.res.IntentRecognitionResult;
import com.nexarag.retrieval.enums.RetrievalScope;
import com.nexarag.workflow.stream.ChatGenerationAccumulator;
import com.nexarag.workflow.stream.ChatGenerationEventPublisher;
import com.nexarag.workflow.stream.ChatStreamEvent;
import com.nexarag.workflow.stream.ChatStreamEventType;
import com.nexarag.workflow.stream.ChatToolOperationDTO;
import com.nexarag.workflow.stream.ChatToolOperationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.INTENT_RESULT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.GENERATION_ACCUMULATOR;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.GENERATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CONVERSATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.ASSISTANT_MESSAGE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RAW_RETRIEVAL_RESULTS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_ROUND;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_SCOPE;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_TOP_K;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_VECTOR_THRESHOLD;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_KNOWLEDGE_BASE_IDS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.REWRITTEN_QUESTION;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TRACE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TOOL_FAILURE_SUMMARIES;

/**
 * 对话检索节点，负责按当前轮次参数调用混合检索服务。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RetrievalNode implements NodeAction {
    private static final int MAX_RETRIEVAL_ATTEMPTS = 3;
    private static final String RETRIEVAL_TOOL_NAME = "system:knowledge_search";

    private final ConversationRetrievalService retrievalService;
    private final RetrievalProperties retrievalProperties;
    private final ChatGenerationEventPublisher eventPublisher;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        var request = new ConversationRetrievalRequest(
                state.value(REWRITTEN_QUESTION, ""),
                state.value(INTENT_RESULT, new IntentRecognitionResult(java.util.List.of(), 0D)),
                state.value(RETRIEVAL_SCOPE, RetrievalScope.INTENT),
                state.value(RETRIEVAL_TOP_K, retrievalProperties.getCandidate().getVectorCandidateLimit()),
                state.value(RETRIEVAL_VECTOR_THRESHOLD, retrievalProperties.getCandidate().getCoarseScoreFloor()),
                state.value(RETRIEVAL_ROUND, 1),
                state.value(RETRIEVAL_KNOWLEDGE_BASE_IDS, List.of()));
        ChatGenerationAccumulator accumulator = state.value(GENERATION_ACCUMULATOR,
                new ChatGenerationAccumulator());
        String generationId = state.value(GENERATION_ID, "");
        ChatToolOperationDTO runningOperation = new ChatToolOperationDTO(generationId + ":tool:retrieval:1",
                generationId, 1L, RETRIEVAL_TOOL_NAME, ChatToolOperationStatus.RUNNING);
        accumulator.upsertOperation(runningOperation);
        publishSnapshot(state, accumulator);

        List<RetrievalChunk> results;
        String failureSummary = null;
        try {
            results = retrieveWithRetry(request, generationId);
            accumulator.upsertOperation(new ChatToolOperationDTO(runningOperation.opId(), runningOperation.processId(),
                    runningOperation.sequence(), runningOperation.name(), ChatToolOperationStatus.SUCCESS));
        } catch (RuntimeException exception) {
            results = List.of();
            failureSummary = "知识库检索暂时不可用，已基于现有上下文继续回答";
            accumulator.upsertOperation(new ChatToolOperationDTO(runningOperation.opId(), runningOperation.processId(),
                    runningOperation.sequence(), runningOperation.name(), ChatToolOperationStatus.FAILED));
            log.warn("知识库检索重试耗尽，traceId={}，generationId={}", state.value(TRACE_ID, ""), generationId,
                    exception);
        }
        publishSnapshot(state, accumulator);
        // 1. 仅记录检索范围和命中数量，不记录查询词或片段正文
        log.info("知识库检索完成，traceId={}，当前检索轮次={}，scope={}，topK={}，相似度阈值={}，检索数={}",
                state.value(TRACE_ID, ""), request.round(), request.scope(), request.topK(),
                request.vectorThreshold(), results.size());
        return failureSummary == null
                ? Map.of(RAW_RETRIEVAL_RESULTS, results)
                : Map.of(RAW_RETRIEVAL_RESULTS, results, TOOL_FAILURE_SUMMARIES, List.of(failureSummary));
    }

    private List<RetrievalChunk> retrieveWithRetry(ConversationRetrievalRequest request, String generationId) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIEVAL_ATTEMPTS; attempt++) {
            try {
                return retrievalService.retrieve(request);
            } catch (RuntimeException exception) {
                lastException = exception;
                log.warn("知识库检索失败，将重试，generationId={}，attempt={}", generationId, attempt, exception);
            }
        }
        throw lastException;
    }

    private void publishSnapshot(OverAllState state, ChatGenerationAccumulator accumulator) {
        eventPublisher.publish(new ChatStreamEvent(ChatStreamEventType.SNAPSHOT, null,
                state.value(CONVERSATION_ID, ""), state.value(TRACE_ID, ""), state.value(GENERATION_ID, ""),
                state.value(ASSISTANT_MESSAGE_ID, ""), null, null, 0L, accumulator.operationsSnapshot()));
    }
}
