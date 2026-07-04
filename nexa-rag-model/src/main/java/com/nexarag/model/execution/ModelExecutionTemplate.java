package com.nexarag.model.execution;

import com.nexarag.model.entity.ModelCallLog;
import com.nexarag.model.route.ModelRouteContext;
import com.nexarag.model.route.ModelRouteDecision;
import com.nexarag.model.route.ModelRouter;
import com.nexarag.model.service.ModelCallLogService;

/**
 * 模型执行模板，统一处理路由和调用日志。
 */
public class ModelExecutionTemplate {

    private final ModelRouter modelRouter;
    private final ModelCallLogService modelCallLogService;

    public ModelExecutionTemplate(ModelRouter modelRouter, ModelCallLogService modelCallLogService) {
        this.modelRouter = modelRouter;
        this.modelCallLogService = modelCallLogService;
    }

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
}
