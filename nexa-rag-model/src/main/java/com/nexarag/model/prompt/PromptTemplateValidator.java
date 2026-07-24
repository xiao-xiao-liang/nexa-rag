package com.nexarag.model.prompt;

import com.nexarag.model.prompt.domain.PromptVariableSchema;

/**
 * Prompt 模板校验器，负责校验模板引用的变量是否合法。
 */
public interface PromptTemplateValidator {

    void validate(String promptCode, String content, PromptVariableSchema schema);
}
