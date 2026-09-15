package com.nexarag.model.execution;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.model.entity.ModelCallLog;
import com.nexarag.model.enums.TokenUsageSource;
import com.nexarag.model.execution.telemetry.GenerationTelemetryCollector;
import com.nexarag.model.gateway.chat.ChatModelStreamResponse;
import com.nexarag.model.governance.ModelGovernanceExecutor;
import com.nexarag.model.governance.ModelGovernanceResolver;
import com.nexarag.model.route.ModelRouteContext;
import com.nexarag.model.route.ModelRouteDecision;
import com.nexarag.model.route.ModelRoutePlan;
import com.nexarag.model.route.ModelRouter;
import com.nexarag.model.service.ModelCallLogService;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Signal;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.UNKNOWN_EXCEPTION;

/**
 * 模型执行模板，统一处理路由和调用日志。
 */
public class ModelExecutionTemplate {

    private final ModelRouter modelRouter;
    private final ModelCallLogService modelCallLogService;
    private final ModelGovernanceExecutor modelGovernanceExecutor;
    private final ModelGovernanceResolver modelGovernanceResolver;
    private final GenerationTelemetryCollector generationTelemetryCollector;

    /**
     * 创建模型执行模板。
     *
     * @param modelRouter         模型路由器
     * @param modelCallLogService 模型调用日志服务
     */
    public ModelExecutionTemplate(ModelRouter modelRouter, ModelCallLogService modelCallLogService) {
        this(modelRouter, modelCallLogService, new ModelGovernanceExecutor(), new ModelGovernanceResolver(), null);
    }

    /**
     * 创建带 Langfuse Generation 采集能力的模型执行模板。
     */
    public ModelExecutionTemplate(ModelRouter modelRouter, ModelCallLogService modelCallLogService,
                                  ModelGovernanceExecutor modelGovernanceExecutor,
                                  ModelGovernanceResolver modelGovernanceResolver,
                                  GenerationTelemetryCollector generationTelemetryCollector) {
        this.modelRouter = modelRouter;
        this.modelCallLogService = modelCallLogService;
        this.modelGovernanceExecutor = modelGovernanceExecutor;
        this.modelGovernanceResolver = modelGovernanceResolver;
        this.generationTelemetryCollector = generationTelemetryCollector;
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
        ModelRoutePlan plan = modelRouter.plan(new ModelRouteContext(command.routeKey(), false));
        return executeStreamPlan(command, plan);
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
        GenerationTelemetryCollector.ActiveGeneration generation = startGeneration(command, decision, log);

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
                    command.tokenUsageSourceExtractor().apply(response),
                    durationMs
            );
            completeGeneration(generation, command.promptTokenExtractor().applyAsInt(response),
                    command.completionTokenExtractor().applyAsInt(response), command.totalTokenExtractor().applyAsInt(response),
                    null);
            return response;
        } catch (Exception exception) {
            // 3. 记录失败结果并继续抛出异常
            long durationMs = Math.max(0, System.currentTimeMillis() - start);
            modelCallLogService.markFailed(log.getCallId(), exception.getClass().getSimpleName(),
                    exception.getMessage(), durationMs);
            failGeneration(generation, exception);
            throw new ModelExecutionAttemptException(exception, log.getCallId(), exception.getClass().getSimpleName());
        }
    }

    private <T> Flux<T> executeStreamPlan(ModelExecutionCommand<Flux<T>> command, ModelRoutePlan plan) {
        if (plan.candidates() == null || plan.candidates().isEmpty()) {
            throw new ServiceException("模型路由没有可用候选: " + command.routeKey());
        }
        return attemptStreamBeforeFirstChunk(command, plan, 0, null, null);
    }

    private <T> Flux<T> attemptStreamBeforeFirstChunk(ModelExecutionCommand<Flux<T>> command, ModelRoutePlan plan,
                                                      int index, String fallbackFromCallId, String fallbackReason) {
        ModelRouteDecision decision = plan.candidates().get(index);
        long start = System.currentTimeMillis();
        ModelCallLog log = modelCallLogService.createRunningLog(
                command.traceId(),
                command.bizType(),
                command.bizId(),
                decision.profileName(),
                decision.profile().getProvider(),
                decision.profile().getBaseUrl(),
                decision.profile().getModelName(),
                command.requestType(),
                index + 1,
                fallbackFromCallId,
                fallbackReason
        );
        GenerationTelemetryCollector.ActiveGeneration generation = startGeneration(command, decision, log);

        try {
            // 1. 执行业务传入的流式模型调用逻辑，并在首个信号处决定是否 fallback
            return command.executor().apply(decision)
                    .switchOnFirst((signal, flux) -> handleFirstStreamSignal(command, plan, index, log, generation, start,
                            signal, flux));
        } catch (Exception exception) {
            // 2. 处理流创建阶段直接抛出的异常，首个分片前允许继续尝试下一个候选
            markStreamFailed(log, start, exception);
            failGeneration(generation, exception);
            if (hasNextCandidate(plan, index)) {
                return attemptStreamBeforeFirstChunk(command, plan, index + 1, log.getCallId(),
                        exception.getClass().getSimpleName());
            }
            return Flux.error(exception);
        }
    }

    private <T> Flux<T> handleFirstStreamSignal(ModelExecutionCommand<Flux<T>> command, ModelRoutePlan plan,
                                                int index, ModelCallLog log, GenerationTelemetryCollector.ActiveGeneration generation,
                                                long start, Signal<? extends T> signal,
                                                Flux<T> flux) {
        if (signal.hasValue()) {
            // 1. 首个分片已经产生，锁定当前候选，后续错误不再 fallback
            return observeLockedStream(command, log, generation, start, flux);
        }
        if (signal.isOnComplete()) {
            // 2. 流在首个分片前正常结束，按空响应成功处理
            markStreamSuccess(log, start, null, 0, 0, 0, 0, 0, TokenUsageSource.ESTIMATED);
            completeGeneration(generation, null, null, null, null);
            return Flux.empty();
        }

        // 3. 首个分片前失败，记录当前候选失败并尝试下一个候选
        Throwable exception = signal.getThrowable();
        markStreamFailed(log, start, exception);
        failGeneration(generation, exception);
        if (hasNextCandidate(plan, index)) {
            return attemptStreamBeforeFirstChunk(command, plan, index + 1, log.getCallId(),
                    exception == null ? null : exception.getClass().getSimpleName());
        }
        return Flux.error(exception);
    }

    private <T> Flux<T> observeLockedStream(ModelExecutionCommand<Flux<T>> command, ModelCallLog log,
                                            GenerationTelemetryCollector.ActiveGeneration generation, long start,
                                            Flux<T> flux) {
        AtomicInteger chunkCount = new AtomicInteger();
        AtomicInteger outputCharCount = new AtomicInteger();
        AtomicInteger promptTokens = new AtomicInteger(-1);
        AtomicInteger completionTokens = new AtomicInteger(-1);
        AtomicInteger totalTokens = new AtomicInteger(-1);
        AtomicLong firstTokenLatencyMs = new AtomicLong(-1L);

        return flux
                .doOnNext(chunk -> {
                    // 1. 记录首个文本分片耗时和流式输出规模
                    if (hasContent(chunk)) {
                        chunkCount.incrementAndGet();
                        firstTokenLatencyMs.compareAndSet(-1L, Math.max(0, System.currentTimeMillis() - start));// 首个分片耗时已记录
                        outputCharCount.addAndGet(outputCharCount(chunk));
                    }
                    // 2. 记录流式响应中最新的厂商 Token 用量
                    recordStreamTokenUsage(chunk, promptTokens, completionTokens, totalTokens);
                })
                .doOnComplete(() -> markStreamSuccess(log, start, firstTokenLatencyMs.get(),
                        chunkCount.get(), outputCharCount.get(), tokenValue(promptTokens),
                        tokenValue(completionTokens), tokenValue(totalTokens), tokenUsageSource(totalTokens)))
                .doOnComplete(() -> completeGeneration(generation, tokenValue(promptTokens), tokenValue(completionTokens),
                        tokenValue(totalTokens), firstTokenLatencyMs.get() < 0 ? null : firstTokenLatencyMs.get()))
                .doOnCancel(() -> {
                    markStreamCanceled(log, start);
                    cancelGeneration(generation);
                })
                .doOnError(exception -> {
                    markStreamFailed(log, start, exception);
                    failGeneration(generation, exception);
                })
                .filter(this::shouldEmitStreamChunk);
    }

    private void markStreamSuccess(ModelCallLog log, long start, Long firstTokenLatencyMs,
                                   Integer chunkCount, Integer outputCharCount, Integer promptTokens,
                                   Integer completionTokens, Integer totalTokens, TokenUsageSource tokenUsageSource) {
        // 1. 记录流式调用成功结果和 Token 用量
        long durationMs = Math.max(0, System.currentTimeMillis() - start);
        modelCallLogService.markStreamSuccess(
                log.getCallId(),
                promptTokens,
                completionTokens,
                totalTokens,
                tokenUsageSource,
                firstTokenLatencyMs == null || firstTokenLatencyMs < 0 ? null : firstTokenLatencyMs,
                chunkCount,
                outputCharCount,
                0,
                durationMs
        );
    }

    private void markStreamFailed(ModelCallLog log, long start, Throwable exception) {
        // 1. 记录流式调用失败信息
        long durationMs = Math.max(0, System.currentTimeMillis() - start);
        if (exception instanceof TimeoutException) {
            modelCallLogService.markTimeout(log.getCallId(), exception.getClass().getSimpleName(),
                    exception.getMessage(), durationMs);
            return;
        }
        modelCallLogService.markFailed(log.getCallId(),
                exception == null ? UNKNOWN_EXCEPTION : exception.getClass().getSimpleName(),
                exception == null ? null : exception.getMessage(), durationMs);
    }

    private void markStreamCanceled(ModelCallLog log, long start) {
        // 1. 记录客户端取消流式调用
        long durationMs = Math.max(0, System.currentTimeMillis() - start);
        modelCallLogService.markCanceled(log.getCallId(), durationMs);
    }

    private ModelRouteDecision firstCandidate(ModelRoutePlan plan, String routeKey) {
        if (plan.candidates() == null || plan.candidates().isEmpty()) {
            throw new ServiceException("模型路由没有可用候选: " + routeKey);
        }
        return plan.candidates().getFirst();
    }

    private boolean hasNextCandidate(ModelRoutePlan plan, int index) {
        return plan.candidates() != null && index + 1 < plan.candidates().size();
    }

    private int outputCharCount(Object chunk) {
        if (chunk instanceof ChatModelStreamResponse response && response.content() != null) {
            return response.content().length();
        }
        return 0;
    }

    private boolean hasContent(Object chunk) {
        return chunk instanceof ChatModelStreamResponse response && StringUtils.hasText(response.content());
    }

    private boolean shouldEmitStreamChunk(Object chunk) {
        if (!(chunk instanceof ChatModelStreamResponse response)) {
            return true;
        }
        // 1. 只过滤用于内部统计的 Token 分片，避免向前端输出空文本分片
        return StringUtils.hasText(response.content())
                || StringUtils.hasText(response.finishReason())
                || StringUtils.hasText(response.errorCode())
                || StringUtils.hasText(response.errorMessage());
    }

    private void recordStreamTokenUsage(Object chunk, AtomicInteger promptTokens, AtomicInteger completionTokens,
                                        AtomicInteger totalTokens) {
        if (!(chunk instanceof ChatModelStreamResponse response)) {
            return;
        }
        // 1. 使用流式响应中最新的非空 Token 用量覆盖旧值
        updateTokenValue(promptTokens, response.promptTokens());
        updateTokenValue(completionTokens, response.completionTokens());
        updateTokenValue(totalTokens, response.totalTokens());
    }

    private void updateTokenValue(AtomicInteger holder, Integer value) {
        if (value != null) {
            holder.set(value);
        }
    }

    private Integer tokenValue(AtomicInteger holder) {
        return holder.get() < 0 ? null : holder.get();
    }

    private TokenUsageSource tokenUsageSource(AtomicInteger totalTokens) {
        return totalTokens.get() < 0 ? TokenUsageSource.UNKNOWN : TokenUsageSource.PROVIDER_USAGE;
    }

    private GenerationTelemetryCollector.ActiveGeneration startGeneration(ModelExecutionCommand<?> command,
                                                                          ModelRouteDecision decision, ModelCallLog log) {
        return generationTelemetryCollector == null ? null : generationTelemetryCollector.start(command, decision, log);
    }

    private void completeGeneration(GenerationTelemetryCollector.ActiveGeneration generation, Integer inputTokens,
                                    Integer outputTokens, Integer totalTokens, Long firstTokenMs) {
        if (generation != null) {
            generation.complete(inputTokens, outputTokens, totalTokens, firstTokenMs);
        }
    }

    private void failGeneration(GenerationTelemetryCollector.ActiveGeneration generation, Throwable throwable) {
        if (generation != null) {
            generation.fail(throwable);
        }
    }

    private void cancelGeneration(GenerationTelemetryCollector.ActiveGeneration generation) {
        if (generation != null) {
            generation.cancel();
        }
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
