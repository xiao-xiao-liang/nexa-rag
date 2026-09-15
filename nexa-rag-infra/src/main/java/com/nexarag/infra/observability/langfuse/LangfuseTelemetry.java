package com.nexarag.infra.observability.langfuse;

import com.nexarag.infra.observability.langfuse.model.LangfuseGenerationCommand;
import com.nexarag.infra.observability.langfuse.model.LangfuseObservationType;
import com.nexarag.infra.observability.langfuse.model.LangfuseTraceCommand;
import io.opentelemetry.context.Context;

import java.util.Map;

/**
 * Langfuse 遥测门面，隔离业务模块与 OpenTelemetry exporter 配置。
 */
public interface LangfuseTelemetry {

    /**
     * 创建一次业务请求对应的根 Trace。
     *
     * @param command 已解析并校验的 Trace 属性
     * @return 可关闭的 Trace 生命周期句柄
     */
    LangfuseTraceScope startTrace(LangfuseTraceCommand command);

    /**
     * 创建一个检索、重排等非生成阶段的子 Span。
     *
     * @param name          Span 名称
     * @param attributes    受控非敏感属性
     * @param parentContext 父 Trace 或 Span 上下文
     * @return 可关闭的 Span 生命周期句柄
     */
    default LangfuseSpanScope startSpan(String name, Map<String, Object> attributes, Context parentContext) {
        return startSpan(name, LangfuseObservationType.SPAN, attributes, parentContext);
    }

    /**
     * 创建具有明确 Langfuse 语义类型的子观察。
     *
     * @param name          观察名称
     * @param type          Langfuse 观察类型
     * @param attributes    受控非敏感属性
     * @param parentContext 父 Trace 或 Span 上下文
     * @return 可关闭的 Span 生命周期句柄
     */
    default LangfuseSpanScope startSpan(String name, LangfuseObservationType type,
                                        Map<String, Object> attributes, Context parentContext) {
        return NoopLangfuseTelemetry.INSTANCE.startSpan(name, type, attributes, parentContext);
    }

    /**
     * 创建模型调用对应的 Generation。
     *
     * @param command       非敏感模型调用元数据
     * @param parentContext 上游 Trace 上下文
     * @return Generation 生命周期句柄
     */
    default LangfuseGenerationScope startGeneration(LangfuseGenerationCommand command, Context parentContext) {
        return NoopLangfuseTelemetry.INSTANCE.startGeneration(command, parentContext);
    }
}
