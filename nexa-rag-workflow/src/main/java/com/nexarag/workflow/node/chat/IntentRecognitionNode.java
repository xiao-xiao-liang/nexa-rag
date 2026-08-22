package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.toolkits.prompt.PromptBuilder;
import com.nexarag.model.prompt.domain.PromptExecutionSnapshot;
import com.nexarag.retrieval.dto.res.IntentRecognitionResult;
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
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.ASSISTANT_MESSAGE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CONVERSATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.GENERATION_ACCUMULATOR;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.GENERATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.REWRITTEN_QUESTION;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TRACE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.PROMPT_EXECUTION_SNAPSHOT;
import static com.nexarag.chat.constants.ChatModelRouteConstants.CHAT_INTENT_ROUTE_KEY;
import static com.nexarag.workflow.constants.ChatWorkflowSystemToolConstants.INTENT_RECOGNITION_SEQUENCE;
import static com.nexarag.workflow.constants.ChatWorkflowSystemToolConstants.INTENT_RECOGNITION_TOOL_NAME;

/**
 * 会话意图识别节点，负责识别检索意图和置信度。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentRecognitionNode implements NodeAction {

    private final ModelGateway modelGateway;
    private final ObjectMapper objectMapper;
    private final PromptBuilder promptBuilder;
    private final ChatGenerationEventPublisher eventPublisher;

    /**
     * 识别改写问题的检索意图，失败时返回低置信度空意图。
     *
     * @param state Workflow 当前状态
     * @return 包含意图识别结果的状态增量
     */
    @Override
    public Map<String, Object> apply(OverAllState state) {
        String question = state.value(REWRITTEN_QUESTION, "");
        ChatGenerationAccumulator accumulator = state.value(GENERATION_ACCUMULATOR, new ChatGenerationAccumulator());
        ChatToolOperationDTO runningOperation = operation(state, ChatToolOperationStatus.RUNNING);
        accumulator.upsertOperation(runningOperation);
        publishSnapshot(state, accumulator);
        IntentRecognitionResult result;
        try {
            // 1. 调用意图识别模型路由
            var response = modelGateway.chat(ChatModelRequest.builder()
                    .traceId(state.value(TRACE_ID, ""))
                    .bizType(ModelBizType.CHAT)
                    .bizId(CHAT_INTENT_ROUTE_KEY)
                    .routeKey(CHAT_INTENT_ROUTE_KEY)
                    .messages(promptBuilder.buildIntentMessages(snapshot(state), Map.of("question", safe(question))))
                    .build());

            // 2. 解析结构化意图结果
            IntentRecognitionResult intentResult = response == null ? null
                    : objectMapper.readValue(response.content(), IntentRecognitionResult.class);
            result = intentResult == null ? emptyIntent() : intentResult;
            log.info("意图识别结果：{}，置信度：{}", result.intentIds(), result.confidence());
        } catch (Exception exception) {
            // 3. 识别失败时交由后续节点扩大检索范围
            log.warn("意图识别失败，进行全局检索", exception);
            result = emptyIntent();
        }
        // 4. 无论模型正常返回还是降级，均通知客户端前置处理完成
        accumulator.upsertOperation(operation(state, ChatToolOperationStatus.SUCCESS));
        publishSnapshot(state, accumulator);
        return Map.of(INTENT_RESULT, result);
    }

    private IntentRecognitionResult emptyIntent() {
        return new IntentRecognitionResult(List.of(), 0.0D);
    }

    private PromptExecutionSnapshot snapshot(OverAllState state) {
        return state.value(PROMPT_EXECUTION_SNAPSHOT, (PromptExecutionSnapshot) null);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private ChatToolOperationDTO operation(OverAllState state, ChatToolOperationStatus status) {
        String generationId = state.value(GENERATION_ID, "");
        return new ChatToolOperationDTO(generationId + ":tool:intent-recognition:1", generationId,
                INTENT_RECOGNITION_SEQUENCE, INTENT_RECOGNITION_TOOL_NAME, status);
    }

    private void publishSnapshot(OverAllState state, ChatGenerationAccumulator accumulator) {
        eventPublisher.publish(new ChatStreamEvent(ChatStreamEventType.SNAPSHOT, null,
                state.value(CONVERSATION_ID, ""), state.value(TRACE_ID, ""), state.value(GENERATION_ID, ""),
                state.value(ASSISTANT_MESSAGE_ID, ""), null, null, 0L, accumulator.operationsSnapshot()));
    }
}
