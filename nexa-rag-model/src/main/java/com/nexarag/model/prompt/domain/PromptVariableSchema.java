package com.nexarag.model.prompt.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ClientException;

import java.util.List;

/**
 * Prompt 变量契约，定义模板允许引用及渲染时必须提供的变量。
 *
 * @param allowed 允许引用的变量名
 * @param required 渲染时必须提供的变量名
 */
public record PromptVariableSchema(List<String> allowed, List<String> required) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 创建变量契约。
     *
     * @param allowed 允许引用的变量名
     * @param required 渲染时必须提供的变量名
     * @return 变量契约
     */
    public static PromptVariableSchema of(List<String> allowed, List<String> required) {
        List<String> normalizedRequired = required == null ? List.of() : List.copyOf(required);
        return new PromptVariableSchema(allowed == null ? normalizedRequired : List.copyOf(allowed), normalizedRequired);
    }

    /**
     * 将变量契约序列化为数据库 JSON。
     *
     * @return 数据库 JSON
     */
    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException exception) {
            throw new ClientException("Prompt变量契约序列化失败", exception, BaseErrorCode.PARAM_ERROR);
        }
    }

    /**
     * 解析数据库中的变量契约 JSON。
     *
     * @param json 变量契约 JSON
     * @return 变量契约
     */
    public static PromptVariableSchema fromJson(String json) {
        try {
            PromptVariableSchema schema = OBJECT_MAPPER.readValue(json, PromptVariableSchema.class);
            return of(schema.allowed(), schema.required());
        } catch (Exception exception) {
            throw new ClientException("Prompt变量契约不合法", exception, BaseErrorCode.PARAM_ERROR);
        }
    }
}
