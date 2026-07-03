package com.nexarag.common.trace;

import lombok.NoArgsConstructor;

/**
 * TraceId 上下文，提供链路 ID 的请求头、日志字段和线程内读取能力。
 */
@NoArgsConstructor
public final class TraceIdContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";
    private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前执行线程的 TraceId。
     *
     * @param traceId 链路追踪 ID
     */
    public static void setTraceId(String traceId) {
        TRACE_ID_HOLDER.set(traceId);
    }

    /**
     * 获取当前执行线程的 TraceId。
     *
     * @return 链路追踪 ID
     */
    public static String getTraceId() {
        return TRACE_ID_HOLDER.get();
    }

    /**
     * 清理当前执行线程的 TraceId。
     */
    public static void clear() {
        TRACE_ID_HOLDER.remove();
    }
}
