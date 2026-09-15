package com.nexarag.infra.observability.langfuse;

import io.opentelemetry.context.Context;

import java.util.Map;

/**
 * Langfuse 普通 Span 的生命周期句柄，用于检索等非模型调用边界。
 */
public interface LangfuseSpanScope extends AutoCloseable {

    /**
     * 返回当前 Span 上下文，供下游子 Span 继承。
     *
     * @return 当前 OTel 上下文
     */
    Context context();

    /**
     * 补充已完成阶段的受控数值属性。
     *
     * @param attributes 仅允许的非敏感属性
     */
    void addAttributes(Map<String, Object> attributes);

    /**
     * 标记 Span 为失败。
     *
     * @param throwable 失败原因
     */
    void fail(Throwable throwable);

    /**
     * 结束 Span；重复调用必须安全。
     */
    @Override
    void close();
}
