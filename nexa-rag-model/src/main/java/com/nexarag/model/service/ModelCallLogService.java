package com.nexarag.model.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexarag.model.entity.ModelCallLog;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.enums.ModelRequestType;

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
     * 标记模型调用失败。
     *
     * @param callId       模型调用ID
     * @param errorCode    错误码
     * @param errorMessage 错误信息
     * @param durationMs   耗时毫秒
     */
    void markFailed(String callId, String errorCode, String errorMessage, long durationMs);
}
