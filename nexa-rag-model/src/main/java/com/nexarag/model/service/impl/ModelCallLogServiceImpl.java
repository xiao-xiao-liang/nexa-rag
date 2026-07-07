package com.nexarag.model.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexarag.model.entity.ModelCallLog;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.enums.ModelCallStatus;
import com.nexarag.model.enums.ModelRequestType;
import com.nexarag.model.enums.TokenUsageSource;
import com.nexarag.model.mapper.ModelCallLogMapper;
import com.nexarag.model.service.ModelCallLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 模型调用日志服务实现。
 */
@Service
public class ModelCallLogServiceImpl extends ServiceImpl<ModelCallLogMapper, ModelCallLog>
        implements ModelCallLogService {

    @Override
    public ModelCallLog createRunningLog(String traceId, ModelBizType bizType, String bizId,
                                         String modelProfile, String provider, String baseUrl,
                                         String modelName, ModelRequestType requestType) {
        return createRunningLog(traceId, bizType, bizId, modelProfile, provider, baseUrl, modelName,
                requestType, 1, null, null);
    }

    @Override
    public ModelCallLog createRunningLog(String traceId, ModelBizType bizType, String bizId,
                                         String modelProfile, String provider, String baseUrl,
                                         String modelName, ModelRequestType requestType,
                                         Integer attemptNo, String fallbackFromCallId, String fallbackReason) {
        // 1. 构建模型调用日志，禁止记录 apiKey 和完整 prompt
        ModelCallLog log = ModelCallLog.builder()
                .callId(UUID.randomUUID().toString().replace("-", ""))
                .traceId(traceId)
                .bizType(bizType)
                .bizId(bizId)
                .modelProfile(modelProfile)
                .provider(provider)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .requestType(requestType)
                .status(ModelCallStatus.RUNNING)
                .attemptNo(attemptNo == null || attemptNo <= 0 ? 1 : attemptNo)
                .fallbackFromCallId(fallbackFromCallId)
                .fallbackReason(fallbackReason)
                .createTime(LocalDateTime.now())
                .build();

        // 2. 保存运行日志
        this.save(log);
        return log;
    }

    @Override
    public void markSuccess(String callId, Integer promptTokens, Integer completionTokens,
                            Integer totalTokens, long durationMs) {
        // 1. 使用 lambdaUpdate 按调用ID更新成功状态和 Token 统计
        this.lambdaUpdate()
                .eq(ModelCallLog::getCallId, callId)
                .set(ModelCallLog::getStatus, ModelCallStatus.SUCCESS)
                .set(ModelCallLog::getPromptTokens, promptTokens)
                .set(ModelCallLog::getCompletionTokens, completionTokens)
                .set(ModelCallLog::getTotalTokens, totalTokens)
                .set(ModelCallLog::getDurationMs, durationMs)
                .update();
    }

    @Override
    public void markStreamSuccess(String callId, Integer promptTokens, Integer completionTokens,
                                  Integer totalTokens, TokenUsageSource tokenUsageSource,
                                  Long firstTokenLatencyMs, Integer chunkCount,
                                  Integer outputCharCount, Integer estimatedOutputTokens,
                                  long durationMs) {
        // 1. 使用 lambdaUpdate 按调用ID更新流式成功状态和观测字段
        this.lambdaUpdate()
                .eq(ModelCallLog::getCallId, callId)
                .set(ModelCallLog::getStatus, ModelCallStatus.SUCCESS)
                .set(ModelCallLog::getPromptTokens, promptTokens)
                .set(ModelCallLog::getCompletionTokens, completionTokens)
                .set(ModelCallLog::getTotalTokens, totalTokens)
                .set(ModelCallLog::getTokenUsageSource, tokenUsageSource)
                .set(ModelCallLog::getFirstTokenLatencyMs, firstTokenLatencyMs)
                .set(ModelCallLog::getChunkCount, chunkCount)
                .set(ModelCallLog::getOutputCharCount, outputCharCount)
                .set(ModelCallLog::getEstimatedOutputTokens, estimatedOutputTokens)
                .set(ModelCallLog::getDurationMs, durationMs)
                .update();
    }

    @Override
    public void markFailed(String callId, String errorCode, String errorMessage, long durationMs) {
        // 1. 使用 lambdaUpdate 按调用ID更新失败状态和错误信息
        this.lambdaUpdate()
                .eq(ModelCallLog::getCallId, callId)
                .set(ModelCallLog::getStatus, ModelCallStatus.FAILED)
                .set(ModelCallLog::getErrorCode, errorCode)
                .set(ModelCallLog::getErrorMessage, errorMessage)
                .set(ModelCallLog::getDurationMs, durationMs)
                .update();
    }

    @Override
    public void markTimeout(String callId, String errorCode, String errorMessage, long durationMs) {
        // 1. 使用 lambdaUpdate 按调用ID更新超时状态和错误信息
        this.lambdaUpdate()
                .eq(ModelCallLog::getCallId, callId)
                .set(ModelCallLog::getStatus, ModelCallStatus.TIMEOUT)
                .set(ModelCallLog::getErrorCode, errorCode)
                .set(ModelCallLog::getErrorMessage, errorMessage)
                .set(ModelCallLog::getDurationMs, durationMs)
                .update();
    }

    @Override
    public void markCanceled(String callId, long durationMs) {
        // 1. 使用 lambdaUpdate 按调用ID更新取消状态
        this.lambdaUpdate()
                .eq(ModelCallLog::getCallId, callId)
                .set(ModelCallLog::getStatus, ModelCallStatus.CANCELED)
                .set(ModelCallLog::getDurationMs, durationMs)
                .update();
    }
}
