package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.workflow.prompt.ChatWorkflowPromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CONVERSATION_CONTEXT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.REWRITTEN_QUESTION;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_QUESTION;

/**
 * 会话问题改写节点，负责调用普通能力模型生成独立检索问题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionRewriteNode implements NodeAction {

    private final ModelGateway modelGateway;
    private final ChatWorkflowPromptBuilder promptBuilder;

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
        String context = state.value(CONVERSATION_CONTEXT, "");
        try {
            // 2. 调用问题改写模型路由
            var response = modelGateway.chat(ChatModelRequest.builder()
                    .traceId(UUID.randomUUID().toString())
                    .bizType(ModelBizType.CHAT)
                    .bizId("chat-rewrite")
                    .routeKey("chat-rewrite")
                    .messages(promptBuilder.buildRewriteMessages(question, context))
                    .build());
            String rewrittenQuestion = response == null || response.content() == null || response.content().isBlank()
                    ? question : response.content().trim();
            return Map.of(REWRITTEN_QUESTION, rewrittenQuestion);
        } catch (RuntimeException exception) {
            // 3. 模型调用失败时回退原问题，保证检索链路继续执行
            log.warn("问题改写失败，回退原问题", exception);
            return Map.of(REWRITTEN_QUESTION, question);
        }
    }
}
