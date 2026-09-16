package com.nexarag.infra.observability.langfuse.aop;

import com.nexarag.infra.observability.langfuse.LangfuseSpanScope;
import com.nexarag.infra.observability.langfuse.LangfuseTelemetry;
import com.nexarag.infra.observability.langfuse.otel.LangfuseOtelContextCodec;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 处理同步 Langfuse 子观察的声明式生命周期。
 */
@Aspect
@Component
public class LangfuseSpanAspect {

    private final LangfuseTelemetry telemetry;
    private final LangfuseExpressionResolver expressionResolver;

    public LangfuseSpanAspect(LangfuseTelemetry telemetry, LangfuseExpressionResolver expressionResolver) {
        this.telemetry = telemetry;
        this.expressionResolver = expressionResolver;
    }

    @Around("@annotation(annotation)")
    public Object span(ProceedingJoinPoint joinPoint, LangfuseSpan annotation) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object carrierValue = expressionResolver.resolve(annotation.parentContextCarrier(), method, joinPoint.getArgs(),
                joinPoint.getTarget());
        String carrier = carrierValue instanceof String value ? value : "";
        LangfuseSpanScope scope = telemetry.startSpan(annotation.name(), annotation.type(),
                expressionResolver.resolveAttributes(annotation.attributes(), method, joinPoint.getArgs(),
                        joinPoint.getTarget()),
                LangfuseOtelContextCodec.decode(carrier));
        try {
            Object result = joinPoint.proceed();
            scope.addAttributes(expressionResolver.resolveResultAttributes(annotation.resultAttributes(), result));
            return result;
        } catch (RuntimeException exception) {
            scope.fail(exception);
            throw exception;
        } finally {
            scope.close();
        }
    }
}
