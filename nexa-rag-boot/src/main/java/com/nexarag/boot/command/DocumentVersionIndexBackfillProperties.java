package com.nexarag.boot.command;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 历史文档版本索引元数据回填配置，默认关闭，避免升级时自动触发大规模向量重写。
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "nexa.document.version.backfill")
public class DocumentVersionIndexBackfillProperties {

    /** 是否在应用启动后执行回填。 */
    private boolean enabled;

    /** 单页回填的 V1 版本数量。 */
    @Min(value = 1, message = "文档版本索引回填批量大小必须大于0")
    private int batchSize = 100;
}
