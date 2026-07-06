package com.nexarag.model.execution;

import com.nexarag.model.entity.ModelCallLog;
import com.nexarag.model.route.ModelRouteContext;
import com.nexarag.model.route.ModelRouteDecision;
import com.nexarag.model.route.ModelRouter;
import com.nexarag.model.service.ModelCallLogService;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * 模型执行模板，统一处理路由和调用日志。
 */
@RequiredArgsConstructor
public class ModelExecutionTemplate {

    private final ModelRouter modelRouter;
    private final ModelCallLogService modelCallLogService;

    /**
     * 执行模型调用。
     *
     * @param command 模型执行命令
     * @param <T>     模型响应类型
     * @return 模型响应
     */
    public <T> T execute(ModelExecutionCommand<T> command) {
        long start = System.currentTimeMillis();
        ModelRouteDecision decision = modelRouter.route(new ModelRouteContext(command.routeKey(), false));
        return execute(command, decision, start);
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
        ModelRouteDecision decision = modelRouter.route(new ModelRouteContext(command.routeKey(), false));
        return executeStream(command, decision, start);
    }

    private <T> T execute(ModelExecutionCommand<T> command, ModelRouteDecision decision, long start) {
        ModelCallLog log = modelCallLogService.createRunningLog(
                command.traceId(),
                command.bizType(),
                command.bizId(),
                decision.profileName(),
                decision.profile().getProvider(),
                decision.profile().getBaseUrl(),
                decision.profile().getModelName(),
                command.requestType()
        );

        try {
            // 1. 执行业务传入的模型调用逻辑
            T response = command.executor().apply(decision);

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
            throw exception;
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
                command.requestType()
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
}
