package com.nexarag.model.toolkits.prompt;

import static com.nexarag.model.constants.PromptContractConstant.ANSWER_CURRENT_QUESTION_CODE;
import static com.nexarag.model.constants.PromptContractConstant.ANSWER_RETRIEVAL_EVIDENCE_CODE;
import static com.nexarag.model.constants.PromptContractConstant.ANSWER_SUMMARY_PREFIX;
import static com.nexarag.model.constants.PromptContractConstant.ANSWER_SYSTEM_INSTRUCTION_CODE;
import static com.nexarag.model.constants.PromptContractConstant.EVIDENCE_VARIABLE;
import static com.nexarag.model.constants.PromptContractConstant.INTENT_INSTRUCTION_CODE;
import static com.nexarag.model.constants.PromptContractConstant.INTENT_USER_INPUT_PREFIX;
import static com.nexarag.model.constants.PromptContractConstant.QUESTION_VARIABLE;
import static com.nexarag.model.constants.PromptContractConstant.RETRIEVAL_CONTEXT_PREFIX;
import static com.nexarag.model.constants.PromptContractConstant.RETRIEVAL_CONTEXT_SUFFIX;
import static com.nexarag.model.constants.PromptContractConstant.REWRITE_INSTRUCTION_CODE;
import static com.nexarag.model.constants.PromptContractConstant.REWRITE_USER_INPUT_PREFIX;
import static com.nexarag.model.constants.PromptContractConstant.SYSTEM_ROLE;
import static com.nexarag.model.constants.PromptContractConstant.TITLE_INSTRUCTION_CODE;
import static com.nexarag.model.constants.PromptContractConstant.TITLE_USER_INPUT_PREFIX;
import static com.nexarag.model.constants.PromptContractConstant.TOOL_EVIDENCE_PREFIX;
import static com.nexarag.model.constants.PromptContractConstant.USER_ROLE;

import com.nexarag.model.gateway.chat.ChatModelMessage;
import com.nexarag.model.prompt.domain.PromptExecutionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Prompt 消息构建器，负责固定模型消息角色、顺序和检索证据安全边界。
 */
@Component
@RequiredArgsConstructor
public class PromptBuilder {

    private final PromptRender promptRender;

    /**
     * 构建问题改写消息。
     *
     * @param snapshot  请求级 Prompt 快照
     * @param variables 渲染变量
     * @return 固定角色顺序的模型消息
     */
    public List<ChatModelMessage> buildRewriteMessages(PromptExecutionSnapshot snapshot, Map<String, Object> variables) {
        return List.of(
                system(render(snapshot, REWRITE_INSTRUCTION_CODE, variables)),
                user(REWRITE_USER_INPUT_PREFIX + variable(variables, QUESTION_VARIABLE))
        );
    }

    /**
     * 构建意图识别消息。
     *
     * @param snapshot  请求级 Prompt 快照
     * @param variables 渲染变量
     * @return 固定角色顺序的模型消息
     */
    public List<ChatModelMessage> buildIntentMessages(PromptExecutionSnapshot snapshot, Map<String, Object> variables) {
        return List.of(
                system(render(snapshot, INTENT_INSTRUCTION_CODE, variables)),
                user(INTENT_USER_INPUT_PREFIX + variable(variables, QUESTION_VARIABLE))
        );
    }

    /**
     * 构建最终回答消息，检索证据的安全边界始终由代码包裹。
     *
     * @param snapshot        请求级 Prompt 快照
     * @param question        当前问题
     * @param summary         会话摘要
     * @param historyMessages 历史消息
     * @param evidence        检索证据
     * @return 固定角色顺序的模型消息
     */
    public List<ChatModelMessage> buildAnswerMessages(PromptExecutionSnapshot snapshot, String question, String summary,
                                                      List<ChatModelMessage> historyMessages, String evidence) {
        return buildAnswerPrompt(snapshot, question, summary, historyMessages, evidence, "").messages();
    }

    /**
     * 构建最终回答 Prompt，并保留用于 Token 观测的原始语义分段。
     *
     * @param snapshot         请求级 Prompt 快照
     * @param question         当前问题
     * @param summary          会话摘要
     * @param historyMessages  历史消息
     * @param retrievalContent 检索证据
     * @param toolContent      工具执行状态
     * @return 最终消息与互斥语义分段
     */
    public AnswerPromptComposition buildAnswerPrompt(PromptExecutionSnapshot snapshot, String question, String summary,
                                                     List<ChatModelMessage> historyMessages, String retrievalContent,
                                                     String toolContent) {
        // 1. 将固定系统规则、会话上下文和检索证据合并为唯一首位 SYSTEM 消息，兼容 vLLM Chat Template。
        String systemInstruction = render(snapshot, ANSWER_SYSTEM_INSTRUCTION_CODE, Map.of());
        String renderedEvidence = render(snapshot, ANSWER_RETRIEVAL_EVIDENCE_CODE,
                Map.of(EVIDENCE_VARIABLE, appendToolEvidence(retrievalContent, toolContent)));
        String leadingSystemContent = systemInstruction
                + ANSWER_SUMMARY_PREFIX + safe(summary)
                + RETRIEVAL_CONTEXT_PREFIX + renderedEvidence + RETRIEVAL_CONTEXT_SUFFIX;
        List<ChatModelMessage> messages = new ArrayList<>();
        messages.add(system(leadingSystemContent));
        if (historyMessages != null) {
            messages.addAll(historyMessages);
        }

        // 2. 最后追加当前问题，确保其始终位于消息序列末尾
        String renderedQuestion = render(snapshot, ANSWER_CURRENT_QUESTION_CODE, Map.of(QUESTION_VARIABLE, safe(question)));
        messages.add(user(renderedQuestion));
        return new AnswerPromptComposition(messages, systemInstruction, summary, historyMessages, question,
                retrievalContent, toolContent);
    }

    /**
     * 构建会话标题生成消息。
     *
     * @param snapshot  请求级 Prompt 快照
     * @param variables 渲染变量
     * @return 固定角色顺序的模型消息
     */
    public List<ChatModelMessage> buildTitleMessages(PromptExecutionSnapshot snapshot, Map<String, Object> variables) {
        return List.of(
                system(render(snapshot, TITLE_INSTRUCTION_CODE, variables)),
                user(TITLE_USER_INPUT_PREFIX + variable(variables, QUESTION_VARIABLE))
        );
    }

    private String render(PromptExecutionSnapshot snapshot, String promptCode, Map<String, Object> variables) {
        return promptRender.render(snapshot, promptCode, variables).content();
    }

    private ChatModelMessage system(String content) {
        return new ChatModelMessage(SYSTEM_ROLE, content);
    }

    private ChatModelMessage user(String content) {
        return new ChatModelMessage(USER_ROLE, content);
    }

    private String variable(Map<String, Object> variables, String name) {
        Object value = variables == null ? null : variables.get(name);
        return value == null ? "" : String.valueOf(value);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String appendToolEvidence(String retrievalContent, String toolContent) {
        if (!org.springframework.util.StringUtils.hasText(toolContent)) {
            return safe(retrievalContent);
        }
        return safe(retrievalContent) + TOOL_EVIDENCE_PREFIX + toolContent;
    }
}
