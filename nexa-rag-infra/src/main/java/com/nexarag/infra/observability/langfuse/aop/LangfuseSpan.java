package com.nexarag.infra.observability.langfuse.aop;

import com.nexarag.infra.observability.langfuse.model.LangfuseObservationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一次同步 Langfuse 子观察。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LangfuseSpan {

    String name();

    LangfuseObservationType type() default LangfuseObservationType.SPAN;

    String parentContextCarrier() default "";

    String[] attributes() default {};

    String[] resultAttributes() default {};
}
