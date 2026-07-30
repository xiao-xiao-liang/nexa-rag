package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.toolkits.prompt.PromptBuilder;
import com.nexarag.model.prompt.domain.PromptExecutionSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CONVERSATION_CONTEXT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.REWRITTEN_QUESTION;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_QUESTION;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TRACE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.PROMPT_EXECUTION_SNAPSHOT;
import static com.nexarag.chat.constants.ChatModelRouteConstants.CHAT_REWRITE_ROUTE_KEY;

/**
 * 会话问题改写节点，负责调用普通能力模型生成独立检索问题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionRewriteNode implements NodeAction {

    private final ModelGateway modelGateway;
    private final PromptBuilder promptBuilder;

    /**
     * 改写当前问题，模型不可用时回退原问题。
     *
     * @param state Workflow 当前状态
     * @return 包含改写问题的状态增量
     */
    @Override
    public Map<String, Object> apply(OverAllState state) {
        // 1. 读取原问题和会话上下文
        String question = state.value(USER_QUESTION, "");
        com.nexarag.chat.domain.ConversationContext context = state.value(CONVERSATION_CONTEXT,
                (com.nexarag.chat.domain.ConversationContext) null);
        try {
            // 2. 调用问题改写模型路由
            var response = modelGateway.chat(ChatModelRequest.builder()
                    .traceId(state.value(TRACE_ID, ""))
                    .bizType(ModelBizType.CHAT)
                    .bizId(CHAT_REWRITE_ROUTE_KEY)
                    .routeKey(CHAT_REWRITE_ROUTE_KEY)
                    .messages(promptBuilder.buildRewriteMessages(snapshot(state), Map.of(
                            "conversationSummary", context == null ? "" : safe(context.summary()),
                            "recentMessages", recentMessages(context),
                            "question", safe(question))))
                    .build());
            String rewrittenQuestion = response == null || response.content() == null || response.content().isBlank()
                    ? question : response.content().trim();
            log.info("问题改写结果：{}", rewrittenQuestion);
            return Map.of(REWRITTEN_QUESTION, rewrittenQuestion);
        } catch (RuntimeException exception) {
            // 3. 模型调用失败时回退原问题，保证检索链路继续执行
            log.warn("问题改写失败，回退原问题", exception);
            return Map.of(REWRITTEN_QUESTION, question);
        }
    }

    private PromptExecutionSnapshot snapshot(OverAllState state) {
        return state.value(PROMPT_EXECUTION_SNAPSHOT, (PromptExecutionSnapshot) null);
    }

    private String recentMessages(com.nexarag.chat.domain.ConversationContext context) {
        if (context == null) {
            return "";
        }
        return context.recentMessages().stream()
                .map(message -> message.role().name() + "：" + safe(message.content()))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
