package com.nexarag.model.toolkits.prompt;

import static com.nexarag.model.constants.PromptContractConstant.ANSWER_CURRENT_QUESTION_CODE;
import static com.nexarag.model.constants.PromptContractConstant.ANSWER_RETRIEVAL_EVIDENCE_CODE;
import static com.nexarag.model.constants.PromptContractConstant.ANSWER_SYSTEM_INSTRUCTION_CODE;
import static com.nexarag.model.constants.PromptContractConstant.INTENT_INSTRUCTION_CODE;
import static com.nexarag.model.constants.PromptContractConstant.REWRITE_INSTRUCTION_CODE;
import static com.nexarag.model.constants.PromptContractConstant.TITLE_INSTRUCTION_CODE;
import com.nexarag.model.gateway.chat.ChatModelMessage;
import com.nexarag.model.prompt.domain.PromptExecutionSnapshot;
import com.nexarag.model.prompt.domain.PromptVariableSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prompt 消息构建器测试，验证辅助任务始终携带真实用户问题。
 */
class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder(new PromptRender());

    @Test
    void shouldAppendCurrentQuestionAsUserMessageForRewrite() {
        List<ChatModelMessage> messages = promptBuilder.buildRewriteMessages(
                snapshot(REWRITE_INSTRUCTION_CODE), Map.of("question", "历史案件如何构建图"));

        assertThat(messages)
                .extracting(ChatModelMessage::role)
                .containsExactly("SYSTEM", "USER");
        assertThat(messages.get(1).content())
                .isEqualTo("请按照系统要求改写以下当前问题，只返回改写结果：\n历史案件如何构建图");
    }

    @Test
    void shouldAppendCurrentQuestionAsUserMessageForIntent() {
        List<ChatModelMessage> messages = promptBuilder.buildIntentMessages(
                snapshot(INTENT_INSTRUCTION_CODE), Map.of("question", "历史案件如何构建图"));

        assertThat(messages)
                .extracting(ChatModelMessage::role)
                .containsExactly("SYSTEM", "USER");
        assertThat(messages.get(1).content())
                .isEqualTo("请按照系统要求识别以下用户问题的知识库意图，只返回合法 JSON：\n历史案件如何构建图");
    }

    @Test
    void shouldAppendCurrentQuestionAsUserMessageForTitle() {
        List<ChatModelMessage> messages = promptBuilder.buildTitleMessages(
                snapshot(TITLE_INSTRUCTION_CODE), Map.of("question", "历史案件如何构建图"));

        assertThat(messages)
                .extracting(ChatModelMessage::role)
                .containsExactly("SYSTEM", "USER");
        assertThat(messages.get(1).content())
                .isEqualTo("请按照系统要求为以下用户问题生成标题，只返回标题文本：\n历史案件如何构建图");
    }

    @Test
    void shouldKeepOnlyLeadingSystemMessageForVllmAnswer() {
        List<ChatModelMessage> messages = promptBuilder.buildAnswerMessages(
                answerSnapshot(), "历史案件如何构建图", "已有会话摘要",
                List.of(new ChatModelMessage("USER", "上一轮问题"), new ChatModelMessage("ASSISTANT", "上一轮回答")),
                "历史案件图谱构建资料");

        assertThat(messages)
                .extracting(ChatModelMessage::role)
                .containsExactly("SYSTEM", "USER", "ASSISTANT", "USER");
        assertThat(messages.getFirst().content())
                .contains("系统规则", "会话摘要：\n已有会话摘要", "<retrieval_context>", "历史案件图谱构建资料");
        assertThat(messages.getLast().content()).isEqualTo("当前问题：历史案件如何构建图");
    }

    @Test
    void shouldExposeNonOverlappingSemanticSectionsForAnswerObservation() {
        AnswerPromptComposition composition = promptBuilder.buildAnswerPrompt(
                answerSnapshot(), "历史案件如何构建图", "已有会话摘要",
                List.of(new ChatModelMessage("USER", "上一轮问题"), new ChatModelMessage("ASSISTANT", "上一轮回答")),
                "历史案件图谱构建资料", "工具调用失败");

        assertThat(composition.systemInstruction()).isEqualTo("系统规则");
        assertThat(composition.summary()).isEqualTo("已有会话摘要");
        assertThat(composition.retrievalContent()).isEqualTo("历史案件图谱构建资料");
        assertThat(composition.toolContent()).isEqualTo("工具调用失败");
        assertThat(composition.messages().getFirst().content())
                .contains("系统规则", "会话摘要：\n已有会话摘要", "历史案件图谱构建资料", "工具调用失败");
    }

    private PromptExecutionSnapshot snapshot(String promptCode) {
        return PromptExecutionSnapshot.of(Map.of(promptCode,
                new PromptExecutionSnapshot.PromptSnapshot(promptCode, 101L, 11L, 1L,
                        "系统规则：{{question}}", PromptVariableSchema.of(List.of("question"), List.of("question")))));
    }

    private PromptExecutionSnapshot answerSnapshot() {
        PromptVariableSchema noVariables = PromptVariableSchema.of(List.of(), List.of());
        return PromptExecutionSnapshot.of(Map.of(
                ANSWER_SYSTEM_INSTRUCTION_CODE,
                new PromptExecutionSnapshot.PromptSnapshot(ANSWER_SYSTEM_INSTRUCTION_CODE, 101L, 11L, 1L,
                        "系统规则", noVariables),
                ANSWER_RETRIEVAL_EVIDENCE_CODE,
                new PromptExecutionSnapshot.PromptSnapshot(ANSWER_RETRIEVAL_EVIDENCE_CODE, 102L, 12L, 1L,
                        "资料：{{evidence}}", PromptVariableSchema.of(List.of("evidence"), List.of("evidence"))),
                ANSWER_CURRENT_QUESTION_CODE,
                new PromptExecutionSnapshot.PromptSnapshot(ANSWER_CURRENT_QUESTION_CODE, 103L, 13L, 1L,
                        "当前问题：{{question}}", PromptVariableSchema.of(List.of("question"), List.of("question")))
        ));
    }
}
