package com.nexarag.model.dto.prompt;

import lombok.Builder;

/**
 * Prompt 基础定义更新数据传输对象。
 *
 * @param name           提示词名称
 * @param variableSchema 变量契约 JSON
 * @param enabled        是否启用
 */
@Builder
public record PromptUpdateDTO(String name, String variableSchema, Boolean enabled) {
}
