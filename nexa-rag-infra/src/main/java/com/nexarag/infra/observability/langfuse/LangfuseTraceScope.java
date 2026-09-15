package com.nexarag.infra.observability.langfuse;

import io.opentelemetry.context.Context;

/**
 * Langfuse 根 Trace 的生命周期句柄。
 */
public interface LangfuseTraceScope extends AutoCloseable {

    /**
     * 返回可跨 Reactor 线程显式传递的 OTel 上下文。
     *
     * @return 根 Trace 上下文
     */
    Context context();

    /**
     * 将根 Trace 标记为异常终态。
     *
     * @param throwable 业务异常，可为空
     */
    void fail(Throwable throwable);

    /**
     * 结束根 Trace；重复调用必须安全。
     */
    @Override
    void close();
}
