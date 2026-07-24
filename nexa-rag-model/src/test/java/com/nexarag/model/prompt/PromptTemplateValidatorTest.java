package com.nexarag.model.prompt;

import com.nexarag.common.exception.ClientException;
import com.nexarag.model.prompt.domain.PromptVariableSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prompt 模板校验器测试。
 */
class PromptTemplateValidatorTest {

    /**
     * 验证空正文时拒绝提交。
     */
    @Test
    void shouldRejectEmptyContent() {
        PromptTemplateValidator validator = new DefaultPromptTemplateValidator();

        // 1. 校验空白模板正文
        assertThatThrownBy(() -> validator.validate("chat.answer.current-question", "  ",
                PromptVariableSchema.of(List.of(), List.of())))
                // 2. 验证返回明确的客户端错误
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("不能为空");
    }

    /**
     * 验证 Mustache 标签未闭合时拒绝提交。
     */
    @Test
    void shouldRejectUnclosedMustacheTag() {
        PromptTemplateValidator validator = new DefaultPromptTemplateValidator();

        // 1. 校验缺少闭合大括号的变量标签
        assertThatThrownBy(() -> validator.validate("chat.answer.current-question", "{{question",
                PromptVariableSchema.of(List.of("question"), List.of())))
                // 2. 验证未闭合标签不能绕过变量契约校验
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("语法");
    }

    /**
     * 验证变量契约为空时返回明确的客户端异常。
     */
    @Test
    void shouldRejectNullVariableSchema() {
        PromptTemplateValidator validator = new DefaultPromptTemplateValidator();

        // 1. 使用没有变量标签的正文校验空变量契约
        assertThatThrownBy(() -> validator.validate("chat.answer.current-question", "固定正文", null))
                // 2. 验证不会暴露空指针异常
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("变量契约");
    }

    /**
     * 验证模板未引用必填变量时拒绝提交。
     */
    @Test
    void shouldRejectTemplateMissingRequiredVariable() {
        PromptTemplateValidator validator = new DefaultPromptTemplateValidator();

        // 1. 使用声明 question 为必填变量但未引用它的模板
        assertThatThrownBy(() -> validator.validate("chat.answer.current-question", "固定正文",
                PromptVariableSchema.of(List.of("question"), List.of("question"))))
                // 2. 验证错误可定位到未引用的必填变量
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("question");
    }

    /**
     * 验证模板引用未登记变量时拒绝提交。
     */
    @Test
    void shouldRejectUndeclaredVariable() {
        PromptTemplateValidator validator = new DefaultPromptTemplateValidator();

        // 1. 使用只允许 question 的变量契约校验未知变量
        assertThatThrownBy(() -> validator.validate("chat.answer.current-question", "{{unknown}}",
                PromptVariableSchema.of(List.of("question"), List.of("question"))))
                // 2. 验证错误可定位到当前 Prompt 和未登记变量
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("chat.answer.current-question")
                .hasMessageContaining("unknown");
    }
}
