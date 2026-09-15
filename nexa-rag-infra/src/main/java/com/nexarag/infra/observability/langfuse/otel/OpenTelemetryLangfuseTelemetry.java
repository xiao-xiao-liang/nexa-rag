package com.nexarag.infra.observability.langfuse.otel;

import com.nexarag.infra.observability.langfuse.LangfuseGenerationScope;
import com.nexarag.infra.observability.langfuse.LangfuseSpanScope;
import com.nexarag.infra.observability.langfuse.LangfuseTelemetry;
import com.nexarag.infra.observability.langfuse.LangfuseTraceScope;
import com.nexarag.infra.observability.langfuse.model.LangfuseGenerationCommand;
import com.nexarag.infra.observability.langfuse.model.LangfuseGenerationResult;
import com.nexarag.infra.observability.langfuse.model.LangfuseObservationType;
import com.nexarag.infra.observability.langfuse.model.LangfuseTraceCommand;
import com.nexarag.infra.observability.langfuse.model.LangfuseTraceContextAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.CANCELED_STATE;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.ENVIRONMENT;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.ERROR_STATE;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.GEN_AI_CHAT_OPERATION;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.GEN_AI_FIRST_TOKEN_LATENCY_MS;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.GEN_AI_INPUT_TOKENS;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.GEN_AI_OPERATION_NAME;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.GEN_AI_OUTPUT_TOKENS;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.GEN_AI_PROVIDER_NAME;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.GEN_AI_REQUEST_MODEL;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.GEN_AI_TOTAL_TOKENS;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.GENERATION_ID_ATTRIBUTE;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.MODEL_CALL_ID_ATTRIBUTE;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.OBSERVATION_TYPE;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.OPENINFERENCE_LLM;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.OPENINFERENCE_SPAN_KIND;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.SESSION_ID;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.TERMINAL_STATE_ATTRIBUTE;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.TRACE_ID_ATTRIBUTE;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.TRACE_NAME;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.UNKNOWN_EXCEPTION;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.USER_ID;

/**
 * 基于 OpenTelemetry API 的 Langfuse 遥测实现。
 */
public class OpenTelemetryLangfuseTelemetry implements LangfuseTelemetry {

    private final Tracer tracer;
    private final String environment;

    /**
     * 创建 OpenTelemetry 遥测实现。
     *
     * @param tracer      专用 Langfuse Tracer
     * @param environment 当前部署环境
     */
    public OpenTelemetryLangfuseTelemetry(Tracer tracer, String environment) {
        this.tracer = tracer;
        this.environment = environment;
    }

    @Override
    public LangfuseTraceScope startTrace(LangfuseTraceCommand command) {
        Context parentContext = Context.current();
        Span span = tracer.spanBuilder(command.name())
                .setParent(parentContext)
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
        applyTraceAttributes(span, command);
        return new OpenTelemetryTraceScope(span, LangfuseTraceContextAttributes.attach(parentContext.with(span),
                LangfuseTraceContextAttributes.from(command)));
    }

    @Override
    public LangfuseGenerationScope startGeneration(LangfuseGenerationCommand command, Context parentContext) {
        Context resolvedParent = parentContext == null ? Context.root() : parentContext;
        Span span = tracer.spanBuilder(command.name())
                .setParent(resolvedParent)
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();
        applyPropagatedTraceAttributes(span, resolvedParent);
        span.setAttribute(ENVIRONMENT, environment);
        span.setAttribute(OBSERVATION_TYPE, LangfuseObservationType.GENERATION.value());
        span.setAttribute(OPENINFERENCE_SPAN_KIND, OPENINFERENCE_LLM);
        span.setAttribute(GEN_AI_OPERATION_NAME, GEN_AI_CHAT_OPERATION);
        setIfHasText(span, GEN_AI_REQUEST_MODEL, command.modelName());
        setIfHasText(span, GEN_AI_PROVIDER_NAME, command.provider());
        setIfHasText(span, MODEL_CALL_ID_ATTRIBUTE, command.callId());
        setIfHasText(span, GENERATION_ID_ATTRIBUTE, command.generationId());
        setIfHasText(span, TRACE_ID_ATTRIBUTE, command.correlationId());
        command.attributes().forEach((key, value) -> setSupportedAttribute(span, key, value));
        return new OpenTelemetryGenerationScope(span, resolvedParent.with(span));
    }

    @Override
    public LangfuseSpanScope startSpan(String name, LangfuseObservationType type,
                                       Map<String, Object> attributes, Context parentContext) {
        Context resolvedParent = parentContext == null ? Context.root() : parentContext;
        Span span = tracer.spanBuilder(name)
                .setParent(resolvedParent)
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
        applyPropagatedTraceAttributes(span, resolvedParent);
        span.setAttribute(ENVIRONMENT, environment);
        span.setAttribute(OBSERVATION_TYPE, (type == null ? LangfuseObservationType.SPAN : type).value());
        if (attributes != null) {
            attributes.forEach((key, value) -> setSupportedAttribute(span, key, value));
        }
        return new OpenTelemetrySpanScope(span, resolvedParent.with(span));
    }

    private void applyTraceAttributes(Span span, LangfuseTraceCommand command) {
        span.setAttribute(TRACE_NAME, command.name());
        span.setAttribute(ENVIRONMENT, environment);
        span.setAttribute(OBSERVATION_TYPE, LangfuseObservationType.SPAN.value());
        setIfHasText(span, USER_ID, command.userId());
        setIfHasText(span, SESSION_ID, command.sessionId());
        setIfHasText(span, TRACE_ID_ATTRIBUTE, command.correlationId());
        command.attributes().forEach((key, value) -> setSupportedAttribute(span, key, value));
    }

    /**
     * 将根 Trace 的安全身份属性复制到子观察，满足 Langfuse v4 按 Observation 筛选和聚合的要求。
     *
     * <p>属性仅保存于当前进程的 OTel Context，不使用会跨 HTTP 边界传播的 Baggage，避免用户和会话标识传递给模型服务。</p>
     *
     * @param span          当前子观察
     * @param parentContext 父上下文
     */
    private void applyPropagatedTraceAttributes(Span span, Context parentContext) {
        LangfuseTraceContextAttributes identity = LangfuseTraceContextAttributes.from(parentContext);
        if (identity == null) {
            return;
        }
        setIfHasText(span, TRACE_NAME, identity.traceName());
        setIfHasText(span, USER_ID, identity.userId());
        setIfHasText(span, SESSION_ID, identity.sessionId());
        setIfHasText(span, ENVIRONMENT, environment);
        setIfHasText(span, TRACE_ID_ATTRIBUTE, identity.correlationId());
    }

    private void setIfHasText(Span span, String key, String value) {
        if (StringUtils.hasText(value)) {
            span.setAttribute(key, value);
        }
    }

    private void setSupportedAttribute(Span span, String key, Object value) {
        if (!StringUtils.hasText(key) || value == null) {
            return;
        }
        if (value instanceof String stringValue) {
            span.setAttribute(AttributeKey.stringKey(key), stringValue);
        } else if (value instanceof Boolean booleanValue) {
            span.setAttribute(AttributeKey.booleanKey(key), booleanValue);
        } else if (value instanceof Integer integerValue) {
            span.setAttribute(AttributeKey.longKey(key), integerValue.longValue());
        } else if (value instanceof Long longValue) {
            span.setAttribute(AttributeKey.longKey(key), longValue);
        } else if (value instanceof Float floatValue) {
            span.setAttribute(AttributeKey.doubleKey(key), floatValue.doubleValue());
        } else if (value instanceof Double doubleValue) {
            span.setAttribute(AttributeKey.doubleKey(key), doubleValue);
        }
    }

    private static final class OpenTelemetryTraceScope implements LangfuseTraceScope {

        private final Span span;
        private final Context context;
        private final AtomicBoolean closed = new AtomicBoolean();

        private OpenTelemetryTraceScope(Span span, Context context) {
            this.span = span;
            this.context = context;
        }

        @Override
        public Context context() {
            return context;
        }

        @Override
        public void fail(Throwable throwable) {
            if (!closed.get()) {
                span.setStatus(StatusCode.ERROR, throwable == null ? UNKNOWN_EXCEPTION
                        : throwable.getClass().getSimpleName());
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                span.end();
            }
        }
    }

    private final class OpenTelemetryGenerationScope implements LangfuseGenerationScope {

        private final Span span;
        private final Context context;
        private final AtomicBoolean closed = new AtomicBoolean();

        private OpenTelemetryGenerationScope(Span span, Context context) {
            this.span = span;
            this.context = context;
        }

        @Override
        public Context context() {
            return context;
        }

        @Override
        public void finish(LangfuseGenerationResult result) {
            if (closed.compareAndSet(false, true)) {
                setInteger(span, GEN_AI_INPUT_TOKENS, result.inputTokens());
                setInteger(span, GEN_AI_OUTPUT_TOKENS, result.outputTokens());
                setInteger(span, GEN_AI_TOTAL_TOKENS, result.totalTokens());
                setLong(span, GEN_AI_FIRST_TOKEN_LATENCY_MS, result.firstTokenMs());
                setIfHasText(span, TERMINAL_STATE_ATTRIBUTE, result.terminalState());
                result.attributes().forEach((key, value) -> setSupportedAttribute(span, key, value));
                if (ERROR_STATE.equals(result.terminalState())) {
                    span.setStatus(StatusCode.ERROR);
                }
                if (result.finishedAt() == null) {
                    span.end();
                } else {
                    span.end(result.finishedAt());
                }
            }
        }

        @Override
        public void fail(Throwable throwable) {
            if (closed.compareAndSet(false, true)) {
                span.setStatus(StatusCode.ERROR, throwable == null ? UNKNOWN_EXCEPTION
                        : throwable.getClass().getSimpleName());
                span.end();
            }
        }

        @Override
        public void close() {
            finish(new LangfuseGenerationResult(null, null, null, null, CANCELED_STATE, Map.of()));
        }
    }

    private final class OpenTelemetrySpanScope implements LangfuseSpanScope {

        private final Span span;
        private final Context context;
        private final AtomicBoolean closed = new AtomicBoolean();

        private OpenTelemetrySpanScope(Span span, Context context) {
            this.span = span;
            this.context = context;
        }

        @Override
        public Context context() {
            return context;
        }

        @Override
        public void addAttributes(Map<String, Object> attributes) {
            if (attributes != null && !closed.get()) {
                attributes.forEach((key, value) -> setSupportedAttribute(span, key, value));
            }
        }

        @Override
        public void fail(Throwable throwable) {
            if (!closed.get()) {
                span.setStatus(StatusCode.ERROR, throwable == null ? UNKNOWN_EXCEPTION
                        : throwable.getClass().getSimpleName());
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                span.end();
            }
        }
    }

    private void setInteger(Span span, String key, Integer value) {
        if (value != null) {
            span.setAttribute(key, value.longValue());
        }
    }

    private void setLong(Span span, String key, Long value) {
        if (value != null) {
            span.setAttribute(key, value);
        }
    }

}
