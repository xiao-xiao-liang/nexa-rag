package com.nexarag.infra.observability.langfuse.aop;

import com.nexarag.infra.observability.langfuse.LangfuseTelemetry;
import com.nexarag.infra.observability.langfuse.LangfuseTraceScope;
import com.nexarag.infra.observability.langfuse.model.LangfuseTraceCommand;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

import static com.nexarag.infra.observability.langfuse.constants.LangfuseContextConstant.REACTOR_OTEL_CONTEXT_KEY;

/**
 * 将 Langfuse Trace 注解适配为同步与 Reactor 流的真实生命周期。
 */
@Aspect
@Component
@Order(0)
public class LangfuseTelemetryAspect {

    /** Reactor Context 中存放根 Trace OTel 上下文的键。 */
    public static final String OTEL_CONTEXT_KEY = REACTOR_OTEL_CONTEXT_KEY;

    private final LangfuseTelemetry telemetry;
    private final LangfuseExpressionResolver expressionResolver;

    /**
     * 创建遥测切面。
     *
     * @param telemetry          遥测门面
     * @param expressionResolver SpEL 解析器
     */
    public LangfuseTelemetryAspect(LangfuseTelemetry telemetry, LangfuseExpressionResolver expressionResolver) {
        this.telemetry = telemetry;
        this.expressionResolver = expressionResolver;
    }

    /**
     * 拦截根 Trace 注解，并在流订阅而非方法返回时创建 Trace。
     *
     * @param joinPoint 连接点
     * @param annotation Trace 注解
     * @return 原始返回值或具备遥测生命周期的包装流
     * @throws Throwable 业务方法异常
     */
    @Around("@annotation(annotation)")
    public Object trace(ProceedingJoinPoint joinPoint, LangfuseTrace annotation) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        LangfuseTraceCommand command = new LangfuseTraceCommand(annotation.name(),
                stringValue(expressionResolver.resolve(annotation.correlationId(), method, joinPoint.getArgs(),
                        joinPoint.getTarget())),
                stringValue(expressionResolver.resolve(annotation.sessionId(), method, joinPoint.getArgs(),
                        joinPoint.getTarget())),
                stringValue(expressionResolver.resolve(annotation.userId(), method, joinPoint.getArgs(),
                        joinPoint.getTarget())),
                expressionResolver.resolveAttributes(annotation.attributes(), method, joinPoint.getArgs(),
                        joinPoint.getTarget()));
        if (Flux.class.isAssignableFrom(method.getReturnType())) {
            return Flux.defer(() -> proceedFlux(joinPoint, command));
        }
        if (Mono.class.isAssignableFrom(method.getReturnType())) {
            return Mono.defer(() -> proceedMono(joinPoint, command));
        }
        LangfuseTraceScope scope = telemetry.startTrace(command);
        try {
            return joinPoint.proceed();
        } catch (Throwable exception) {
            scope.fail(exception);
            throw exception;
        } finally {
            scope.close();
        }
    }

    @SuppressWarnings("unchecked")
    private Flux<Object> proceedFlux(ProceedingJoinPoint joinPoint, LangfuseTraceCommand command) {
        LangfuseTraceScope scope = telemetry.startTrace(command);
        try {
            return ((Flux<Object>) joinPoint.proceed())
                    .contextWrite(context -> context.put(OTEL_CONTEXT_KEY, scope.context()))
                    .doOnError(scope::fail)
                    .doFinally(ignored -> scope.close());
        } catch (Throwable exception) {
            scope.fail(exception);
            scope.close();
            return Flux.error(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Mono<Object> proceedMono(ProceedingJoinPoint joinPoint, LangfuseTraceCommand command) {
        LangfuseTraceScope scope = telemetry.startTrace(command);
        try {
            return ((Mono<Object>) joinPoint.proceed())
                    .contextWrite(context -> context.put(OTEL_CONTEXT_KEY, scope.context()))
                    .doOnError(scope::fail)
                    .doFinally(ignored -> scope.close());
        } catch (Throwable exception) {
            scope.fail(exception);
            scope.close();
            return Mono.error(exception);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
