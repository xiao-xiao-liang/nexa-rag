package com.nexarag.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 模型治理配置实体，对应 model_governance_config 表，保存单个模型配置的重试、熔断、限流和并发隔离参数。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("model_governance_config")
public class ModelGovernanceConfig {

    /**
     * 治理配置ID。
     */
    @TableId("governance_id")
    private Long governanceId;

    /**
     * 模型配置ID。
     */
    private Long configId;

    /**
     * 是否启用治理配置。
     */
    private Boolean enabled;

    /**
     * 是否启用重试。
     */
    private Boolean retryEnabled;

    /**
     * 最大尝试次数。
     */
    private Integer maxAttempts;

    /**
     * 重试等待时间，单位毫秒。
     */
    private Integer retryWaitMs;

    /**
     * 是否启用熔断。
     */
    private Boolean circuitEnabled;

    /**
     * 失败率阈值。
     */
    private Integer failureRateThreshold;

    /**
     * 慢调用比例阈值。
     */
    private Integer slowCallRateThreshold;

    /**
     * 慢调用判定时长，单位毫秒。
     */
    private Integer slowCallDurationMs;

    /**
     * 熔断统计最小调用数。
     */
    private Integer minimumNumberOfCalls;

    /**
     * 熔断滑动窗口大小。
     */
    private Integer slidingWindowSize;

    /**
     * 熔断打开后的等待时间，单位毫秒。
     */
    private Integer waitDurationInOpenStateMs;

    /**
     * 是否启用限流。
     */
    private Boolean rateLimitEnabled;

    /**
     * 单周期允许调用数。
     */
    private Integer limitForPeriod;

    /**
     * 限流周期刷新时间，单位毫秒。
     */
    private Integer limitRefreshPeriodMs;

    /**
     * 获取限流许可等待时间，单位毫秒。
     */
    private Integer timeoutDurationMs;

    /**
     * 是否启用并发隔离。
     */
    private Boolean bulkheadEnabled;

    /**
     * 最大并发调用数。
     */
    private Integer maxConcurrentCalls;

    /**
     * 获取并发许可等待时间，单位毫秒。
     */
    private Integer maxWaitDurationMs;

    /**
     * 创建时间。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 删除标记：0未删除，1已删除。
     */
    @TableLogic(value = "0", delval = "1")
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;

    /**
     * 删除时间。
     */
    private LocalDateTime deleteTime;
}
