package com.nexarag.infra.observability.langfuse.constants;

/**
 * Langfuse Trace 上下文在 Java 与 Reactor 边界间传递时使用的键和值。
 */
public final class LangfuseContextConstant {

    public static final int CARRIER_PART_COUNT = 6;
    public static final String REACTOR_OTEL_CONTEXT_KEY = "nexa.langfuse.otel-context";
    public static final String TRACE_ATTRIBUTES_CONTEXT_KEY = "nexa.langfuse.trace.attributes";
    public static final String CARRIER_DELIMITER = ".";
    public static final String CARRIER_SPLIT_PATTERN = "\\.";

    private LangfuseContextConstant() {
    }
}
