package com.nexarag.infra.observability.langfuse.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Langfuse Observations API v2 的游标分页响应。
 *
 * @param data 观测记录
 * @param meta 游标信息
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LangfuseObservationPageDTO(List<LangfuseObservationDTO> data, Meta meta) {

    public LangfuseObservationPageDTO {
        data = data == null ? List.of() : List.copyOf(data);
    }

    /**
     * 分页游标元数据。
     *
     * @param cursor 下一页游标
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(String cursor) {
    }
}
