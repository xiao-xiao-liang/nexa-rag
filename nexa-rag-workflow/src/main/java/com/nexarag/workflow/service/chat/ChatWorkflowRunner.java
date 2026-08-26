package com.nexarag.workflow.service.chat;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.auth.tenant.TenantAccessGuard;
import com.nexarag.model.toolkits.prompt.PromptBuilder;
import com.nexarag.model.toolkits.prompt.PromptReleaseResolver;
import com.nexarag.workflow.service.StreamingWorkflowGraphRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Set;

import static com.nexarag.workflow.constants.ChatWorkflowGraphConstants.CHAT_CONVERSATION_GRAPH_NAME;
import static com.nexarag.workflow.constants.ChatWorkflowGraphConstants.CHAT_THREAD_PREFIX;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TRACE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TENANT_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.PROMPT_EXECUTION_SNAPSHOT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CONVERSATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.GENERATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_QUESTION;

/**
 * Chat Workflow Runner，负责编译并以请求级线程标识运行对话 Graph。
 */
@Service
@ConditionalOnProperty(prefix = "nexa.chat", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ChatWorkflowRunner implements StreamingWorkflowGraphRunner {
    private static final Set<String> CHAT_PROMPT_CODES = Set.of(
            PromptBuilder.REWRITE_INSTRUCTION,
            PromptBuilder.INTENT_INSTRUCTION,
            PromptBuilder.ANSWER_SYSTEM_INSTRUCTION,
            PromptBuilder.ANSWER_RETRIEVAL_EVIDENCE,
            PromptBuilder.ANSWER_CURRENT_QUESTION,
            PromptBuilder.TITLE_INSTRUCTION);

    private final CompiledGraph compiledGraph;
    private final PromptReleaseResolver promptReleaseResolver;
    private final TenantAccessGuard tenantAccessGuard;

    public ChatWorkflowRunner(@Qualifier("chatConversationGraph") StateGraph graph,
                              PromptReleaseResolver promptReleaseResolver, TenantAccessGuard tenantAccessGuard) {
        this.promptReleaseResolver = promptReleaseResolver;
        this.tenantAccessGuard = tenantAccessGuard;
        try {
            this.compiledGraph = graph.compile();
        } catch (GraphStateException exception) {
            throw new ServiceException("Chat Workflow Graph 编译失败");
        }
    }

    @Override
    public String graphName() {
        return CHAT_CONVERSATION_GRAPH_NAME;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Flux<GraphResponse<StreamingOutput<?>>> stream(Map<String, Object> initialState) {
        // 1. 在 Graph 执行前绑定本次请求的所有 Prompt 版本，隔离后续发布变更
        Map<String, Object> state = new java.util.LinkedHashMap<>(initialState);
        state.put(PROMPT_EXECUTION_SNAPSHOT, promptReleaseResolver.resolve(CHAT_PROMPT_CODES,
                String.valueOf(state.get(USER_ID))));

        // 2. 使用链路标识构造唯一 Graph 线程，并传入已绑定 Prompt 快照的初始状态
        String traceId = String.valueOf(state.get(TRACE_ID));
        RunnableConfig config = RunnableConfig.builder().threadId(CHAT_THREAD_PREFIX + traceId).build();
        return Flux.defer(() -> {
            tenantAccessGuard.requireUserAccess(Long.valueOf(String.valueOf(state.get(USER_ID))),
                    String.valueOf(state.get(TENANT_ID)));
            long workflowStart = System.currentTimeMillis();
            log.info("对话工作流开始，traceId={}，userId={}，conversationId={}", traceId, state.get(USER_ID), state.get(CONVERSATION_ID));

            return compiledGraph.graphResponseStream(state, config)
                    .doOnComplete(() -> log.info("对话工作流结束，traceId={}，totalDurationMs={}", traceId,
                            Math.max(0, System.currentTimeMillis() - workflowStart)))
                    .doOnError(exception -> log.error("对话工作流执行失败，traceId={}，totalDurationMs={}", traceId,
                            Math.max(0, System.currentTimeMillis() - workflowStart), exception))
                    .map(response -> (GraphResponse<StreamingOutput<?>>) (GraphResponse) response);
        });
    }
}
