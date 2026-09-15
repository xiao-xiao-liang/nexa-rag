package com.nexarag.infra.observability.langfuse.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一次业务请求的 Langfuse 根 Trace。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LangfuseTrace {

    /** Trace 名称。 */
    String name();

    /** NexaRAG 业务链路关联 ID 的 SpEL 表达式。 */
    String correlationId() default "";

    /** 会话 ID 的 SpEL 表达式。 */
    String sessionId() default "";

    /** 用户 ID 的 SpEL 表达式。 */
    String userId() default "";

    /** 受控自定义属性，格式为“键=SpEL 表达式”。 */
    String[] attributes() default {};
}
