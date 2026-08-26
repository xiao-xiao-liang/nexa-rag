package com.nexarag.workflow.request;

import com.nexarag.retrieval.enums.RetrievalScope;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowGraphConstants.MAX_RETRIEVAL_ROUND_VALUE;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CONVERSATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TENANT_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.GENERATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.MAX_RETRIEVAL_ROUND;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_ROUND;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_SCOPE;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_TOP_K;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_VECTOR_THRESHOLD;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_KNOWLEDGE_BASE_IDS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.STREAM_STATUS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TRACE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_QUESTION;

/**
 * Chat Workflow 请求。
 *
 * @param userId 用户 ID
 * @param tenantId 可信当前租户ID
 * @param conversationId 会话 ID，可为空
 * @param question 用户问题
 * @param generationId 生成任务 ID
 * @param traceId 链路 ID
 * @param knowledgeBaseIds 可选知识库检索范围；为空表示当前租户全部知识库
 */
public record ChatWorkflowRequest(String userId, String tenantId, String conversationId, String question,
                                  String generationId, String traceId, List<Long> knowledgeBaseIds) {

    /** 创建未限定知识库范围的工作流请求。 */
    public ChatWorkflowRequest(String userId, String conversationId, String question,
                               String generationId, String traceId) {
        this(userId, null, conversationId, question, generationId, traceId, List.of());
    }

    /**
     * 转换为 Graph 初始状态。
     *
     * @return 初始状态
     */
    public Map<String, Object> toInitialState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(USER_ID, userId);
        state.put(TENANT_ID, tenantId);
        state.put(USER_QUESTION, question);
        state.put(GENERATION_ID, generationId);
        state.put(TRACE_ID, traceId);
        state.put(STREAM_STATUS, "INIT");
        state.put(RETRIEVAL_SCOPE, RetrievalScope.INTENT);
        state.put(RETRIEVAL_KNOWLEDGE_BASE_IDS,
                knowledgeBaseIds == null ? List.of() : List.copyOf(knowledgeBaseIds));
        state.put(RETRIEVAL_ROUND, 1);
        state.put(MAX_RETRIEVAL_ROUND, MAX_RETRIEVAL_ROUND_VALUE);
        if (conversationId != null && !conversationId.isBlank()) {
            state.put(CONVERSATION_ID, conversationId);
        }
        return state;
    }
}
