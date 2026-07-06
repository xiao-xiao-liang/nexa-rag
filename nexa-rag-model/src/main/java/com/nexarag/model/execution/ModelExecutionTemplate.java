package com.nexarag.model.execution;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.model.entity.ModelCallLog;
import com.nexarag.model.governance.ModelGovernanceExecutor;
import com.nexarag.model.governance.ModelGovernanceResolver;
import com.nexarag.model.route.ModelRouteContext;
import com.nexarag.model.route.ModelRouteDecision;
import com.nexarag.model.route.ModelRoutePlan;
import com.nexarag.model.route.ModelRouter;
import com.nexarag.model.service.ModelCallLogService;
import reactor.core.publisher.Flux;

/**
 * 模型执行模板，统一处理路由和调用日志。
 */
public class ModelExecutionTemplate {

    private final ModelRouter modelRouter;
    private final ModelCallLogService modelCallLogService;
    private final ModelGovernanceExecutor modelGovernanceExecutor;
    private final ModelGovernanceResolver modelGovernanceResolver;

    /**
     * 创建模型执行模板。
     *
     * @param modelRouter         模型路由器
     * @param modelCallLogService 模型调用日志服务
     */
    public ModelExecutionTemplate(ModelRouter modelRouter, ModelCallLogService modelCallLogService) {
        this(modelRouter, modelCallLogService, new ModelGovernanceExecutor(), new ModelGovernanceResolver());
    }

    /**
     * 创建模型执行模板。
     *
     * @param modelRouter             模型路由器
     * @param modelCallLogService     模型调用日志服务
     * @param modelGovernanceExecutor 模型治理执行器
     * @param modelGovernanceResolver 模型治理配置解析器
     */
    public ModelExecutionTemplate(ModelRouter modelRouter, ModelCallLogService modelCallLogService,
                                  ModelGovernanceExecutor modelGovernanceExecutor,
                                  ModelGovernanceResolver modelGovernanceResolver) {
        this.modelRouter = modelRouter;
        this.modelCallLogService = modelCallLogService;
        this.modelGovernanceExecutor = modelGovernanceExecutor;
        this.modelGovernanceResolver = modelGovernanceResolver;
    }

    /**
     * 执行模型调用。
     *
     * @param command 模型执行命令
     * @param <T>     模型响应类型
     * @return 模型响应
     */
    public <T> T execute(ModelExecutionCommand<T> command) {
        // 1. 获取候选模型链并逐个尝试
        ModelRoutePlan plan = modelRouter.plan(new ModelRouteContext(command.routeKey(), false));
        return executePlan(command, plan);
    }

    /**
     * 按指定路由决策直接执行模型调用。
     *
     * @param command  模型执行命令
     * @param decision 指定路由决策
     * @param <T>      模型响应类型
     * @return 模型响应
     */
    public <T> T execute(ModelExecutionCommand<T> command, ModelRouteDecision decision) {
        long start = System.currentTimeMillis();
        return execute(command, decision, start);
    }

    /**
     * 执行流式模型调用。
     *
     * @param command 流式模型执行命令
     * @param <T>     流式响应分片类型
     * @return 流式响应分片
     */
    public <T> Flux<T> executeStream(ModelExecutionCommand<Flux<T>> command) {
        long start = System.currentTimeMillis();
        ModelRoutePlan plan = modelRouter.plan(new ModelRouteContext(command.routeKey(), false));
        ModelRouteDecision decision = firstCandidate(plan, command.routeKey());
        return executeStream(command, decision, start);
    }

    private <T> T executePlan(ModelExecutionCommand<T> command, ModelRoutePlan plan) {
        Exception lastException = null;
        String fallbackFromCallId = null;
        String fallbackReason = null;
        for (int index = 0; index < plan.candidates().size(); index++) {
            ModelRouteDecision decision = plan.candidates().get(index);
            long start = System.currentTimeMillis();
            try {
                // 1. 按候选模型执行调用，失败后进入下一个候选
                return execute(command, decision, start, index + 1, fallbackFromCallId, fallbackReason);
            } catch (Exception exception) {
                ModelExecutionAttemptException attemptException = unwrapAttemptException(exception);
                lastException = attemptException.originalException();
                fallbackFromCallId = attemptException.callId();
                fallbackReason = attemptException.reason();
            }
        }
        if (lastException instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new ServiceException("模型路由全部候选调用失败: " + command.routeKey(), lastException,
                com.nexarag.common.error.BaseErrorCode.SERVICE_ERROR);
    }

    private <T> T execute(ModelExecutionCommand<T> command, ModelRouteDecision decision, long start) {
        return execute(command, decision, start, 1, null, null);
    }

    private <T> T execute(ModelExecutionCommand<T> command, ModelRouteDecision decision, long start,
                          Integer attemptNo, String fallbackFromCallId, String fallbackReason) {
        ModelCallLog log = modelCallLogService.createRunningLog(
                command.traceId(),
                command.bizType(),
                command.bizId(),
                decision.profileName(),
                decision.profile().getProvider(),
                decision.profile().getBaseUrl(),
                decision.profile().getModelName(),
                command.requestType(),
                attemptNo,
                fallbackFromCallId,
                fallbackReason
        );

        try {
            // 1. 执行业务传入的模型调用逻辑
            T response = modelGovernanceExecutor.execute(decision.profileName(),
                    modelGovernanceResolver.resolve(decision),
                    () -> command.executor().apply(decision));

            // 2. 记录成功结果
            long durationMs = Math.max(0, System.currentTimeMillis() - start);
            modelCallLogService.markSuccess(
                    log.getCallId(),
                    command.promptTokenExtractor().applyAsInt(response),
                    command.completionTokenExtractor().applyAsInt(response),
                    command.totalTokenExtractor().applyAsInt(response),
                    durationMs
            );
            return response;
        } catch (Exception exception) {
            // 3. 记录失败结果并继续抛出异常
            long durationMs = Math.max(0, System.currentTimeMillis() - start);
            modelCallLogService.markFailed(log.getCallId(), exception.getClass().getSimpleName(),
                    exception.getMessage(), durationMs);
            throw new ModelExecutionAttemptException(exception, log.getCallId(), exception.getClass().getSimpleName());
        }
    }

    private <T> Flux<T> executeStream(ModelExecutionCommand<Flux<T>> command, ModelRouteDecision decision, long start) {
        ModelCallLog log = modelCallLogService.createRunningLog(
                command.traceId(),
                command.bizType(),
                command.bizId(),
                decision.profileName(),
                decision.profile().getProvider(),
                decision.profile().getBaseUrl(),
                decision.profile().getModelName(),
                command.requestType(),
                1,
                null,
                null
        );

        try {
            // 1. 执行业务传入的流式模型调用逻辑
            return command.executor().apply(decision)
                    .doOnComplete(() -> markStreamSuccess(command, log, start))
                    .doOnError(exception -> markStreamFailed(log, start, exception));
        } catch (Exception exception) {
            // 2. 处理流创建阶段直接抛出的异常
            markStreamFailed(log, start, exception);
            throw exception;
        }
    }

    private <T> void markStreamSuccess(ModelExecutionCommand<Flux<T>> command, ModelCallLog log, long start) {
        // 1. 流式 Chat 暂记 Token 为 0，精确统计后续单独实现
        long durationMs = Math.max(0, System.currentTimeMillis() - start);
        modelCallLogService.markSuccess(
                log.getCallId(),
                0,
                0,
                0,
                durationMs
        );
    }

    private void markStreamFailed(ModelCallLog log, long start, Throwable exception) {
        // 1. 记录流式调用失败信息
        long durationMs = Math.max(0, System.currentTimeMillis() - start);
        modelCallLogService.markFailed(log.getCallId(), exception.getClass().getSimpleName(),
                exception.getMessage(), durationMs);
    }

    private ModelRouteDecision firstCandidate(ModelRoutePlan plan, String routeKey) {
        if (plan.candidates() == null || plan.candidates().isEmpty()) {
            throw new ServiceException("模型路由没有可用候选: " + routeKey);
        }
        return plan.candidates().getFirst();
    }

    private ModelExecutionAttemptException unwrapAttemptException(Exception exception) {
        if (exception instanceof ModelExecutionAttemptException attemptException) {
            return attemptException;
        }
        return new ModelExecutionAttemptException(exception, null, exception.getClass().getSimpleName());
    }

    /**
     * 模型单次候选调用异常，携带当前调用日志ID供后续 fallback 记录使用。
     */
    private static class ModelExecutionAttemptException extends RuntimeException {

        private final Exception originalException;
        private final String callId;
        private final String reason;

        private ModelExecutionAttemptException(Exception originalException, String callId, String reason) {
            super(originalException);
            this.originalException = originalException;
            this.callId = callId;
            this.reason = reason;
        }

        private Exception originalException() {
            return originalException;
        }

        private String callId() {
            return callId;
        }

        private String reason() {
            return reason;
        }
    }
}
