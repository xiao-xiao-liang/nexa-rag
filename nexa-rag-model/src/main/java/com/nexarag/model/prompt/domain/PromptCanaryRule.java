package com.nexarag.model.prompt.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Prompt 用户百分比灰度规则。
 *
 * @param percentage 灰度百分比，取值范围为 0 到 100
 */
public record PromptCanaryRule(Integer percentage) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 校验灰度百分比。
     */
    public void validate() {
        if (percentage == null || percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException("Prompt灰度百分比必须在0到100之间");
        }
    }

    /**
     * 将灰度规则序列化为数据库 JSON。
     *
     * @return 灰度规则 JSON
     */
    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Prompt灰度规则序列化失败", exception);
        }
    }
}
