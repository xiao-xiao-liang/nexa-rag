package com.nexarag.model.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexarag.model.entity.ModelCallLog;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.enums.ModelRequestType;
import com.nexarag.model.enums.TokenUsageSource;

/**
 * 模型调用日志服务。
 */
public interface ModelCallLogService extends IService<ModelCallLog> {

    /**
     * 创建运行中的模型调用日志。
     *
     * @param traceId      链路追踪ID
     * @param bizType      业务类型
     * @param bizId        业务ID
     * @param modelProfile 模型配置名称
     * @param provider     模型厂商
     * @param baseUrl      模型服务地址
     * @param modelName    模型名称
     * @param requestType  请求类型
     * @return 模型调用日志
     */
    ModelCallLog createRunningLog(String traceId, ModelBizType bizType, String bizId,
                                  String modelProfile, String provider, String baseUrl,
                                  String modelName, ModelRequestType requestType);

    /**
     * 创建运行中的模型调用日志。
     *
     * @param traceId            链路追踪ID
     * @param bizType            业务类型
     * @param bizId              业务ID
     * @param modelProfile       模型配置名称
     * @param provider           模型厂商
     * @param baseUrl            模型服务地址
     * @param modelName          模型名称
     * @param requestType        请求类型
     * @param attemptNo          第几次尝试
     * @param fallbackFromCallId 降级来源调用ID
     * @param fallbackReason     降级原因
     * @return 模型调用日志
     */
    ModelCallLog createRunningLog(String traceId, ModelBizType bizType, String bizId,
                                  String modelProfile, String provider, String baseUrl,
                                  String modelName, ModelRequestType requestType,
                                  Integer attemptNo, String fallbackFromCallId, String fallbackReason);

    /**
     * 标记模型调用成功。
     *
     * @param callId           模型调用ID
     * @param promptTokens     输入 Token 数
     * @param completionTokens 输出 Token 数
     * @param totalTokens      总 Token 数
     * @param durationMs       耗时毫秒
     */
    void markSuccess(String callId, Integer promptTokens, Integer completionTokens, Integer totalTokens, long durationMs);

    /**
     * 标记流式模型调用成功。
     *
     * @param callId                模型调用ID
     * @param promptTokens          输入 Token 数
     * @param completionTokens      输出 Token 数
     * @param totalTokens           总 Token 数
     * @param tokenUsageSource      Token 用量统计来源
     * @param firstTokenLatencyMs   首个分片耗时，单位毫秒
     * @param chunkCount            流式响应分片数量
     * @param outputCharCount       输出字符数
     * @param estimatedOutputTokens 估算输出 Token 数
     * @param durationMs            耗时毫秒
     */
    void markStreamSuccess(String callId, Integer promptTokens, Integer completionTokens,
                           Integer totalTokens, TokenUsageSource tokenUsageSource,
                           Long firstTokenLatencyMs, Integer chunkCount,
                           Integer outputCharCount, Integer estimatedOutputTokens,
                           long durationMs);

    /**
     * 标记模型调用失败。
     *
     * @param callId       模型调用ID
     * @param errorCode    错误码
     * @param errorMessage 错误信息
     * @param durationMs   耗时毫秒
     */
    void markFailed(String callId, String errorCode, String errorMessage, long durationMs);

    /**
     * 标记模型调用超时。
     *
     * @param callId       模型调用ID
     * @param errorCode    错误码
     * @param errorMessage 错误信息
     * @param durationMs   耗时毫秒
     */
    void markTimeout(String callId, String errorCode, String errorMessage, long durationMs);

    /**
     * 标记模型调用被客户端取消。
     *
     * @param callId     模型调用ID
     * @param durationMs 耗时毫秒
     */
    void markCanceled(String callId, long durationMs);
}
