package com.nexarag.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.enums.ModelCallStatus;
import com.nexarag.model.enums.ModelRequestType;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 模型调用日志实体，对应 model_call_log 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("model_call_log")
public class ModelCallLog {

    /**
     * 模型调用ID。
     */
    @TableId("call_id")
    private String callId;

    /**
     * 链路追踪ID。
     */
    private String traceId;

    /**
     * 业务类型。
     */
    private ModelBizType bizType;

    /**
     * 业务ID。
     */
    private String bizId;

    /**
     * 模型配置名称。
     */
    private String modelProfile;

    /**
     * 模型厂商。
     */
    private String provider;

    /**
     * 模型服务地址。
     */
    private String baseUrl;

    /**
     * 模型名称。
     */
    private String modelName;

    /**
     * 请求类型。
     */
    private ModelRequestType requestType;

    /**
     * 调用状态。
     */
    private ModelCallStatus status;

    /**
     * 输入 Token 数。
     */
    private Integer promptTokens;

    /**
     * 输出 Token 数。
     */
    private Integer completionTokens;

    /**
     * 总 Token 数。
     */
    private Integer totalTokens;

    /**
     * 耗时毫秒。
     */
    private Long durationMs;

    /**
     * 第几次尝试。
     */
    private Integer attemptNo;

    /**
     * 降级来源调用ID。
     */
    private String fallbackFromCallId;

    /**
     * 降级原因。
     */
    private String fallbackReason;

    /**
     * 降级来源模型。
     */
    private String fallbackFrom;

    /**
     * 降级目标模型。
     */
    private String fallbackTo;

    /**
     * 错误码。
     */
    private String errorCode;

    /**
     * 错误信息。
     */
    private String errorMessage;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;
}
