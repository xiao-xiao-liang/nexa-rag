package com.nexarag.infra.observability.langfuse.model;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;

import static com.nexarag.infra.observability.langfuse.constants.LangfuseContextConstant.TRACE_ATTRIBUTES_CONTEXT_KEY;

/**
 * 需要复制到 Langfuse 子观察的根 Trace 标识属性。
 *
 * <p>该对象仅保存在进程内 OTel Context，绝不直接写入可序列化的 Graph State。</p>
 *
 * @param traceName     Trace 名称
 * @param correlationId 应用链路标识
 * @param sessionId     会话标识
 * @param userId        用户标识
 */
public record LangfuseTraceContextAttributes(String traceName, String correlationId, String sessionId,
                                             String userId) {

    private static final ContextKey<LangfuseTraceContextAttributes> CONTEXT_KEY = ContextKey.named(
            TRACE_ATTRIBUTES_CONTEXT_KEY);

    /**
     * 将安全根 Trace 属性附加到 OTel 上下文。
     *
     * @param context    原始上下文
     * @param attributes 根 Trace 属性
     * @return 附带属性的上下文
     */
    public static Context attach(Context context, LangfuseTraceContextAttributes attributes) {
        Context resolvedContext = context == null ? Context.root() : context;
        return attributes == null ? resolvedContext : resolvedContext.with(CONTEXT_KEY, attributes);
    }

    /**
     * 读取 OTel 上下文中已传播的根 Trace 属性。
     *
     * @param context OTel 上下文
     * @return 属性；未设置时返回 {@code null}
     */
    public static LangfuseTraceContextAttributes from(Context context) {
        return (context == null ? Context.root() : context).get(CONTEXT_KEY);
    }

    /**
     * 从已解析的 Trace 命令构造可传播属性。
     *
     * @param command Trace 命令
     * @return 根 Trace 属性
     */
    public static LangfuseTraceContextAttributes from(LangfuseTraceCommand command) {
        return new LangfuseTraceContextAttributes(command.name(), command.correlationId(), command.sessionId(),
                command.userId());
    }
}
