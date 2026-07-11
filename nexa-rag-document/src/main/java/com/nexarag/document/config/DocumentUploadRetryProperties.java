package com.nexarag.document.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 文档上传重试配置，定义每次对象存储失败后的短退避时间。
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "nexa.document.pipeline.upload-retry")
public class DocumentUploadRetryProperties {

    /**
     * 每次失败后的退避毫秒数，列表长度即最大上传尝试次数。
     */
    @NotEmpty(message = "文档上传重试退避配置不能为空")
    private List<@PositiveOrZero(message = "文档上传重试退避时间不能小于0") Long> backoffMillis =
            List.of(200L, 500L, 1000L);
}
