package com.nexarag.workflow.request;

import com.nexarag.retrieval.enums.RetrievalScope;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowGraphConstants.MAX_RETRIEVAL_ROUND_VALUE;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CONVERSATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.GENERATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.MAX_RETRIEVAL_ROUND;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_ROUND;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_SCOPE;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_TOP_K;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_VECTOR_THRESHOLD;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.STREAM_STATUS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TRACE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_QUESTION;

/**
 * Chat Workflow 请求。
 *
 * @param userId 用户 ID
 * @param conversationId 会话 ID，可为空
 * @param question 用户问题
 * @param generationId 生成任务 ID
 * @param traceId 链路 ID
 */
public record ChatWorkflowRequest(String userId, String conversationId, String question,
                                  String generationId, String traceId) {

    /**
     * 转换为 Graph 初始状态。
     *
     * @return 初始状态
     */
    public Map<String, Object> toInitialState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(USER_ID, userId);
        state.put(USER_QUESTION, question);
        state.put(GENERATION_ID, generationId);
        state.put(TRACE_ID, traceId);
        state.put(STREAM_STATUS, "INIT");
        state.put(RETRIEVAL_SCOPE, RetrievalScope.INTENT);
        state.put(RETRIEVAL_ROUND, 1);
        state.put(MAX_RETRIEVAL_ROUND, MAX_RETRIEVAL_ROUND_VALUE);
        if (conversationId != null && !conversationId.isBlank()) {
            state.put(CONVERSATION_ID, conversationId);
        }
        return state;
    }
}
