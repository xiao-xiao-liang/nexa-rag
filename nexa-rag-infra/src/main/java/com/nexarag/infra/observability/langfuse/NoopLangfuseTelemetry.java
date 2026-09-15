package com.nexarag.infra.observability.langfuse;

import com.nexarag.infra.observability.langfuse.model.LangfuseGenerationCommand;
import com.nexarag.infra.observability.langfuse.model.LangfuseGenerationResult;
import com.nexarag.infra.observability.langfuse.model.LangfuseObservationType;
import com.nexarag.infra.observability.langfuse.model.LangfuseTraceCommand;
import io.opentelemetry.context.Context;

import java.util.Map;

/**
 * Langfuse 关闭时使用的无操作实现。
 */
public enum NoopLangfuseTelemetry implements LangfuseTelemetry {

    /**
     * 单例实例。
     */
    INSTANCE;

    private static final LangfuseTraceScope SCOPE = new LangfuseTraceScope() {
        @Override
        public Context context() {
            return Context.root();
        }

        @Override
        public void fail(Throwable throwable) {
            // 无操作实现无需记录异常。
        }

        @Override
        public void close() {
            // 无操作实现无需关闭资源。
        }
    };
    private static final LangfuseGenerationScope GENERATION_SCOPE = new LangfuseGenerationScope() {
        @Override
        public Context context() {
            return Context.root();
        }

        @Override
        public void finish(LangfuseGenerationResult result) {
            // 无操作实现无需结束资源。
        }

        @Override
        public void fail(Throwable throwable) {
            // 无操作实现无需结束资源。
        }

        @Override
        public void close() {
            // 无操作实现无需结束资源。
        }
    };
    private static final LangfuseSpanScope SPAN_SCOPE = new LangfuseSpanScope() {
        @Override
        public Context context() {
            return Context.root();
        }

        @Override
        public void addAttributes(Map<String, Object> attributes) {
            // 无操作实现无需记录属性。
        }

        @Override
        public void fail(Throwable throwable) {
            // 无操作实现无需记录异常。
        }

        @Override
        public void close() {
            // 无操作实现无需关闭资源。
        }
    };

    @Override
    public LangfuseTraceScope startTrace(LangfuseTraceCommand command) {
        return SCOPE;
    }

    @Override
    public LangfuseGenerationScope startGeneration(LangfuseGenerationCommand command, Context parentContext) {
        return GENERATION_SCOPE;
    }

    @Override
    public LangfuseSpanScope startSpan(String name, LangfuseObservationType type,
                                       Map<String, Object> attributes, Context parentContext) {
        return SPAN_SCOPE;
    }
}
