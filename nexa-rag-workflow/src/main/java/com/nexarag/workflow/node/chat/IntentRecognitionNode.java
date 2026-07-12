package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.retrieval.chat.model.IntentRecognitionResult;
import com.nexarag.workflow.prompt.ChatWorkflowPromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.INTENT_RESULT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.REWRITTEN_QUESTION;
import static com.nexarag.chat.constants.ChatModelRouteConstants.CHAT_INTENT_ROUTE_KEY;

/**
 * 会话意图识别节点，负责识别检索意图和置信度。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentRecognitionNode implements NodeAction {

    private final ModelGateway modelGateway;
    private final ObjectMapper objectMapper;
    private final ChatWorkflowPromptBuilder promptBuilder;

    /**
     * 识别改写问题的检索意图，失败时返回低置信度空意图。
     *
     * @param state Workflow 当前状态
     * @return 包含意图识别结果的状态增量
     */
    @Override
    public Map<String, Object> apply(OverAllState state) {
        String question = state.value(REWRITTEN_QUESTION, "");
        try {
            // 1. 调用意图识别模型路由
            var response = modelGateway.chat(ChatModelRequest.builder()
                    .traceId(UUID.randomUUID().toString())
                    .bizType(ModelBizType.CHAT)
                    .bizId(CHAT_INTENT_ROUTE_KEY)
                    .routeKey(CHAT_INTENT_ROUTE_KEY)
                    .messages(promptBuilder.buildIntentMessages(question))
                    .build());

            // 2. 解析结构化意图结果
            IntentRecognitionResult intentResult = response == null ? null
                    : objectMapper.readValue(response.content(), IntentRecognitionResult.class);
            return Map.of(INTENT_RESULT, intentResult == null ? emptyIntent() : intentResult);
        } catch (Exception exception) {
            // 3. 识别失败时交由后续节点扩大检索范围
            log.warn("意图识别失败，将使用全局检索范围", exception);
            return Map.of(INTENT_RESULT, emptyIntent());
        }
    }

    private IntentRecognitionResult emptyIntent() {
        return new IntentRecognitionResult(List.of(), 0.0D);
    }
}
